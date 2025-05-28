package network.message;

import java.util.ArrayList;
import java.util.List;

import kgroup.KGroup;
import metric.KGroupMetric;
import network.message.payload.MessagePayload;

public class QuorumMsgInfo extends MsgInfo {
    private int positiveReplies;
    private List<String> replied;
    private boolean approved;

    public QuorumMsgInfo(
            MessageType type, MessagePayload payload, KGroup kgroup, boolean boundedWait,
            int routeOWD, int msgTS, int initialEpoch, int seqNo) {
        super(type, payload, kgroup, boundedWait, routeOWD, msgTS, initialEpoch);
        positiveReplies = 0;
        replied = new ArrayList<String>();
        approved = false;
        KGroupMetric.startQuorum(kgroup, seqNo, msgTS);
    }

    public void newReply(String nodeID, boolean positive) {
        if (!replied.contains(nodeID)) {
            replied.add(nodeID);
            if (positive)
                positiveReplies++;
        }
    }

    public int getPositiveRepliesCount() {
        return positiveReplies;
    }

    public List<String> getRepliedNodesIDs() {
        return replied;
    }

    public List<String> getNoReplyNodesIDs() {
        List<String> kgroupRecipients;
        kgroupRecipients = new ArrayList<>(kgrp.getCurMemberIDs());
        kgroupRecipients.removeAll(replied);
        return kgroupRecipients;
    }

    public void setApproved(int ts, int seqNo) {
        approved = true;
        KGroupMetric.completeQuorum(kgrp, seqNo, ts);
    }

    public boolean isApproved() {
        return approved;
    }
}
