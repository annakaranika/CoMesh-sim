package network.message.payload.lock;

import java.io.Serial;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class PLockLockMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 4527894244950821016L;
    public int rtnSeqNo;
    public int requestSeqNo;

    public PLockLockMessagePayload(
            int epochNo, String devID, String rtnID, int rtnSeqNo, int requestSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID);
        this.rtnID = rtnID;
        this.rtnSeqNo = rtnSeqNo;
        this.requestSeqNo = requestSeqNo;
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
                    epochNo, devID, replySeqNo, src);
            replyMsgType = MessageType.ROUTINE_KGROUP_LEADER_INFO;
        } else {
            String devID = getMonitored1();
            mngr.log(
                    kgrp,
                    "PLockLock for dev " + devID + " from routine " + rtnID + "-" + rtnSeqNo);

            mngr.gatherQuorumForDevLockAcquisition(rtnID, rtnSeqNo, devID, requestSeqNo);
            // Send ACK back to routine leader.
            replyMsgType = MessageType.PLOCK_LOCK_ACK;
            replyPayload = new PLockLockAckMessagePayload(
                    epochNo, rtnID /* send to routine leader */, replySeqNo /* message src seq no */
            );
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
