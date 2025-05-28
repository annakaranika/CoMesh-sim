package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.state.LockRequest;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class LockReleaseQuorumAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -5205396067179785877L;

    public int reqSeqNo, rtnSeqNo;

    public LockReleaseQuorumAckMessagePayload(int epochNo, List<String> devID, int srcSeqNo, String rtnID, int reqSeqNo, int rtnSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
        this.rtnID = rtnID;
        this.reqSeqNo = reqSeqNo;
        this.rtnSeqNo = rtnSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
        Device node, KGroup kgrp, String src, int replySeqNo
    ) {
        if (!(node instanceof KGroupManager)) return null;
        KGroupManager mngr = (KGroupManager) node;
    
        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }
        
        QuorumMsgInfo msgInfo = mngr.getQuorumMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.log(kgrp, "Msg seq no " + srcSeqNo + " does not exist!\n\t" + this);
        }
        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            mngr.log(
                kgrp,
                "Received new vote for lock release (" + msgInfo.getPositiveRepliesCount()
                + "/" + neededAckCount + " now)"
            );
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                String devID = getMonitored1();
                LockRequest nextRtn = kgrp.releaseLock(devID, rtnID, rtnSeqNo, reqSeqNo);
                mngr.log(
                    kgrp,
                    "Device " + devID + " k-group: lock released for routine "
                    + rtnID + "-" + rtnSeqNo
                );

                mngr.remoteCall(
                    kgrp, KGroupType.ROUTINE, rtnID, MessageType.LOCK_RELEASED,
                    new LockReleasedMessagePayload(epochNo, rtnID, devID, rtnSeqNo),
                    true
                );
                if (nextRtn != null && !kgrp.isLockAcquired(
                        devID, nextRtn.getRtnID(), nextRtn.getRtnSeqNo())) {
                    mngr.log(
                        kgrp,
                        "Device " + devID +
                        " k-group: gathering quorum for granting lock to routine " + nextRtn
                    );
                    mngr.gatherQuorumForDevLockAcquisition(
                        nextRtn.getRtnID(), nextRtn.getRtnSeqNo(), devID
                    );
                }
                mngr.removeMsgInfo(srcSeqNo);
            }
        }
            
        return null;
    }

    @Override
    public String toString() {
        return "LOCK_RELEASE_QUORUM_ACK " + super.toString();
    }
}
