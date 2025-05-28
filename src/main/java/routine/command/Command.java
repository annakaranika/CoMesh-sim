package routine.command;

import java.util.List;

import routine.DeviceState;

public class Command extends Subroutine {
    // this class contains a single command
    private RoutineCommand cmd;

    public Command(RoutineCommand cmd) {
        this.cmd = cmd;
    }

    public RoutineCommand getCmd() {
        return cmd;
    }

    @Override
    public int getLength() {
        return cmd.getLength();
    }

    @Override
    public List<String> getTouchedDevIDs() {
        return cmd.getTouchedDevIDs();
    }

    public DeviceState getNewState(String devID) {
        if (cmd.getDevID().equals(devID))
            return cmd.getNewState();
        else
            return null;
    }
}
