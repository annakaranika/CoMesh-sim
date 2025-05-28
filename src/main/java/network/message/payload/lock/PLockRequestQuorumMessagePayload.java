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

public class PLockRequestQuorumMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = -5659243460030671211L;
    private int reqSeqNo, rtnSeqNo;

    public PLockRequestQuorumMessagePayload(
            int epochNo, String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
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

        String devID = getMonitored1();
        if (!kgrp.isLeader(mngr.getMyID()) && kgrp.isLockRequestReceived(devID, rtnID, rtnSeqNo)) {
            kgrp.replicateNewLockRequest(devID, rtnID, rtnSeqNo, reqSeqNo);
            mngr.log(
                    kgrp,
                    "Device " + devID
                            + ": replicated lock request - new state = " + mngr.getDevState(devID));
        }

        ReplyMessagePayload replyPayload = new PLockRequestQuorumAckMessagePayload(
                epochNo, devID, replySeqNo);
        MessageType replyMsgType = MessageType.PLOCK_REQUEST_QUORUM_ACK;

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }

    public String getRtnID() {
        return rtnID;
    }

    public int getRtnSeqNo() {
        return rtnSeqNo;
    }

    public int getReqSeqNo() {
        return reqSeqNo;
    }
}
