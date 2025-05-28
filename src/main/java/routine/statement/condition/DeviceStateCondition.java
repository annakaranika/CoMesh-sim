package routine.statement.condition;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Map.Entry;

import routine.Accumulator;
import routine.DeviceState;

public class DeviceStateCondition extends RoutineCondition {
    private String devID;
    private DeviceState value;

    public DeviceStateCondition(String devId, ConditionRelation relation, String value) {
        this.devID = devId;
        this.relation = relation;
        this.value = new DeviceState(value);
    }

    public DeviceStateCondition(String devId, ConditionRelation relation, String value, String mode_field) {
        this.devID = devId;
        this.relation = relation;
        this.value = new DeviceState(value, mode_field);
    }

    @Override
    public boolean isSatisfied(Map<String, NavigableMap<Integer, DeviceState>> devHistories, int curTS) {
        if (!devID.contains("acc")) {
            NavigableMap<Integer, DeviceState> devIdHistories = devHistories.get(devID);
            if (devIdHistories == null)
                return false;
            DeviceState oldState = devIdHistories.lastEntry().getValue();
            if (oldState == null)
                return false;

            if (oldState != null) {
                switch (relation) {
                    case EQUAL:
                        return oldState.equalsOne(value);
                    case NOT_EQUAL:
                        return !oldState.equalsOne(value);
                    case GREATER:
                        return oldState.isGreaterOne(value);
                    case GREATER_EQUAL:
                        return oldState.isGreaterOne(value) || oldState.equalsOne(value);
                    case LESS:
                        return oldState.isLessOne(value);
                    case LESS_EQUAL:
                        return oldState.isLessOne(value) || oldState.equalsOne(value);
                    default:
                        return false;
                }
            }
        } else {
            Accumulator acc = new Accumulator(devID);
            int count = 0;
            for (Entry<String, NavigableMap<Integer, DeviceState>> devHistory : devHistories.entrySet()) {
                if (!devHistory.getKey().startsWith(acc.getDevice()))
                    continue; // prefix

                for (Entry<Integer, DeviceState> e : devHistory.getValue().entrySet()) {
                    if (e.getKey() > curTS + acc.getElapsedMs()) {
                        boolean satisfied = false;
                        switch (relation) {
                            case EQUAL:
                                satisfied = e.getValue().equalsOne(value);
                                break;
                            case NOT_EQUAL:
                                satisfied = !e.getValue().equalsOne(value);
                                break;
                            case GREATER:
                                satisfied = e.getValue().isGreaterOne(value);
                                break;
                            case GREATER_EQUAL:
                                satisfied = e.getValue().isGreaterOne(value) || e.getValue().equalsOne(value);
                                break;
                            case LESS:
                                satisfied = e.getValue().isLessOne(value);
                                break;
                            case LESS_EQUAL:
                                satisfied = e.getValue().isLessOne(value) || e.getValue().equalsOne(value);
                                break;
                            default:
                                satisfied = false;
                                break;
                        }
                        if (satisfied)
                            count++;
                    }
                }
            }
            if (count >= acc.getDeviceCount())
                return true;
        }
        return false;
    }

    @Override
    public String getTriggerDevID() {
        String devName = devID;
        if (devID.startsWith("acc")) {
            Accumulator acc = new Accumulator(devID);
            devName = acc.getDevice();
        }
        return devName;
    }

    @Override
    public String toString() {
        return "dev " + devID + relation.toString() + value;
    }
}
