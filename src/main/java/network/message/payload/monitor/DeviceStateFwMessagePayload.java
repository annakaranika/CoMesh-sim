package network.message.payload.monitor;

import routine.DeviceState;
import device.KGroupManager;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class DeviceStateFwMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 5123861986542260870L;

    private DeviceState state;
    private boolean triggered = false;

    public DeviceStateFwMessagePayload(
            int epochNo, String rtnID, String devID, DeviceState state) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.devID = devID;
        this.state = state;
    }

    public DeviceStateFwMessagePayload(
            int epochNo, String rtnID, String devID, boolean triggered) {
        super(epochNo, KGroupType.ROUTINE, rtnID);
        this.devID = devID;
        this.triggered = triggered;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        KGroupManager mngr = (KGroupManager) node;

        if (kgrp.isLeader(mngr.getMyID())) {
            mngr.log("Device State Fw");

            mngr.updateDevState(devID, state);

            String rtnID = getMonitored1();
            mngr.log(
                    "Rtn " + rtnID + ": device " + devID + " state changed to " + state);
            if (!mngr.areAllDevStatesNull())
                mngr.log("\tnew relevant states: " + mngr.getDevStates(rtnID));

            if (triggered || mngr.isRtnTriggered(rtnID)) {
                int rtnSeqNo = mngr.setRtnTriggeredTS(rtnID, mngr.getCurTS());
                if (rtnSeqNo != -1) {
                    mngr.recordTriggerSysTime(rtnID, rtnSeqNo);
                    mngr.gatherQuorumForRtnTrigger(rtnID, rtnSeqNo);
                    mngr.log(kgrp, "Rtn " + rtnID + "-" + rtnSeqNo + " triggered 1", true);
                }
            }
        }

        MessageType replyType = MessageType.DEVICE_STATE_ACK;
        ReplyMessagePayload replyPayload = new DeviceStateAckMessagePayload(
                epochNo, devID, replySeqNo);
        return new Pair<MessageType, ReplyMessagePayload>(replyType, replyPayload);
    }
}
