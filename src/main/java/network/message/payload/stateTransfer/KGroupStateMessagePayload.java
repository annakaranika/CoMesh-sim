package network.message.payload.stateTransfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import metric.KGroupMetric;
import network.message.Message;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.ReplyMessagePayload;
import routine.DeviceState;

public class KGroupStateMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -6346149567749302839L;

    public final Map<String, Object> state = new HashMap<String, Object>();
    public final Map<String, Integer> seqNos = new HashMap<String, Integer>();
    public final Map<String, Integer> lastTriggered = new HashMap<String, Integer>();
    public final Map<String, DeviceState> oldDevStates = new HashMap<String, DeviceState>();
    public final List<Message> unprocessedMessages = new ArrayList<Message>();

    public KGroupStateMessagePayload(KGroup kgrp, int srcSeqNo, List<Message> messages) {
        super(kgrp, srcSeqNo);
        if (kgrp.getState() != null)
            state.putAll(kgrp.getState());
        if (kgrp.getSeqNos() != null)
            seqNos.putAll(kgrp.getSeqNos());
        if (kgrp.getLastTriggered() != null)
            lastTriggered.putAll(kgrp.getLastTriggered());
        if (kgrp.getOldDevStates() != null)
            oldDevStates.putAll(kgrp.getOldDevStates());
        if (messages != null)
            unprocessedMessages.addAll(messages);
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

        kgrp.setState(state);
        kgrp.setSeqNos(seqNos);
        kgrp.setLastTriggered(lastTriggered);
        kgrp.setOldDevStates(oldDevStates);
        kgrp.setWaitingMessageQueue(unprocessedMessages);

        int seqNo = mngr.quorum(
                kgrp, MessageType.KGROUP_STATE_DISTRIBUTION,
                new KGroupStateDistributionMessagePayload(kgrp), false, false);
        KGroupMetric.addStateTrnsfrSeqNo(kgrp, srcSeqNo, seqNo);

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
