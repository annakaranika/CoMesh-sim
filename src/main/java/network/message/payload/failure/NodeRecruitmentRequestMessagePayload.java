package network.message.payload.failure;

import java.util.ArrayList;
import java.util.Map;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class NodeRecruitmentRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -2822918732737107307L;

    public Map<String, Object> state;

    public NodeRecruitmentRequestMessagePayload(
            int epochNo, KGroupType kGroupType, Map<String, Object> states) {
        super(epochNo, kGroupType, new ArrayList<String>(states.keySet()));
        this.state = states;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (kgrp.getEpochNo() > epochNo)
            return null;
        else if (kgrp.getEpochNo() < epochNo)
            kgrp.updateKGroup(mngr.getMembershipList(), mngr.getKMemberMix(), epochNo);

        mngr.log(kgrp, "Received " + this, true);

        mngr.recruited(kgrp);
        kgrp.nodeRecruited(mngr.getMyID());
        kgrp.updateLeader(src);
        kgrp.setState(state);

        MessageType replyMsgType = MessageType.NODE_RECRUITED;
        ReplyMessagePayload replyPayload = new NodeRecruitedMessagePayload(kgrp, replySeqNo);

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }

    @Override
    public String toString() {
        return "NODE_RECRUITEMENT_REQUEST " + super.toString();
    }
}
