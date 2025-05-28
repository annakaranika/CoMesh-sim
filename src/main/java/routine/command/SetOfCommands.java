package routine.command;

import java.util.ArrayList;
import java.util.List;

import routine.DeviceState;

public class SetOfCommands extends Subroutine {
    // the commands in this class can be performed in parallel
    private List<Subroutine> cmds;

    public SetOfCommands(List<Subroutine> cmds) {
        this.cmds = cmds;
    }

    public List<Subroutine> getCmds() {
        return cmds;
    }

    @Override
    public List<String> getTouchedDevIDs() {
        List<String> touchedDevIDs = new ArrayList<>();

        for (Subroutine sub : cmds) {
            touchedDevIDs.addAll(sub.getTouchedDevIDs());
        }

        return touchedDevIDs;
    }

    @Override
    public int getLength() {
        int length = 0;
        for (Subroutine sub : cmds) {
            length = Math.max(length, sub.getLength());
        }
        return length;
    }

    public DeviceState getNewState(String devID) {
        DeviceState newState = null;
        for (Subroutine cmd : cmds) {
            newState = cmd.getNewState(devID);
            if (newState != null) {
                break;
            }
        }
        return newState;
    }
}
