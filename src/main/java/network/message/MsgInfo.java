package network.message;

import kgroup.KGroup;
import network.message.payload.MessagePayload;

public class MsgInfo {
    protected MessageType type;
    protected MessagePayload payload;
    protected KGroup kgrp;
    protected boolean boundedWait;
    protected int routeOWD, fstSendTS, lstSendTS, fstEpoch;

    public MsgInfo(
            MessageType type, MessagePayload payload, KGroup kgrp, boolean boundedWait,
            int routeOWD, int fstSendTS, int fstEpoch) {
        this.type = type;
        this.payload = payload;
        this.kgrp = kgrp;
        this.boundedWait = boundedWait;
        this.routeOWD = routeOWD;
        this.fstSendTS = this.lstSendTS = fstSendTS;
        this.fstEpoch = fstEpoch;
    }

    public UnicastMsgInfo getUniMsgInfo() {
        return (UnicastMsgInfo) this;
    }

    public QuorumMsgInfo getQuorumMsgInfo() {
        return (QuorumMsgInfo) this;
    }

    public MessageType getMsgType() {
        return type;
    }

    public boolean isMsgType(MessageType newType) {
        return type == newType;
    }

    public MessagePayload getMsgPayload() {
        return payload;
    }

    public KGroup getKGrp() {
        return kgrp;
    }

    public boolean isBounded() {
        return boundedWait;
    }

    public int getRouteOWD() {
        return routeOWD;
    }

    public int getRouteRTT() {
        return routeOWD * 2;
    }

    public int getFstEpoch() {
        return fstEpoch;
    }

    public int getFstTS() {
        return fstSendTS;
    }

    public int getLstTS() {
        return lstSendTS;
    }

    public int getNxtDstTS(int ts) {
        return ts + routeOWD;
    }

    public void updateLastTS(int lstSendTS) {
        this.lstSendTS = lstSendTS;
    }

    public int getTimePassedSinceFstSend(int ts) {
        return ts - fstSendTS;
    }

    public int getTimePassedSinceLstSend(int ts) {
        return ts - lstSendTS;
    }
}