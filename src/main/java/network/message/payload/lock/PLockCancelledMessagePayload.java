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

public class PLockCancelledMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 9105750019058971231L;
    public int rtnSeqNo;

    public PLockCancelledMessagePayload(int epochNo, String rtnID, int rtnSeqNo, String devID) {
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
        kgrp.removePreLockRecord(rtnID, rtnSeqNo, devID);

        mngr.log(kgrp, "Routine " + rtnID + "-" + rtnSeqNo + " CANCELLED lock on dev " + devID);
        mngr.log(kgrp, "    local state: " + kgrp.getRtnStage(rtnID, rtnSeqNo).toString());

        // Request parallel lock again with delay after all CANCELLED has been received.
        mngr.requestDevLockCancel(rtnID, rtnSeqNo);

        MessageType replyMsgType = MessageType.PLOCK_CANCELLED_ACK;
        ReplyMessagePayload replyPayload = new PLockCancelledAckMessagePayload(
                epochNo, devID, replySeqNo);
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
