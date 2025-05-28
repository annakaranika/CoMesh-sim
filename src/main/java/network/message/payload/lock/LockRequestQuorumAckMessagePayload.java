package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.state.LockRequest;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class LockRequestQuorumAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = 8234550092862487011L;

    public int reqSeqNo, rtnSeqNo;

    public LockRequestQuorumAckMessagePayload(
            int epochNo, List<String> devID, int srcSeqNo, String rtnID, int reqSeqNo, int rtnSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
        this.rtnID = rtnID;
        this.reqSeqNo = reqSeqNo;
        this.rtnSeqNo = rtnSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        QuorumMsgInfo msgInfo = mngr.getQuorumMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.log(
                    kgrp, "Msg seq no " + srcSeqNo + " does not exist!\n\t" + this + " from " + src);
        }
        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            mngr.log(
                    kgrp,
                    "Received new vote for lock request (" + msgInfo.getPositiveRepliesCount()
                            + "/" + neededAckCount + " now)");
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                String devID = getMonitored1();
                // if the request has been previously acknowledged, do not do it again
                if (!kgrp.isLockRequestGranted(devID, rtnID, rtnSeqNo)) {
                    kgrp.lockRequestReplicated(devID, rtnID, rtnSeqNo, reqSeqNo);

                    mngr.log(
                            kgrp,
                            "Lock request granted by device " + devID
                                    + " to routine " + rtnID + "-" + rtnSeqNo);
                    mngr.log(kgrp, "Device " + devID + " state: " + mngr.getDevState(devID));

                    mngr.remoteCall(
                            kgrp, KGroupType.ROUTINE, rtnID, MessageType.LOCK_REQUESTED,
                            new LockRequestedMessagePayload(
                                    epochNo, List.of(rtnID), rtnSeqNo, devID),
                            false);

                    // if the device lock is free and this is the only request
                    // in the queue, give it to the new request
                    if (!kgrp.isLocked(devID) &&
                            kgrp.getLastLockedReqSeqNo(devID) + 1 == reqSeqNo) {
                        LockRequest nextRtn = kgrp.getLockQueueHead(devID);
                        mngr.gatherQuorumForDevLockAcquisition(
                                nextRtn.getRtnID(), nextRtn.getRtnSeqNo(), devID);
                    }
                }
                mngr.removeMsgInfo(srcSeqNo);
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "LOCK_REQUEST_QUORUM_ACK " + super.toString();
    }
}
