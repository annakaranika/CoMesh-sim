package simulate;

import routine.DeviceState;

public class Event {
    private EventType type;
    private String affectedEntity;
    private int rtnSeqNo;
    private DeviceState newState;

    public Event(EventType type) {
        this.type = type;
    }

    public Event(EventType type, String affectedEntity) {
        this.type = type;
        this.affectedEntity = affectedEntity;
    }

    public Event(String rtnID, int rtnSeqNo) {
        this.type = EventType.ROUTINE_EXECUTED;
        this.affectedEntity = rtnID;
        this.rtnSeqNo = rtnSeqNo;
    }

    public Event(String devID, DeviceState newState) {
        this.type = EventType.STATE_UPDATE;
        this.affectedEntity = devID;
        this.newState = newState;
    }

    public Event(String type, String affectedEntity) {
        switch (type) {
            case "f":
                this.type = EventType.NODE_FAILED;
                break;
            case "j":
                this.type = EventType.NODE_JOINED;
                break;
            case "r":
                this.type = EventType.ROUTINE_TRIGGERED;
                break;
            case "e":
                this.type = EventType.ROUTINE_EXECUTED;
                break;
            case "s":
                this.type = EventType.STATE_UPDATE;
                break;
            case "m":
                this.type = EventType.STATE_MONITOR;
                break;
        }
        this.affectedEntity = affectedEntity;
    }

    public EventType getType() {
        return type;
    }

    public String getAffectedEntity() {
        return affectedEntity;
    }

    public int getRtnSeqNo() {
        return rtnSeqNo;
    }

    public DeviceState getNewDevState() {
        return newState;
    }

    public String toString() {
        return type.toString() + " " + affectedEntity +
                (type == EventType.ROUTINE_EXECUTED ? "-" + rtnSeqNo : "");
    }
}
