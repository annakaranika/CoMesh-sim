package network.message.payload.election;

import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import kgroup.LeaderElectionStage;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class ElectionMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -5070340407181689772L;

    private LeaderElectionCause cause;

    public ElectionMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs,
            LeaderElectionCause cause) {
        super(epochNo, kGroupType, entitiesIDs);
        this.cause = cause;
    }

    @Override
    public LeaderElectionCause getLdrElctnCause() {
        return cause;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (kgrp.getEpochNo() > epochNo || !kgrp.isMember(mngr.getMyID())
                || (kgrp.getEpochNo() == epochNo && kgrp.isLdrElctnStg(LeaderElectionStage.COMPLETE))) {
            return null;
        } else if (kgrp.getEpochNo() < epochNo) {
            kgrp.updateKGroup(mngr.getMembershipList(), mngr.getKMemberMix(), epochNo);
        }

        MessageType replyMsgType = MessageType.ELECTION_ACK;
        ReplyMessagePayload replyPayload = new ElectionAckMessagePayload(kgrp, replySeqNo);

        if (kgrp.getLdrElctnStg() == LeaderElectionStage.NOT_STARTED) {
            mngr.leaderElection(kgrp, cause);
        }
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
