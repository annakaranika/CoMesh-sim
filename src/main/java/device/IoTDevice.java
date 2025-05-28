package device;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import kgroup.Membership;
import network.Network;
import network.message.Message;
import routine.DeviceState;
import routine.Routine;
import simulate.Event;

public class IoTDevice extends Device {
    private DeviceState state;
    private Membership membership;

    public IoTDevice(
            String myID, Network network, int initialTS, Map<Integer, List<Event>> events,
            Map<String, Routine> routines, Membership membership) {
        super(myID, network, initialTS, events, routines);
        this.membership = membership;
    }

    @Override
    public boolean isNodeOnline() {
        return membership.equals(Membership.ONLINE);
    }

    @Override
    public int incrementTS() {
        // log("IoT dev incrementing ts!");
        int newTS = super.incrementTS();
        processEvents();
        return newTS;
    }

    @Override
    public void recvAndProcessMsgs(int curTS) {
        Set<Message> rcvdMsgs = network.recvPastMsgs(myID, curTS);

        for (Message rcvdMsg : rcvdMsgs) {
            switch (rcvdMsg.type) {
                case DEVICE_STATE_CHECK:
                case DEVICE_COMMAND:
                    rcvdMsg.process(this, null);
                    break;
                default:
                    log("DEFAULT Msg " + rcvdMsg.type + " received - not processing!");
            }
        }
    }

    @Override
    public void processEvents() {
        List<Integer> toRemoveLists = new ArrayList<>();
        int curTS = getCurTS();
        for (Integer checkTS : events.keySet()) {
            if (checkTS > curTS) {
                break;
            }
            if (events.get(checkTS) != null) {
                List<Event> toRemove = new ArrayList<>();
                for (Event e : events.get(checkTS)) {
                    switch (e.getType()) {
                        case ROUTINE_TRIGGERED:
                            String rtnID = e.getAffectedEntity();
                            if (routines.get(rtnID).shouldTrigger(myID))
                                if (!rtnTrigs.contains(e.getAffectedEntity()))
                                    rtnTrigs.add(e.getAffectedEntity());
                            break;
                        case NODE_FAILED:
                            if (e.getAffectedEntity().equals(myID))
                                membership = Membership.OFFLINE;
                            break;
                        case STATE_UPDATE:
                            if (e.getAffectedEntity().equals(myID)) {
                                updateDevState(new DeviceState(e.getNewDevState()));
                                // log(e + ", new state: " + state, myID.equals("tsa_1304"));
                            }
                            break;
                        default:
                            break;
                    }
                    toRemove.add(e);
                }
                events.get(checkTS).removeAll(toRemove);
                if (events.get(checkTS).isEmpty())
                    toRemoveLists.add(checkTS);
            }
        }
        for (Integer removeTS : toRemoveLists) {
            events.remove(removeTS);
        }
    }

    @Override
    public DeviceState getDevState() {
        return state;
    }

    public boolean updateDevState(DeviceState newDevState) {
        boolean diff = true;
        if (state == null) {
            if (newDevState == null)
                diff = false;
        } else {
            if (state.isList()) {
                List<String> keys = new ArrayList<String>(newDevState.getList().keySet());
                for (Entry<String, DeviceState> e : state.getList().entrySet()) {
                    if (keys.contains(e.getKey()))
                        continue;
                    newDevState.putProperty(e.getKey(), e.getValue());
                }
            }
            diff = !state.equals(newDevState);
        }
        log(
                "Updated dev " + getMyID() + "'s state from " + state + " to " + newDevState);
        state = newDevState;
        return diff;
    }
}
