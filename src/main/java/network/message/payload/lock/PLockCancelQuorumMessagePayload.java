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

public class PLockCancelQuorumMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = -274068365068723274L;
    public int reqSeqNo, rtnSeqNo;

    public PLockCancelQuorumMessagePayload(int epochNo, String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID);
        this.rtnID = rtnID;
        this.reqSeqNo = reqSeqNo;
        this.rtnSeqNo = rtnSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID()))
            return null;

        if (!kgrp.isLeader(mngr.getMyID())) {
            String devID = getMonitored1();
            kgrp.removeLockRequest(devID, rtnID, rtnSeqNo, reqSeqNo);
            mngr.log(
                    kgrp,
                    "Device " + devID
                            + ": canceling lock request - new state = " + mngr.getDevState(devID));
        }

        ReplyMessagePayload replyPayload = new PLockCancelQuorumAckMessagePayload(
                epochNo, entitiesIDs, replySeqNo, rtnID, reqSeqNo, rtnSeqNo);
        MessageType replyMsgType = MessageType.PLOCK_CANCEL_QUORUM_ACK;

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
