package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class LockedQuorumAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = 2704514377346524282L;
    public int reqSeqNo, rtnSeqNo;

    public LockedQuorumAckMessagePayload(
            int epochNo, List<String> devID, int srcSeqNo, String rtnID, int rtnSeqNo,
            int reqSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
        this.rtnID = rtnID;
        this.rtnSeqNo = rtnSeqNo;
        this.reqSeqNo = reqSeqNo;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int seqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo() || !kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        QuorumMsgInfo msgInfo = mngr.getQuorumMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.log(
                    kgrp, "Msg seq no " + srcSeqNo + " does not exist!\n\t" + this + " from " + src);
        }
        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            mngr.log(kgrp,
                    "Received new vote for lock request (" + msgInfo.getPositiveRepliesCount()
                            + "/" + neededAckCount + " now)");
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                String devID = getMonitored1();
                if (!kgrp.isLockAcquired(devID, rtnID, rtnSeqNo)) {
                    kgrp.lock(devID, rtnID, rtnSeqNo, reqSeqNo);

                    mngr.log(
                            kgrp,
                            "Device " + devID + " granted lock to routine "
                                    + rtnID + "-" + rtnSeqNo);
                    mngr.log(kgrp, "Device " + devID + " state: " + mngr.getDevState(devID));

                    MessagePayload payload = new LockedMessagePayload(
                            epochNo, List.of(rtnID), devID, rtnSeqNo);
                    mngr.remoteCall(
                            kgrp, KGroupType.ROUTINE, rtnID, MessageType.LOCKED, payload, false);
                }
                mngr.removeMsgInfo(srcSeqNo);
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "LOCKED_QUORUM_ACK " + super.toString();
    }
}
