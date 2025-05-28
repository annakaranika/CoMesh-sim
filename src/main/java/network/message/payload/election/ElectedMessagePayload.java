package network.message.payload.election;

import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import kgroup.LeaderElectionStage;
import kgroup.Membership;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class ElectedMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 4094035504829395065L;

    private LeaderElectionCause cause;

    public ElectedMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs,
            LeaderElectionCause cause) {
        super(epochNo, kGroupType, entitiesIDs);
        this.cause = cause;
    }

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

        if (cause == LeaderElectionCause.LEADER_FAILURE) {
            mngr.getMembershipList().replace(kgrp.getLeader(), Membership.OFFLINE);
            kgrp.updateFailedNodes(kgrp.getLeader());
        }

        if (!mngr.getMyID().equals(src)) {
            // update leader
            kgrp.updateLeader(src);
            kgrp.advLdrElctnStg(LeaderElectionStage.COMPLETE);
            kgrp.openReceivingEnd();
            mngr.log(kgrp, "Elected node");
        }

        ReplyMessagePayload replyPayload = new ElectedAckMessagePayload(kgrp, replySeqNo);
        MessageType replyMsgType = MessageType.ELECTED_ACK;

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
