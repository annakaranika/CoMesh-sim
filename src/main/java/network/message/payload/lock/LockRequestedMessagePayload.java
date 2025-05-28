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

public class LockRequestedMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = -6501728481760721880L;

    public int rtnSeqNo;

    public LockRequestedMessagePayload(int epochNo, List<String> rtnID, int rtnSeqNo, String devID) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.rtnSeqNo = rtnSeqNo;
        this.devID = devID;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            // forward message to new device k-group leader ?
            return null;
        }

        // update the routine k-group's leader so that I can safely reply afterwards
        mngr.getDevKGrp(devID).updateLeader(src);

        ReplyMessagePayload replyPayload;
        MessageType replyMsgType;

        if (!kgrp.isLeader(mngr.getMyID())) {
            replyPayload = new RoutineKGroupLeaderInfoMessagePayload(
                    epochNo, List.of(devID), replySeqNo, src);
            replyMsgType = MessageType.ROUTINE_KGROUP_LEADER_INFO;
        } else {
            String rtnID = getMonitored1();
            mngr.log(
                    kgrp,
                    "Routine " + rtnID + "-" + rtnSeqNo
                            + " was granted a lock request for device " + devID);
            replyPayload = new LockRequestedAckMessagePayload(epochNo, List.of(devID), replySeqNo);
            replyMsgType = MessageType.LOCK_REQUESTED_ACK;
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
