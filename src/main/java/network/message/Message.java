package network.message;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import metric.LoadMetric;
import network.Network;
import network.message.payload.MessagePayload;
import network.message.payload.ReplyMessagePayload;

public class Message implements Cloneable, Serializable {
    private static final long serialVersionUID = 6939491387675430673L;
    public String src, dst;
    public int srcTS, srcSeqNo, dstTS;
    public MessageType type; // deprecating it in favor of MessagePayload specific extensions
    public MessagePayload payload;

    public Message(
            String src, int srcTS, int srcSeqNo, String dst, int dstTS, MessageType type,
            MessagePayload payload) {
        this.src = src;
        this.srcTS = srcTS;
        this.srcSeqNo = srcSeqNo; // might not need source sequence number
        this.dst = dst;
        this.dstTS = dstTS;
        this.type = type;
        this.payload = payload;
    }

    public int getByteSize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this.clone());
            oos.flush();
            return bos.toByteArray().length;
        } catch (Exception ex) {
            Network.log(src, "Exception during msg byte size calculation", true);
            ex.printStackTrace(System.out);
            return -1;
        }
    }

    public MessagePayload getPayload() {
        return payload;
    }

    public void process(Device node, KGroup kgrp) {
        LoadMetric.recordLoad(dst, node.getCurTS());

        Pair<MessageType, ReplyMessagePayload> replyInfo = getPayload().process(
                node, kgrp, src, srcSeqNo);
        if (replyInfo == null) {
            return;
        }

        MessageType replyMsgType = replyInfo.getKey();
        ReplyMessagePayload replyPayload = replyInfo.getValue();
        int seqNo = node.getNextSeqNo();
        node.unicast(src, replyMsgType, replyPayload, seqNo);
    }

    public String toString() {
        return type + " msg from " + src + " to " + dst + " (arriving at time " + dstTS + ")";
    }
}