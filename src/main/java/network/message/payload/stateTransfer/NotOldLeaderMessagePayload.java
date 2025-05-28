package network.message.payload.stateTransfer;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.LeaderElectionCause;
import metric.KGroupMetric;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class NotOldLeaderMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -6346149567749302478L;

    public String oldLeader;

    public NotOldLeaderMessagePayload(KGroup kgrp, int srcSeqNo) {
        super(kgrp, srcSeqNo);
        this.oldLeader = kgrp.getOldLeader();
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        if (oldLeader != null) {
            kgrp.updateOldLeader(oldLeader);
            mngr.log(kgrp, "old leader updated to " + oldLeader + " by leader");
        } else {
            kgrp.removeOldLdrCnddt(src);
            mngr.log(
                    kgrp,
                    "old node " + src + " removed as old leader candidate by leader " + kgrp.getLeader());
        }
        int seqNo;
        if (kgrp.isOldLeaderNull() || !kgrp.isOldLeader(mngr.getMyID())) {
            if (kgrp.isOldLeaderNull() && kgrp.isOldMember(mngr.getMyID())) {
                // in case I just joined
                // but the system believes I should have been the leader
                kgrp.removeOldLdrCnddt(mngr.getMyID());
            }
            if (kgrp.noOldLdrCnddts()) {
                seqNo = mngr.quorum(
                        kgrp, MessageType.LOCAL_KGROUP_STATE_REQUEST,
                        new LocalKGroupStateRequestMessagePayload(epochNo, kGroupType, entitiesIDs),
                        true, false);
            } else {
                seqNo = mngr.remoteCall(kgrp, kgrp.getMostProbableLeader(true),
                        MessageType.KGROUP_STATE_REQUEST, new KGroupStateRequestMessagePayload(
                                kgrp, LeaderElectionCause.NEW_EPOCH),
                        true);
            }
        } else {
            seqNo = mngr.quorum(
                    kgrp, MessageType.KGROUP_STATE_DISTRIBUTION,
                    new KGroupStateDistributionMessagePayload(kgrp), false, false);
        }
        KGroupMetric.addStateTrnsfrSeqNo(kgrp, srcSeqNo, seqNo);

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
