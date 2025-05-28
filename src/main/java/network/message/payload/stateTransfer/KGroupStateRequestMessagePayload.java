package network.message.payload.stateTransfer;

import java.util.ArrayList;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.LeaderElectionCause;
import network.message.Message;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class KGroupStateRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -4554161628775213371L;

    public LeaderElectionCause cause;

    public KGroupStateRequestMessagePayload(
            KGroup kgrp, LeaderElectionCause cause) {
        super(kgrp);
        this.cause = cause;
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

        ReplyMessagePayload replyPayload;
        MessageType replyMsgType;
        kgrp.updateLeader(src);
        if (kgrp.isOldLeaderNull()) {
            replyPayload = new NotOldLeaderMessagePayload(kgrp, replySeqNo);
            replyMsgType = MessageType.NOT_OLD_LEADER;
        } else if (!kgrp.isOldLeader(mngr.getMyID())) {
            replyPayload = new NotOldLeaderMessagePayload(kgrp, replySeqNo);
            replyMsgType = MessageType.NOT_OLD_LEADER;
        } else {
            replyPayload = new KGroupStateMessagePayload(
                    kgrp, replySeqNo, new ArrayList<Message>(kgrp.getWaitingMessageQueue()));
            replyMsgType = MessageType.KGROUP_STATE;
        }
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
