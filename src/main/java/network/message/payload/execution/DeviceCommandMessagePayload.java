package network.message.payload.execution;

import java.io.Serial;

import device.SmartDevice;
import device.Device;
import device.IoTDevice;
import routine.DeviceState;
import javafx.util.Pair;
import kgroup.KGroup;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class DeviceCommandMessagePayload extends MessagePayload {
    @Serial
    private static final long serialVersionUID = 7447662879459978795L;
    private final DeviceState newState;

    public DeviceCommandMessagePayload(DeviceState newState, String rtnID) {
        this.newState = newState;
        this.rtnID = rtnID;
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (node instanceof SmartDevice) {
            SmartDevice mngr = (SmartDevice) node;
            mngr.updateDevState(mngr.getMyID(), newState);
        } else if (node instanceof IoTDevice) {
            IoTDevice dev = (IoTDevice) node;
            dev.updateDevState(newState);
        }

        MessageType replyMsgType = MessageType.DEVICE_COMMAND_ACK;
        ReplyMessagePayload replyPayload = new DeviceCommandAckMessagePayload(rtnID, replySeqNo);

        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
