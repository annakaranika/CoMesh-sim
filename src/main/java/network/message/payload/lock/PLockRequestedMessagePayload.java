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

public class PLockRequestedMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 6580474586145450814L;
    public int rtnSeqNo;
    public int requestSeqNo;

    public PLockRequestedMessagePayload(
            int epochNo, String rtnID, int rtnSeqNo, int reqSeqNo, String devID) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.rtnSeqNo = rtnSeqNo;
        this.requestSeqNo = reqSeqNo;
        this.devID = devID;
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

        // Update device k-group leader at routine k-group
        mngr.getDevKGrp(devID).updateLeader(src);

        ReplyMessagePayload replyPayload;
        MessageType replyMsgType;

        if (!kgrp.isLeader(mngr.getMyID())) {
            replyPayload = new RoutineKGroupLeaderInfoMessagePayload(
                    epochNo, devID, replySeqNo, src);
            replyMsgType = MessageType.ROUTINE_KGROUP_LEADER_INFO;
        } else {
            String rtnID = getMonitored1();
            kgrp.acquiredDevLockForParallel(rtnID, rtnSeqNo, requestSeqNo, devID);
            mngr.log(
                    kgrp,
                    "Routine " + rtnID + "-" + rtnSeqNo +
                            " PLOCK_REQUESTED received for device " + devID);
            mngr.log(
                    kgrp,
                    "    Routine " + rtnID + "-" + rtnSeqNo + " preLockDevSeqNo " +
                            kgrp.getAllPreLockDevSeqNo(rtnID, rtnSeqNo));
            mngr.log(
                    kgrp,
                    "    local state: " + kgrp.getState().toString());
            // If all locks are acquired (pre-locked). Send message to grant device lock.
            if (kgrp.acquiredAllLocksForParallel(rtnID, rtnSeqNo)) {
                List<String> targetDevs = kgrp
                        .getAllDevIdsLockAcquiredNotGranted(rtnID, rtnSeqNo);
                for (String devID : targetDevs) {
                    int devReqSeqNo = kgrp.getPreLockDevSeqNo(rtnID, rtnSeqNo, devID);
                    PLockLockMessagePayload plock_payload = new PLockLockMessagePayload(
                            epochNo, devID, rtnID, rtnSeqNo, devReqSeqNo);
                    mngr.remoteCall(
                            kgrp, KGroupType.DEVICE, devID,
                            MessageType.PLOCK_LOCK, plock_payload, false);
                }
            }
            replyMsgType = MessageType.PLOCK_REQUESTED_ACK;
            replyPayload = new PLockRequestedAckMessagePayload(
                    epochNo, devID, replySeqNo, rtnID, rtnSeqNo);
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
