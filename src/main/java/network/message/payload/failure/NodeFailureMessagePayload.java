package network.message.payload.failure;

import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class NodeFailureMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -5307849874262842593L;

    private String failedNodeID;

    public NodeFailureMessagePayload(
            KGroupType kGroupType, List<String> entitiesIDs, String failedNodeID) {
        super(-1, kGroupType, entitiesIDs);
        this.failedNodeID = failedNodeID;
    }

    @Override
    public String getFailedNodeID() {
        return failedNodeID;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        mngr.handleNodeFailure(failedNodeID);

        MessageType replyMsgType = MessageType.NODE_FAILURE_ACK;
        ReplyMessagePayload replyPayload = new NodeFailureAckMessagePayload(replySeqNo);

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
