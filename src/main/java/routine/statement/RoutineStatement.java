package routine.statement;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import routine.DeviceState;

public abstract class RoutineStatement {
    public abstract RoutineStatement negate();

    public abstract boolean isSatisfied(Map<String, NavigableMap<Integer, DeviceState>> devHistories, int timestamp);

    public List<String> getTriggerDevIDs() {
        return List.of();
    }

    public abstract String toString();

    public abstract int getInitialTime();
}