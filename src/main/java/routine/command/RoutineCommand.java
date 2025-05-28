package routine.command;

import java.util.List;

import routine.DeviceState;

public class RoutineCommand {
    private String devID;
    private DeviceState newState;

    public RoutineCommand(String devID, String newState) {
        this.devID = devID;
        this.newState = new DeviceState(newState);
    }

    public RoutineCommand(String devId, String newState, String property) {
        this.devID = devId;
        this.newState = new DeviceState(newState, property);
    }

    public String getDevID() {
        return devID;
    }

    public DeviceState getNewState() {
        return newState;
    }

    public List<String> getTouchedDevIDs() {
        return List.of(devID);
    }

    public int getLength() {
        return 1;
    }
}
