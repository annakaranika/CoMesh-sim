package network.message.payload.monitor;

import device.KGroupManager;
import device.SmartDevice;
import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import network.message.MessageType;
import network.message.payload.ReplyMessagePayload;

public class DeviceStateAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 5123863986542290870L;

    public DeviceStateAckMessagePayload(int epochNo, String devID, int srcSeqNo) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
    }

    @Override
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        if (!(node instanceof KGroupManager))
            return null;
        SmartDevice mngr = (SmartDevice) node;
        mngr.removeMsgInfo(srcSeqNo);
        return null;
    }
}
