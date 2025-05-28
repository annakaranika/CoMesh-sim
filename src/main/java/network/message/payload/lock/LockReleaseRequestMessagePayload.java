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

public class LockReleaseRequestMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 3631481029651092528L;

    public int rtnSeqNo;

    public LockReleaseRequestMessagePayload(
            int epochNo, List<String> devID, String rtnID, int rtnSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID);
        this.rtnID = rtnID;
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

        // update the routine k-group's leader so that I can safely reply afterwards
        mngr.getRtnKGrp(rtnID).updateLeader(src);

        ReplyMessagePayload replyPayload;
        MessageType replyMsgType;

        if (!kgrp.isLeader(mngr.getMyID())) {
            replyPayload = new DeviceKGroupLeaderInfoMessagePayload(
                    epochNo, List.of(rtnID), replySeqNo, src);
            replyMsgType = MessageType.DEVICE_KGROUP_LEADER_INFO;
        } else {
            String devID = getMonitored1();
            mngr.log(
                    kgrp,
                    "Device " + devID + " k-group: lock release requested by routine "
                            + rtnID + "-" + rtnSeqNo);
            replyPayload = new LockReleaseRequestAckMessagePayload(
                    epochNo, List.of(rtnID), replySeqNo);
            replyMsgType = MessageType.LOCK_RELEASE_REQUEST_ACK;
            if (kgrp.isLocked(devID) && kgrp.isLocker(devID, rtnID, rtnSeqNo)) {
                int reqSeqNo = kgrp.getLockerReqSeqNo(devID);
                mngr.gatherQuorumForDevLockRelease(rtnID, rtnSeqNo, devID, reqSeqNo);
            }
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
