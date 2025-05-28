package network.message.payload.stateTransfer;

import java.util.HashMap;
import java.util.Map;

import device.Device;
import device.KGroupManager;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.state.StateTransferStage;
import metric.KGroupMetric;
import network.Network;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.ReplyMessagePayload;
import routine.DeviceState;

public class LocalKGroupStateMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 1008075774431667084L;

    public final Map<String, Object> state = new HashMap<String, Object>();
    public final Map<String, Integer> seqNos = new HashMap<String, Integer>();
    public final Map<String, Integer> lastTriggered = new HashMap<String, Integer>();
    public final Map<String, DeviceState> oldDevStates = new HashMap<String, DeviceState>();

    public LocalKGroupStateMessagePayload(KGroup kgrp, int srcSeqNo) {
        super(kgrp, srcSeqNo);
        if (kgrp.getState() != null)
            state.putAll(kgrp.getState());
        if (kgrp.getSeqNos() != null)
            seqNos.putAll(kgrp.getSeqNos());
        if (kgrp.getLastTriggered() != null)
            lastTriggered.putAll(kgrp.getLastTriggered());
        if (kgrp.getOldDevStates() != null)
            oldDevStates.putAll(kgrp.getOldDevStates());
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(node.getMyID())
                || kgrp.isStateTrnsfrStg(StateTransferStage.COMPLETE)) {
            return null;
        }

        kgrp.addLocalState(state);
        kgrp.addLocalSeqNos(seqNos);
        kgrp.addLocalLastTriggered(lastTriggered);
        kgrp.addLocalOldDevStates(oldDevStates);

        QuorumMsgInfo msgInfo = mngr.getQuorumMsgInfo(srcSeqNo);
        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.setApproved(Network.getCurTS(), srcSeqNo);
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                int seqNo = mngr.quorum(kgrp, MessageType.KGROUP_STATE_DISTRIBUTION,
                        new KGroupStateDistributionMessagePayload(kgrp),
                        false, false);

                KGroupMetric.addStateTrnsfrSeqNo(kgrp, srcSeqNo, seqNo);
                mngr.removeMsgInfo(srcSeqNo);
            }
        }
        return null;
    }
}
