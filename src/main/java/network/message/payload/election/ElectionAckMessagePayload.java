package network.message.payload.election;

import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionStage;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class ElectionAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 3917732281659350393L;

    public ElectionAckMessagePayload(
            int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
    }

    public ElectionAckMessagePayload(KGroup kgrp, int srcSeqNo) {
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

        if (kgrp.isLdrElctnStg(LeaderElectionStage.WAITING_ON_ELECTION_ACK)) {
            kgrp.advLdrElctnStg(LeaderElectionStage.WAITING_ON_ELECTED);
        }

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }

    @Override
    public String toString() {
        return "ELECTION_ACK " + super.toString();
    }
}
