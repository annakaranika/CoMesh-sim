package network.message.payload.execution;

import device.IoTDevice;
import device.SmartDevice;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.ReplyMessagePayload;

public class DeviceCommandAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 8883248263329096745L;

    public DeviceCommandAckMessagePayload(String rtnID, int srcSeqNo) {
        super(KGroupType.ROUTINE, rtnID, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (node instanceof IoTDevice)
            return null;
        SmartDevice mngr = (SmartDevice) node;

        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
