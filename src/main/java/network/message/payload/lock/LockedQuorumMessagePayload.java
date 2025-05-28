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

public class LockedQuorumMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = -6325764608392709083L;

    public int reqSeqNo, rtnSeqNo;

    public LockedQuorumMessagePayload(
            int epochNo, List<String> devID, String rtnID, int reqSeqNo, int rtnSeqNo) {
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

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            return null;
        }

        if (!kgrp.isLeader(mngr.getMyID())) {
            String devIP = getMonitored1();
            kgrp.replicateLockAcquisition(devIP, rtnID, rtnSeqNo, reqSeqNo);
            mngr.log(
                    kgrp,
                    "Device " + devIP + " k-group: replicated lock acquisition - new state = "
                            + mngr.getDevState(devIP));
        }

        ReplyMessagePayload replyPayload = new LockedQuorumAckMessagePayload(
                epochNo, entitiesIDs, replySeqNo, rtnID, rtnSeqNo, reqSeqNo);
        MessageType replyMsgType = MessageType.LOCKED_QUORUM_ACK;

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
