package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class LockReleasedMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = -717256623598162112L;

    public int rtnSeqNo;

    public LockReleasedMessagePayload(int epochNo, String rtnID, String devID, int rtnSeqNo) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.devID = devID;
        this.rtnSeqNo = rtnSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            // forward message to new device k-group leader
            return null;
        }

        ReplyMessagePayload replyPayload;
        MessageType replyMsgType;

        if (!kgrp.isLeader(mngr.getMyID())) {
            replyPayload = new RoutineKGroupLeaderInfoMessagePayload(
                    epochNo, List.of(devID), replySeqNo, src);
            replyMsgType = MessageType.ROUTINE_KGROUP_LEADER_INFO;
        } else {
            String rtnID = getMonitored1();
            kgrp.releasedDevLockForRtn(rtnID, rtnSeqNo, devID);
            mngr.log(
                    kgrp,
                    "Routine " + rtnID + "-" + rtnSeqNo + ": device " + devID + " lock released",
                    true);

            replyPayload = new LockReleasedAckMessagePayload(
                    epochNo, devID, replySeqNo);
            replyMsgType = MessageType.LOCK_RELEASED_ACK;
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
