package network.message.payload.lock;

import java.io.Serial;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.QuorumMsgInfo;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class PLockRequestQuorumAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = 7941972998006287283L;

    public PLockRequestQuorumAckMessagePayload(int epochNo, String devID, int srcSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
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

        String devID = getMonitored1();
        MessagePayload ogPayload = msgInfo.getMsgPayload();
        String rtnID = ogPayload.getRtnID();
        int rtnSeqNo = ogPayload.getRtnSeqNo();
        int reqSeqNo = ogPayload.getReqSeqNo();
        kgrp.lockRequestReplicated(devID, rtnID, rtnSeqNo, reqSeqNo);

        PLockRequestedMessagePayload pLockRequestedPayload = new PLockRequestedMessagePayload(
                epochNo, rtnID, rtnSeqNo, reqSeqNo, devID);

        mngr.remoteCall(
                kgrp, KGroupType.ROUTINE, rtnID,
                MessageType.PLOCK_REQUESTED, pLockRequestedPayload, false);

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
