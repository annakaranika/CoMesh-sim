package routine.statement;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import routine.DeviceState;
import routine.statement.condition.RoutineCondition;

public class Statement extends RoutineStatement {
    RoutineCondition condition;

    public Statement(RoutineCondition condition) {
        this.condition = condition;
    }

    @Override
    public RoutineStatement negate() {
        return new Statement(condition.invertRelation());
    }

    public RoutineCondition getCondition() {
        return condition;
    }

    public boolean isSatisfied(Map<String, NavigableMap<Integer, DeviceState>> deviceHistories, int curTS) {
        return condition.isSatisfied(deviceHistories, curTS);
    }

    @Override
    public List<String> getTriggerDevIDs() {
        String devID = condition.getTriggerDevID();
        if (devID == null)
            return List.of();
        return List.of(devID);
    }

    @Override
    public String toString() {
        return condition.toString();
    }

    @Override
    public int getInitialTime() {
        return condition.getInitialTime();
    }
}
