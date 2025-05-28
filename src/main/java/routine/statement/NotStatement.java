package routine.statement;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import routine.DeviceState;

public class NotStatement extends RoutineStatement {
    RoutineStatement innerStatement;

    public NotStatement(RoutineStatement statement) {
        this.innerStatement = statement.negate();
    }

    @Override
    public RoutineStatement negate() {
        return innerStatement.negate();
    }

    public RoutineStatement getInnerStatement() {
        return innerStatement;
    }

    public boolean isSatisfied(Map<String, NavigableMap<Integer, DeviceState>> devHistories, int curTS) {
        return innerStatement.isSatisfied(devHistories, curTS);
    }

    @Override
    public List<String> getTriggerDevIDs() {
        return innerStatement.getTriggerDevIDs();
    }

    @Override
    public String toString() {
        return innerStatement.toString();
    }

    @Override
    public int getInitialTime() {
        return innerStatement.getInitialTime();
    }
}
