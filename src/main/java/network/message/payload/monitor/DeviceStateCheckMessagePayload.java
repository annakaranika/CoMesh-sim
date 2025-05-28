package network.message.payload.monitor;

import device.Device;
import device.IoTDevice;
import device.SmartDevice;
import javafx.util.Pair;
import kgroup.KGroup;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;
import routine.DeviceState;

public class DeviceStateCheckMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 5123861986542290870L;

    public DeviceStateCheckMessagePayload() {}

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
        Device node, KGroup kgrp, String src, int replySeqNo
    ) {
        MessageType replyMsgType = MessageType.DEVICE_STATE;
        ReplyMessagePayload replyPayload;
        DeviceState devState;
        if (node instanceof SmartDevice) {
            SmartDevice smart = (SmartDevice) node;
            smart.log("Smart " + smart.getDevStates().toString());
            devState = smart.getDevState();
        }
        else if (node instanceof IoTDevice) {
            IoTDevice iot = (IoTDevice) node;
            iot.log("IoT");
            devState = iot.getDevState();
        }
        else {
            node.log("Device");
            devState = node.getDevState();
        }

        if (devState != null) {
            node.log(
                "Sending DEVICE_STATE msg to dev leader " + src
            );
            replyPayload = new DeviceStateMessagePayload(
                node.getMyID(), devState, replySeqNo
            );
        }
        else {
            node.log("Sending DEVICE_STATE msg triggering routines " + node.getTriggered());
            replyPayload = new DeviceStateMessagePayload(
                node.getMyID(), node.getTriggered(), replySeqNo
            );
            node.clearTriggered();
        }
        return new Pair<MessageType, ReplyMessagePayload>(replyMsgType, replyPayload);
    }
}
