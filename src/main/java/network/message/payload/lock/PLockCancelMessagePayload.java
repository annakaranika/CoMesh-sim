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

public class PLockCancelMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 5128713308909528515L;
    public int rtnSeqNo;

    public PLockCancelMessagePayload(
            int epochNo, String devID, String rtnID, int rtnSeqNo) {
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

        String devID = getMonitored1();
        int reqSeqNo = kgrp.getLockReqSeqNo(devID, rtnID, rtnSeqNo);
        mngr.log(
                kgrp,
                "Device " + devID + " receives PLOCK_CANCEL for rtn " + rtnID + "-" + rtnSeqNo);
        if (reqSeqNo >= 0) {
            // Update this cancel info to all group member
            mngr.gatherQuorumForDevicePLockCancel(rtnID, rtnSeqNo, devID, reqSeqNo);
            // Clear records in device leader.
            kgrp.removeLockRequest(devID, rtnID, rtnSeqNo, reqSeqNo);
        } else { // The request was rejected before.
            PLockCancelledMessagePayload cancelled_payload = new PLockCancelledMessagePayload(
                    epochNo, rtnID, rtnSeqNo, devID);
            mngr.remoteCall(
                    kgrp, KGroupType.ROUTINE, rtnID,
                    MessageType.PLOCK_CANCELLED, cancelled_payload, false);
        }
        // Send ACK back to routine leader.
        MessageType replyMsgType = MessageType.PLOCK_CANCEL_ACK;
        ReplyMessagePayload replyPayload = new PLockLockAckMessagePayload(
                epochNo, rtnID, replySeqNo);
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
