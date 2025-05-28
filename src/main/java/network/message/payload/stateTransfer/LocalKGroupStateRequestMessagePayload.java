package network.message.payload.stateTransfer;

import java.util.List;

import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class LocalKGroupStateRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -437966124955578173L;

    public KGroupType kGroupType;
    public List<String> entitiesIDs;

    public LocalKGroupStateRequestMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs) {
        super(epochNo, kGroupType, entitiesIDs);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (kgrp.getEpochNo() != epochNo) {
            return null;
        }

        ReplyMessagePayload replyPayload = new LocalKGroupStateMessagePayload(kgrp, replySeqNo);
        MessageType replyMsgType = MessageType.LOCAL_KGROUP_STATE;
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
