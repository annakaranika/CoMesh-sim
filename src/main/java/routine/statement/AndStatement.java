package routine.statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;

import routine.DeviceState;

public class AndStatement extends RoutineStatement {
    List<RoutineStatement> innerStatements;

    public AndStatement(RoutineStatement statement) {
        innerStatements = new ArrayList<RoutineStatement>();
        innerStatements.add(statement);
    }

    public AndStatement(List<RoutineStatement> statements) {
        innerStatements = statements;
    }

    public void add(RoutineStatement statement) {
        innerStatements.add(statement);
    }

    public void addAll(List<RoutineStatement> statements) {
        innerStatements.addAll(statements);
    }

    @Override
    public RoutineStatement negate() {
        ListIterator<RoutineStatement> listIterator = innerStatements.listIterator();
        while (listIterator.hasNext()) {
            listIterator.set(listIterator.next().negate());
        }
        return new OrStatement(innerStatements);
    }

    public List<RoutineStatement> getInnerStatements() {
        return innerStatements;
    }

    public boolean isSatisfied(Map<String, NavigableMap<Integer, DeviceState>> devHistories, int timestamp) {
        boolean success = true;

        for (RoutineStatement innerStatement : innerStatements) {
            if (!innerStatement.isSatisfied(devHistories, timestamp)) {
                success = false;
                break;
            }
        }
        return success;
    }

    @Override
    public List<String> getTriggerDevIDs() {
        return innerStatements.stream()
                .map(RoutineStatement::getTriggerDevIDs)
                .distinct()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        String strRepr = "[";
        for (RoutineStatement innerStatement : innerStatements) {
            strRepr += innerStatement.toString() + " and ";
        }
        return strRepr.substring(0, strRepr.length() - 5).toString() + "]";
    }

    @Override
    public int getInitialTime() {
        List<Integer> times = innerStatements.stream()
                .map(RoutineStatement::getInitialTime)
                .filter(x -> x != -1)
                .collect(Collectors.toList());
        return Collections.max(times);
    }
}
