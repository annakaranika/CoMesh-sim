package network.message.payload.failure;

import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class NodeRecruitedMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -5798654073094211382L;

    public NodeRecruitedMessagePayload(
            int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
    }

    public NodeRecruitedMessagePayload(KGroup kgrp, int srcSeqNo) {
        super(kgrp, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (!kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        kgrp.nodeRecruited(src);
        mngr.log(kgrp, "Node " + src + " recruited", true);

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
