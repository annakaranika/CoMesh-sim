package routine.statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;

import routine.DeviceState;

public class OrStatement extends RoutineStatement {
    List<RoutineStatement> innerStatements;

    public OrStatement(RoutineStatement statement) {
        innerStatements = new ArrayList<RoutineStatement>();
        innerStatements.add(statement);
    }

    public OrStatement(List<RoutineStatement> statements) {
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
        return new AndStatement(innerStatements);
    }

    public List<RoutineStatement> getInnerStatements() {
        return innerStatements;
    }

    public boolean isSatisfied(
            Map<String, NavigableMap<Integer, DeviceState>> devHistories, int timestamp) {
        boolean success = false;
        for (RoutineStatement innerStatement : innerStatements) {
            if (innerStatement.isSatisfied(devHistories, timestamp)) {
                success = true;
                break;
            }
        }
        return success;
    }

    @Override
    public List<String> getTriggerDevIDs() {
        return innerStatements.stream()
                .map(RoutineStatement::getTriggerDevIDs)
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        String strRepr = "[";
        for (RoutineStatement innerStatement : innerStatements) {
            strRepr += innerStatement.toString() + " or ";
        }
        return strRepr.substring(0, strRepr.length() - 4).toString() + "]";
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
