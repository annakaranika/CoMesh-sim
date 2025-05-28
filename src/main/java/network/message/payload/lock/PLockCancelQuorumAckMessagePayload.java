package network.message.payload.lock;

import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.ReplyMessagePayload;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;

public class PLockCancelQuorumAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = 3985041353348347310L;
    public int reqSeqNo, rtnSeqNo;

    public PLockCancelQuorumAckMessagePayload(int epochNo, List<String> devID, int srcSeqNo, String rtnID, int reqSeqNo,
            int rtnSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
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
                    "Received new vote for lock request (" + msgInfo.getPositiveRepliesCount()
                            + "/" + neededAckCount + " now)");
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                String devID = getMonitored1();
                PLockCancelledMessagePayload cancelled_payload = new PLockCancelledMessagePayload(
                        epochNo, rtnID, rtnSeqNo, devID);
                mngr.remoteCall(
                        kgrp, KGroupType.ROUTINE, rtnID,
                        MessageType.PLOCK_CANCELLED, cancelled_payload, false);
                mngr.removeMsgInfo(srcSeqNo);
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "PLOCK_CANCEL_QUORUM_ACK " + super.toString();
    }
}
