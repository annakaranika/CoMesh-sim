package network.message.payload.lock;

import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.ReplyMessagePayload;

import java.io.Serial;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;

public class PLockRequestAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -2108963766272582011L;

    public PLockRequestAckMessagePayload(int epochNo, String rtnID, int srcSeqNo) {
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
