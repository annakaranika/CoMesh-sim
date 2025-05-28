package network.message.payload.failure;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import network.message.MessageType;
import network.message.payload.ReplyMessagePayload;

public class NodeFailureAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 2467589647387006617L;

    public NodeFailureAckMessagePayload(int srcSeqNo) {
        super(srcSeqNo);
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
