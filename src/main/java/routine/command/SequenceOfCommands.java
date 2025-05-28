package routine.command;

import java.util.ArrayList;
import java.util.List;

import routine.DeviceState;

public class SequenceOfCommands extends Subroutine {
    // the commands in this class must be performed sequentially
    private List<Subroutine> cmds;

    public SequenceOfCommands(List<Subroutine> cmds) {
        this.cmds = cmds;
    }

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
            length += sub.getLength();
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
