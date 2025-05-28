package network.message.payload.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import device.KGroupManager;
import device.CentralizedManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.UnicastMsgInfo;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;
import routine.DeviceState;

public class DeviceStateMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 8128151605419785570L;

    private DeviceState state;
    private List<String> triggered = new ArrayList<>();

    public DeviceStateMessagePayload(String devID, DeviceState state, int srcSeqNo) {
        super(KGroupType.DEVICE, devID, srcSeqNo);
        this.state = state;
    }

    public DeviceStateMessagePayload(String devID, List<String> triggered, int srcSeqNo) {
        super(KGroupType.DEVICE, devID, srcSeqNo);
        this.triggered.addAll(triggered);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (node instanceof KGroupManager) {
            KGroupManager mngr = (KGroupManager) node;

            if (!kgrp.isLeader(mngr.getMyID())) {
                mngr.removeMsgInfo(srcSeqNo);
                return null;
            }

            UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
            if (msgInfo == null) {
                mngr.log(kgrp, "UnicastMsgInfo not found for DeviceStateRequest to "
                        + src + " and srcSeqNo " + srcSeqNo);
                mngr.logMsgsInfo();
                mngr.removeMsgInfo(srcSeqNo);
                return null;
            }

            if (!triggered.isEmpty()) {
                mngr.log(kgrp, "Received DEVICE_STATE msg triggering routines " + triggered);
                MessagePayload payload;
                MessageType msgType = MessageType.DEVICE_STATE_FW;
                for (String rtnID : triggered) {
                    payload = new DeviceStateFwMessagePayload(kgrp.getEpochNo(), rtnID, src, true);
                    mngr.remoteCall(kgrp, KGroupType.ROUTINE, rtnID, msgType, payload, false);
                }

                mngr.removeMsgInfo(srcSeqNo);
                return null;
            }

            List<Integer> prevSeqNos = mngr.getUnicastSeqNosForDevStateReq(src, srcSeqNo);
            if (!prevSeqNos.isEmpty()) {
                for (Integer prevSeqNo : prevSeqNos) {
                    mngr.removeMsgInfo(prevSeqNo);
                }
            }

            boolean diff;
            if (Objects.equals(src, mngr.getMyID())) {
                diff = mngr.updatePrevDevState(state);
            } else {
                diff = mngr.updateDevState(src, state);
            }
            mngr.log(
                    kgrp,
                    "State updated for device " + src + " to " + state + ", diff: " + diff);
            if (diff) {
                MessageType msgType = MessageType.DEVICE_STATE_FW;
                MessagePayload payload;
                List<String> rtnIDs = kgrp.getRtnsToNotify(src);
                mngr.log(
                        kgrp,
                        "Informing the k-group leaders of rtns " + rtnIDs +
                                " for dev " + src + "'s state update");
                for (String rtnID : rtnIDs) {
                    payload = new DeviceStateFwMessagePayload(kgrp.getEpochNo(), rtnID, src, state);
                    mngr.remoteCall(kgrp, KGroupType.ROUTINE, rtnID, msgType, payload, false);
                }
            }

            mngr.removeMsgInfo(srcSeqNo);
        } else if (node instanceof CentralizedManager) {
            CentralizedManager mngr = (CentralizedManager) node;

            UnicastMsgInfo msgInfo = mngr.getUnicastMsgInfo(srcSeqNo);
            if (msgInfo == null) {
                mngr.log(kgrp, "UnicastMsgInfo not found for DeviceStateRequest to "
                        + src + " and srcSeqNo " + srcSeqNo);
                mngr.removeMsgInfo(srcSeqNo);
                return null;
            }

            boolean diff = mngr.updateDevState(src, state);
            mngr.log(kgrp, "State updated for device " + src + " to " + state + ", diff: " + diff); // , true);
            if (diff) {
                List<String> rtnIDs = kgrp.getRtnsToNotify(src);
                int rtnSeqNo;
                for (String rtnID : rtnIDs) {
                    if (mngr.isRtnTriggered(rtnID)) {
                        mngr.log(kgrp, "Rtn " + rtnID + " triggered", true);
                        rtnSeqNo = mngr.setRtnTriggered(rtnID);
                        boolean locked = mngr.lockDevForRtn(rtnID, rtnSeqNo);
                        while (locked)
                            locked = mngr.lockDevForRtn(rtnID, rtnSeqNo);
                    }
                }
            }
        }
        return null;
    }
}
