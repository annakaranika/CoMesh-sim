package network.message.payload.lock;

import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.ReplyMessagePayload;

import java.io.Serial;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;

public class PLockLockAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -1692255007522253078L;

    public PLockLockAckMessagePayload(int epochNo, String rtnID, int srcSeqNo) {
        super(epochNo, KGroupType.ROUTINE, rtnID, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
