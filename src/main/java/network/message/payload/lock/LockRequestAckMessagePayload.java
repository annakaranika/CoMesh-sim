package network.message.payload.lock;

import java.io.Serial;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class LockRequestAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -6501728481760721880L;

    public LockRequestAckMessagePayload(
            int epochNo, String rtnID, int srcSeqNo) {
        super(epochNo, KGroupType.ROUTINE, rtnID, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            return null;
        }
        String rtnID = getMonitored1();
        String devID = msgInfo.getMsgPayload().getMonitored1();
        int rtnSeqNo = msgInfo.getMsgPayload().getRtnSeqNo();

        mngr.recordLockRequestAckTime(rtnID, devID, rtnSeqNo);

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
