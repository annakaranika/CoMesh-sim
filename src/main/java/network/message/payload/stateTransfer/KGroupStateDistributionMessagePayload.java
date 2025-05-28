package network.message.payload.stateTransfer;

import java.util.HashMap;
import java.util.Map;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.LeaderElectionStage;
import kgroup.state.StateTransferStage;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;
import routine.DeviceState;

public class KGroupStateDistributionMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 5530604947123381069L;

    public final Map<String, Object> state = new HashMap<String, Object>();
    public final Map<String, Integer> seqNos = new HashMap<String, Integer>();
    public final Map<String, Integer> lastTriggered = new HashMap<String, Integer>();
    public final Map<String, DeviceState> oldDevStates = new HashMap<String, DeviceState>();

    public KGroupStateDistributionMessagePayload(KGroup kgrp) {
        super(kgrp);
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

        if (epochNo != kgrp.getEpochNo()) {
            return null;
        }

        if (kgrp.isLeaderNull() || !kgrp.isLdrElctnStg(LeaderElectionStage.COMPLETE)) {
            mngr.log(kgrp, "Leader election stage: " + kgrp.getLdrElctnStg());
            kgrp.updateLeader(src);
            kgrp.advLdrElctnStg(LeaderElectionStage.COMPLETE);
        }
        kgrp.setState(state);
        kgrp.setSeqNos(seqNos);
        kgrp.setLastTriggered(lastTriggered);
        kgrp.setOldDevStates(oldDevStates);

        kgrp.advStateTrnsfrStg(StateTransferStage.COMPLETE);
        kgrp.openReceivingEnd();
        ReplyMessagePayload replyPayload = new KGroupStateAckMessagePayload(kgrp, replySeqNo);
        MessageType replyMsgType = MessageType.KGROUP_STATE_ACK;

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
