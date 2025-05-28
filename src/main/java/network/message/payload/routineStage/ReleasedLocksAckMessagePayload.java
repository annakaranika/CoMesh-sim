package network.message.payload.routineStage;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class ReleasedLocksAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 4548420034890318714L;

    public ReleasedLocksAckMessagePayload(int epochNo, String rtnID, int srcSeqNo) {
        super(epochNo, KGroupType.ROUTINE, rtnID, srcSeqNo);
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
            mngr.log(kgrp, "Msg seq no " + srcSeqNo + " does not exist!\n\t" + this);
        }
        if (msgInfo != null && !msgInfo.isApproved()) {
            msgInfo.newReply(src, true);
            final int neededAckCount = mngr.getF() + 1;
            mngr.log(kgrp, "Received new locks released ack (" + msgInfo.getPositiveRepliesCount()
                    + "/" + neededAckCount + " now)");
            if (msgInfo.getPositiveRepliesCount() >= neededAckCount) {
                String rtnID = getMonitored1();
                ReleasedLocksMessagePayload ogPayload = (ReleasedLocksMessagePayload) msgInfo.getMsgPayload();
                int rtnSeqNo = ogPayload.rtnSeqNo;
                mngr.log(kgrp, "Routine " + rtnID + "-" + rtnSeqNo + " locks released replicated");
                mngr.removeMsgInfo(srcSeqNo);
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "RELEASED_LOCKS_ACK " + super.toString();
    }
}
