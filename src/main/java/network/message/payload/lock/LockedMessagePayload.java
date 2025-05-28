package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LockStrategy;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class LockedMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 8889721375113625608L;

    public int rtnSeqNo;

    public LockedMessagePayload(
            int epochNo, List<String> rtnID, String devID, int rtnSeqNo) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.devID = devID;
        this.rtnSeqNo = rtnSeqNo;
    }

    public LockedMessagePayload(int epochNo, String rtnID, String devID, int rtnSeqNo) {
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
            replyPayload = new LockedAckMessagePayload(epochNo, devID, replySeqNo);
            replyMsgType = MessageType.LOCKED_ACK;

            if (kgrp.existsRtnStage(rtnID, rtnSeqNo)) {
                if (kgrp.isRtnAcquiringLocks(rtnID, rtnSeqNo)) {
                    kgrp.acquiredDevLockForRtn(rtnID, rtnSeqNo, devID);
                    mngr.log(
                            kgrp,
                            "Routine " + rtnID + "-" + rtnSeqNo + ": device " + devID + " lock acquired");
                    mngr.log(kgrp, "State: " + kgrp.getRtnState(rtnID));
                }

                if (kgrp.isLockStrategy(LockStrategy.SERIAL) &&
                        kgrp.isRtnAcquiringLocks(rtnID, rtnSeqNo)) {
                    mngr.log(
                            kgrp,
                            "Routine " + rtnID + " sending LOCK_REQUEST for next device");
                    String latestLockedDevID = mngr.getRtnKGrp(rtnID).getRtnStage(rtnID, rtnSeqNo)
                            .getLatestLockedDevID();
                    if (latestLockedDevID.equals(devID))
                        mngr.requestDevLockForRtn(rtnID, rtnSeqNo);
                } else if (kgrp.areRtnLocksAcquired(rtnID, rtnSeqNo)) {
                    mngr.startRtnExec(rtnID, rtnSeqNo);

                    mngr.log(
                            kgrp,
                            "Routine " + rtnID + "-" + rtnSeqNo + " is starting execution!!!!",
                            true);
                }

                if (kgrp.isRtnReleasingLocks(rtnID, rtnSeqNo)) {
                    mngr.releaseDevLockForRtn(rtnID, rtnSeqNo, devID);
                }
            }
        }

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
