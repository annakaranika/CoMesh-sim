package network.message.payload.election;

import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionStage;
import metric.KGroupMetric;
import metric.NodeRole;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class ElectedAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 1845402298249270653L;

    public ElectedAckMessagePayload(KGroup kgrp, int srcSeqNo) {
        super(kgrp, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (kgrp.getEpochNo() != epochNo || !kgrp.isMember(mngr.getMyID())
                || kgrp.isLdrElctnStg(LeaderElectionStage.COMPLETE)) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        QuorumMsgInfo msgInfo = null;
        try {
            msgInfo = mngr.getQuorumMsgInfo(srcSeqNo);
        } catch (Exception e) {
            mngr.log(this, true);
        }

        if (msgInfo == null) {
            mngr.log(kgrp, "Msg seq no " + srcSeqNo + " does not exist!\n\t" + this);
        }
        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            mngr.log(kgrp, "Received new vote for election (" + msgInfo.getPositiveRepliesCount()
                    + "/" + neededAckCount + " now)");
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                kgrp.updateLeader(mngr.getMyID());
                msgInfo.setApproved(mngr.getCurTS(), srcSeqNo);
                kgrp.advLdrElctnStg(LeaderElectionStage.COMPLETE);
                int ldrElctnDelay = KGroupMetric.completeLdrElctn(
                        kgrp, srcSeqNo, mngr.getCurTS());
                mngr.log(
                        kgrp, "Finished election for epoch " + epochNo
                                + "\n\tit lasted " + ldrElctnDelay
                                + " ms,\n\tgot replies from " + msgInfo.getRepliedNodesIDs());

                if (kgrp.getCountFailedNodes() >= mngr.getF()) {
                    List<String> replacementNodes = kgrp.replaceFailedNodes(
                            mngr.getMembershipList(), epochNo);
                    mngr.replaceKGroupNodes(kgrp, replacementNodes);
                    mngr.log(kgrp, "replacing failed nodes with " + replacementNodes, true);
                }
                if (kGroupType.equals(KGroupType.ROUTINE)) {
                    mngr.startRtnRoles(NodeRole.LEADER, entitiesIDs);
                } else {
                    mngr.startDevRoles(NodeRole.LEADER, entitiesIDs);
                }
                mngr.stateTransfer(
                        kgrp, ((ElectedMessagePayload) msgInfo.getMsgPayload()).getLdrElctnCause());
                mngr.removeMsgInfo(srcSeqNo);
            }
        }

        return null;
    }

    public String toString() {
        return "ELECTED_ACK " + super.toString();
    }
}