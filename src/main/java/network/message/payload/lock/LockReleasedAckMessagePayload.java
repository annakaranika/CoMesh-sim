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

public class LockReleasedAckMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -6501728481760721880L;

    public LockReleasedAckMessagePayload(int epochNo, String devID, int srcSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (!kgrp.isMember(mngr.getMyID())) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
        if (msgInfo == null) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        String devID = getMonitored1();
        String rtnID = msgInfo.getMsgPayload().getMonitored1();
        int rtnSeqNo = msgInfo.getMsgPayload().getRtnSeqNo();
        if (kgrp.isLeader(mngr.getMyID())) {
            mngr.log(
                    kgrp,
                    "Received LOCK_RELEASED_ACK for rtn " + rtnID + "-" + rtnSeqNo
                            + " by device " + devID);
        }

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
