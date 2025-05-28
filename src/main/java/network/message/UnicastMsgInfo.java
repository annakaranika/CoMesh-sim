package network.message;

import kgroup.KGroup;
import network.message.payload.MessagePayload;

public class UnicastMsgInfo extends MsgInfo {
    private String dst;
    private KGroup otherKGrp;
    private boolean acknowledged;

    public UnicastMsgInfo(
            MessageType type, MessagePayload payload, KGroup thisKGroup, KGroup otherKGrp,
            String dst, boolean boundedWait, int routeOWD, int msgTS) {
        super(type, payload, thisKGroup, boundedWait, routeOWD, msgTS, payload.epochNo);
        this.otherKGrp = otherKGrp;
        this.dst = dst;
        acknowledged = false;
    }

    public String getDst() {
        return dst;
    }

    public void updateDst(String dst) {
        this.dst = dst;
    }

    public KGroup getThisKGrp() {
        return kgrp;
    }

    public KGroup getOtherKGrp() {
        return otherKGrp;
    }

    public void setAcknowledged() {
        acknowledged = true;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    @Override
    public String toString() {
        return type + " to " + dst + " (" + kgrp + ")";
    }
}
