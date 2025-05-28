package routine;

import java.util.Map;
import java.util.NavigableMap;

import routine.command.Subroutine;
import routine.statement.RoutineStatement;

public class DetailedRoutine extends Routine {
    private RoutineStatement conditions;
    private Subroutine cmds;

    public DetailedRoutine(RoutineStatement conditions, Subroutine cmds) {
        super(conditions.getTriggerDevIDs(), cmds.getTouchedDevIDs(), cmds.getLength());
        this.conditions = conditions;
        this.cmds = cmds;
    }

    public RoutineStatement getConditions() {
        return conditions;
    }

    public Subroutine getCmds() {
        return cmds;
    }

    @Override
    public int getLength() {
        return cmds.getLength();
    }

    @Override
    public boolean isTriggered(
            Map<String, NavigableMap<Integer, DeviceState>> devHistories, int timestamp) {
        return conditions.isSatisfied(devHistories, timestamp);
    }

    @Override
    public DeviceState getNewState(String devID) {
        return cmds.getNewState(devID);
    }

    @Override
    public int getInitialTime() {
        return conditions.getInitialTime();
    }
}