package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.ReplyMessagePayload;

public class DeviceKGroupLeaderInfoMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -1694369111580051543L;

    public String leaderNodeID;

    public DeviceKGroupLeaderInfoMessagePayload(
            int epochNo, List<String> rtnID, int srcSeqNo, String leaderNodeID) {
        super(epochNo, KGroupType.ROUTINE, rtnID, srcSeqNo);
        this.leaderNodeID = leaderNodeID;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int seqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (epochNo != kgrp.getEpochNo()) {
            mngr.removeMsgInfo(srcSeqNo);
            return null;
        }

        UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
        if (msgInfo != null) {
            kgrp.updateLeader(leaderNodeID);
            msgInfo.updateDst(leaderNodeID);
        }

        return null;
    }

    @Override
    public String toString() {
        return "DEVICE_KGROUP_LEADER_INFO " + super.toString();
    }
}
