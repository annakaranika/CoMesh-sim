package network.message.payload.routineStage;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.*;

public class ReleasedLocksMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -710072838803293143L;
    public int rtnSeqNo;

    public ReleasedLocksMessagePayload(int epochNo, String rtnID, int rtnSeqNo) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.rtnSeqNo = rtnSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int seqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            return null;
        }

        String rtnID = getMonitored1();
        if (!kgrp.isLeader(mngr.getMyID())) {
            kgrp.removeRtnStage(rtnID, rtnSeqNo);
        }

        ReplyMessagePayload replyPayload = new ReleasedLocksAckMessagePayload(epochNo, rtnID, seqNo);
        MessageType replyMsgType = MessageType.RELEASED_LOCKS_ACK;

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
