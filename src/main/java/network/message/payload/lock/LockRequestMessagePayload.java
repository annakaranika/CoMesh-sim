package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class LockRequestMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 8452728320378949730L;

    public int rtnSeqNo;

    public LockRequestMessagePayload(int epochNo, String devID, String rtnID, int rtnSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID);
        this.rtnID = rtnID;
        this.rtnSeqNo = rtnSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            // forward message to new device k-group leader
            return null;
        }

        // update the routine k-group's leader so that I can safely reply afterwards
        mngr.getRtnKGrp(rtnID).updateLeader(src);

        ReplyMessagePayload replyPayload;
        MessageType replyMsgType;

        if (!kgrp.isLeader(mngr.getMyID())) {
            replyPayload = new DeviceKGroupLeaderInfoMessagePayload(
                    epochNo, List.of(rtnID), replySeqNo, src);
            replyMsgType = MessageType.DEVICE_KGROUP_LEADER_INFO;
        } else {
            String devID = getMonitored1();
            mngr.log(
                    kgrp,
                    "Lock request for device " + devID + " by routine " + rtnID
                            + "-" + rtnSeqNo + " received");
            replyPayload = new LockRequestAckMessagePayload(epochNo, rtnID, replySeqNo);
            replyMsgType = MessageType.LOCK_REQUEST_ACK;
            int reqSeqNo;

            // if this request has not been received before
            // meaning if it is not in newLockRequests/queue
            if (!kgrp.isLockRequestReceived(devID, rtnID, rtnSeqNo)) {
                // mngr.log("Device " + deviceID + " state before: " +
                // kgroup.getState().get(deviceID));
                reqSeqNo = kgrp.addNewLockRequest(devID, rtnID, rtnSeqNo);
                if (reqSeqNo == -1) {
                    return null;
                }
                mngr.gatherQuorumForDevLockRequest(rtnID, rtnSeqNo, devID, reqSeqNo);
            }
            // if this request has already been received before
            else {
                reqSeqNo = kgrp.getLockReqSeqNo(devID, rtnID, rtnSeqNo);
                if (reqSeqNo == -1) {
                    return null;
                }
                // mngr.log("Rq timestamp: " + reqSeqNo);
                // if this lock referred to by this request has already been granted before
                // meaning if it is the current locker
                if (kgrp.isLockAcquired(devID, rtnID, rtnSeqNo)) {
                    mngr.remoteCall(
                            kgrp, KGroupType.ROUTINE, rtnID, MessageType.LOCKED,
                            new LockedMessagePayload(
                                    epochNo, List.of(rtnID), devID, rtnSeqNo),
                            false);
                }
                // if lock request has been granted
                else if (kgrp.isLockRequestGranted(devID, rtnID, rtnSeqNo)) {
                    mngr.remoteCall(
                            kgrp, KGroupType.ROUTINE, rtnID, MessageType.LOCK_REQUESTED,
                            new LockRequestedMessagePayload(
                                    epochNo, List.of(rtnID), rtnSeqNo, devID),
                            false);
                }
                // if lock request has been received but not granted
                else {
                    mngr.gatherQuorumForDevLockRequest(rtnID, rtnSeqNo, devID, reqSeqNo);
                }
            }
            mngr.log(kgrp, "Device " + devID + " state after: " + kgrp.getDevLock(devID));
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }

    @Override
    public int getRtnSeqNo() {
        return rtnSeqNo;
    }
}
