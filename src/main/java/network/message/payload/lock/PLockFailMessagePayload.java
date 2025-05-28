package network.message.payload.lock;

import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

import java.io.Serial;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;

public class PLockFailMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 1646035768600306694L;
    public int rtnSeqNo;

    public PLockFailMessagePayload(
            int epochNo, String rtnID, int rtnSeqNo, String devID) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.rtnSeqNo = rtnSeqNo;
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

        String rtnID = getMonitored1();
        mngr.getDevKGrp(devID).updateLeader(src);
        mngr.log(
                kgrp,
                "Routine " + rtnID + "-" + rtnSeqNo + " PLOCK_FAILED received for dev " + devID);

        MessageType replyMsgType = MessageType.PLOCK_FAILED_ACK;
        ReplyMessagePayload replyPayload = new PLockFailAckMessagePayload(epochNo, devID, replySeqNo);
        // The routine already knows the failure and started to cancel.
        // TODO: deal with failure.
        if (kgrp.isLockAcquireFailed(rtnID, rtnSeqNo)) {
            return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
        }

        // Notify device to cancel pre-locks.
        for (String devID : kgrp.getTouchedDevIDs(rtnID)) {
            PLockCancelMessagePayload cancel_payload = new PLockCancelMessagePayload(
                    epochNo, devID, rtnID, rtnSeqNo);
            mngr.remoteCall(
                    kgrp, KGroupType.DEVICE, devID,
                    MessageType.PLOCK_CANCEL, cancel_payload, false);
        }
        kgrp.lockAcquireFailedForRtn(rtnID, rtnSeqNo);
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
