package routine;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class DumbRoutine extends Routine {
    private boolean triggered;

    public DumbRoutine(List<String> triggerDevIDs, List<String> touchedDevIDs, int length) {
        super(triggerDevIDs, touchedDevIDs, length);
        triggered = false;
    }

    public DumbRoutine(List<String> triggerDevIDs, List<String> touchedDevIDs) {
        if (triggerDevIDs != null)
            this.triggerDevIDs.addAll(triggerDevIDs);
        if (touchedDevIDs != null)
            this.touchedDevIDs.addAll(touchedDevIDs);
        triggered = false;
    }

    @Override
    public boolean isTriggered(Map<String, NavigableMap<Integer, DeviceState>> devStates, int curTS) {
        return triggered;
    }

    public void setTriggered() {
        triggered = true;
    }

    public void resetTriggered() {
        triggered = false;
    }
}
