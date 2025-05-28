package routine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class Routine {
    protected final List<String> triggerDevIDs = new ArrayList<>();
    protected final List<String> touchedDevIDs = new ArrayList<>();
    protected int length, seqNo;
    // A routine triggering should have a sequence number.
    // This way, we may support routine triggerings that happen
    // before a specific triggering's execution has finished
    // Specifically, this way, we may know if a lock request is new
    // or old and we may handle it appropriately.

    protected Routine() {
    }

    protected Routine(List<String> triggerDevIDs, List<String> touchedDevIDs, int length) {
        if (triggerDevIDs != null)
            this.triggerDevIDs.addAll(triggerDevIDs);
        if (touchedDevIDs != null)
            this.touchedDevIDs.addAll(touchedDevIDs);
        this.length = length;
        this.seqNo = -1;
    }

    public List<String> getTriggerDevIDs() {
        return triggerDevIDs;
    }

    public List<String> getTouchedDevIDs() {
        return touchedDevIDs;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public boolean shouldTrigger(String devID) {
        return !triggerDevIDs.isEmpty() && triggerDevIDs.contains(devID);
    }

    public boolean isTriggered(
            Map<String, NavigableMap<Integer, DeviceState>> devHistories, int curTS) {
        return false;
    }

    public int newTriggering() {
        return ++seqNo;
    }

    public DeviceState getNewState(String devID) {
        return null;
    }

    @Override
    public String toString() {
        return "trig devs: " + triggerDevIDs + ", cmd devs: " + touchedDevIDs + ", length: " + length;
    }

    public int getInitialTime() {
        return -1;
    }

}
