package routine.statement.condition;

import java.util.Map;
import java.util.NavigableMap;

import routine.DeviceState;

public class TimeCondition extends RoutineCondition {
    public float value;

    public TimeCondition(ConditionRelation relation, float value) {
        this.relation = relation;
        this.value = value;
    }

    @Override
    public boolean isSatisfied(Map<String, NavigableMap<Integer, DeviceState>> devHistories, int curTS) {
        switch (relation) {
            case EQUAL:
                return curTS == value;
            case GREATER:
                return curTS > value;
            case GREATER_EQUAL:
                return curTS >= value;
            case LESS:
                return curTS < value;
            case LESS_EQUAL:
                return curTS <= value;
            case NOT_EQUAL:
                return curTS != value;
            default:
                // could throw exception here alternatively
                return false;
        }
    }

    @Override
    public String toString() {
        return "time" + relation.toString() + value;
    }

    @Override
    public int getInitialTime() {

        switch (relation) {
            case EQUAL:
            case GREATER_EQUAL:
                return (int) value;

            case GREATER:
                return (int) value + 1;

            default:
                return -1;
        }
    }
}
