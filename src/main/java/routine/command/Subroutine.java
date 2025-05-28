package routine.command;

import java.util.List;

import routine.DeviceState;

public abstract class Subroutine {
    abstract public List<String> getTouchedDevIDs();

    abstract public int getLength();

    abstract public DeviceState getNewState(String devID);
}
