package network.message.payload.stateTransfer;

import java.util.Map;
import java.util.Map.Entry;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LockStrategy;
import kgroup.state.DeviceLock;
import kgroup.state.LockRequest;
import kgroup.state.StateTransferStage;
import metric.KGroupMetric;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;
import network.message.payload.lock.LockedMessagePayload;
import network.message.payload.lock.PLockCancelMessagePayload;
import network.message.payload.lock.PLockRequestedMessagePayload;
import simulate.Event;
import simulate.EventType;

public class KGroupStateAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -5768899016281379791L;

    public KGroupStateAckMessagePayload(KGroup kgrp, int srcSeqNo) {
        super(kgrp, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (kgrp.getEpochNo() != epochNo || !kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        QuorumMsgInfo msgInfo = mngr.getQuorumMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            mngr.log(
                    kgrp,
                    "Received new ack for state distribution (" + msgInfo.getPositiveRepliesCount()
                            + "/" + neededAckCount + " now)");
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                long stateTransferDelay = KGroupMetric.completeStateTrnsfr(
                        kgrp, srcSeqNo, mngr.getCurTS());
                mngr.log(
                        kgrp,
                        "state transferred to members for epoch " + epochNo
                                + "\n\tit lasted: " + stateTransferDelay
                                + "ms\n\tgot replies from: " + msgInfo.getRepliedNodesIDs()
                                + "\n\tlocal state: " + kgrp.getState());
                kgrp.advStateTrnsfrStg(StateTransferStage.COMPLETE);
                mngr.addRecurringEvent(
                        0, mngr.getDevMntrPeriod(), mngr.getEpochLen(),
                        new Event(EventType.STATE_MONITOR));
                kgrp.openReceivingEnd();
                switch (kGroupType) {
                    case DEVICE:
                        for (String devID : entitiesIDs) {
                            // if a device's lock is acquired,
                            // let the locker routine's k-group know in case
                            // the device k-group did not succeed before the epoch change
                            if (kgrp.isLocked(devID)) {
                                LockRequest locker = kgrp.getDevLock(devID).getLocker();
                                String rtnID = locker.getRtnID();
                                int rtnSeqNo = locker.getRtnSeqNo();
                                mngr.log(
                                        kgrp,
                                        "Reminding routine " + rtnID + "-" + rtnSeqNo
                                                + " that it has device" + devID + "'s lock");
                                mngr.log(kgrp, kgrp.getMostProbableLeader(false));
                                mngr.remoteCall(
                                        kgrp, KGroupType.ROUTINE, rtnID, MessageType.LOCKED,
                                        new LockedMessagePayload(
                                                epochNo, rtnID, devID, rtnSeqNo),
                                        false);
                            }
                            // if a device is not locked but the queue is not empty either
                            else if (!kgrp.isLocked(devID) && !mngr.getDevKGrp(devID).isLockQueueEmpty(devID)) {
                                DeviceLock lock = kgrp.getDevLock(devID);
                                LockRequest request = lock.getQueueHead();
                                String rtnID = request.getRtnID();
                                int rtnSeqNo = request.getRtnSeqNo();
                                int reqSeqNo = lock.getQueueHeadReqSeqNo();
                                if (kgrp.isLockStrategy(LockStrategy.SERIAL)) {
                                    mngr.gatherQuorumForDevLockAcquisition(rtnID, rtnSeqNo, devID);
                                } else {
                                    MessagePayload pLockRequestedPayload = new PLockRequestedMessagePayload(
                                            epochNo, rtnID, rtnSeqNo, reqSeqNo, devID);

                                    mngr.remoteCall(
                                            kgrp, KGroupType.ROUTINE, rtnID, MessageType.PLOCK_REQUESTED,
                                            pLockRequestedPayload, false);
                                }
                            }

                            // if there are requests in the newLockRequests queue
                            if (!kgrp.isNewLockRequestsEmpty(devID)) {
                                mngr.log(
                                        kgrp,
                                        "---- Dev " + devID + " has new requests. Asking for quorum. ---");
                                mngr.log(kgrp, "    kgroup state: " + kgrp.getDevLock(devID).toString());
                                // }
                                // resend LOCK_REQUEST_QUORUM to move lock requests to the queue
                                Map<Integer, LockRequest> newLockRequests = kgrp.getNewLockRequests(
                                        devID);
                                for (Entry<Integer, LockRequest> req : newLockRequests.entrySet()) {
                                    LockRequest request = req.getValue();
                                    String rtnID = request.getRtnID();
                                    int rtnSeqNo = request.getRtnSeqNo();
                                    int reqSeqNo = req.getKey();
                                    if (kgrp.isLockStrategy(LockStrategy.SERIAL)) {
                                        mngr.gatherQuorumForDevLockRequest(
                                                rtnID, rtnSeqNo, devID, reqSeqNo);
                                    } else {
                                        mngr.gatherQuorumForDevPLockRequest(
                                                rtnID, rtnSeqNo, devID, reqSeqNo);
                                    }
                                }
                            }
                        }
                        break;

                    case ROUTINE:
                        // 1. resend lock request for next device to lock
                        // in case a lock request was lost before
                        // 2. if routine had previously started execution
                        // check to see if it has stopped or not
                        // 3. if routine had finished execution but not
                        // released all device locks, release them now
                        for (String rtnID : entitiesIDs) {
                            for (Integer rtnSeqNo : kgrp.getRtnSeqNos(rtnID)) {
                                if (kgrp.isRtnAcquiringLocks(rtnID, rtnSeqNo)) {
                                    if (kgrp.isLockStrategy(LockStrategy.SERIAL)) {
                                        mngr.requestDevLockForRtn(rtnID, rtnSeqNo);
                                    } else {
                                        mngr.requestDevLockForRtnInParallel(rtnID, rtnSeqNo);
                                    }
                                } else if (kgrp.isRtnExecuting(rtnID, rtnSeqNo)) {
                                    int rtnEndTS = kgrp.getRtnEndTS(rtnID, rtnSeqNo);
                                    int curTS = mngr.getCurTS();
                                    if (rtnEndTS < curTS) {
                                        mngr.finishRtnExec(rtnID, rtnSeqNo);
                                    } else if (rtnEndTS > curTS) {
                                        mngr.addEvent(rtnEndTS, new Event(src, srcSeqNo));
                                    }
                                } else if (kgrp.isRtnReleasingLocks(rtnID, rtnSeqNo)) {
                                    for (String devID : kgrp.getAllUnreleasedDevIDs(rtnID, rtnSeqNo)) {
                                        mngr.releaseDevLockForRtn(rtnID, rtnSeqNo, devID);
                                    }
                                } else if (kgrp.isLockAcquireFailed(rtnID, rtnSeqNo)) {
                                    for (String devID : kgrp.getTouchedDevIDs(rtnID)) {
                                        PLockCancelMessagePayload cancelPayload = new PLockCancelMessagePayload(epochNo,
                                                devID, rtnID, rtnSeqNo);
                                        mngr.remoteCall(
                                                kgrp, KGroupType.DEVICE, devID,
                                                MessageType.PLOCK_CANCEL, cancelPayload, false);
                                    }
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
                mngr.removeMsgInfo(srcSeqNo);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ELECTION_ACK " + super.toString();
    }
}
