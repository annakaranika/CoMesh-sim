package device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import network.Network;
import network.message.Message;
import network.message.MessageType;
import network.message.payload.MessagePayload;
import routine.DeviceState;
import routine.Routine;
import simulate.Event;

public abstract class Device {
    protected String myID;
    protected Network network;
    protected int _ts = -1, _seqNo = -1;
    
    // list of global events per time unit
    protected final Map<Integer, List<Event>> events = new TreeMap<Integer, List<Event>>();
    protected final Map<String, Routine> routines = new HashMap<>();
    protected final List<String> rtnTrigs = new ArrayList<>();

    public Device(
        String myID, Network network, int initialTS, Map<Integer, List<Event>> events,
        Map<String, Routine> routines
    ) {
        this.myID = myID;
        this.network = network;
        _ts = initialTS - 1;
        for (Map.Entry<Integer, List<Event>> e: events.entrySet()) {
            this.events.put(e.getKey(), new ArrayList<Event>());
            this.events.get(e.getKey()).addAll(e.getValue());
        }
        log("routines: " + routines);
        if (routines != null && !routines.isEmpty()) {
            this.routines.putAll(routines);
        }
    }

    public IoTDevice getIoTDev() {
        if (this instanceof IoTDevice) return (IoTDevice) this;
        return null;
    }

    public KGroupManager getKGrpMngr() {
        if (this instanceof KGroupManager) return (KGroupManager) this;
        return null;
    }

    public CentralizedManager getCentralMngr() {
        if (this instanceof CentralizedManager) return (CentralizedManager) this;
        return null;
    }

    public void log(Object text, boolean debug) {
        Network.log(myID, text, debug);
    }

    public void log(Object text) {
        Network.log(myID, text);
    }

    public String getMyID() { return myID; }

    public int getCurTS() { return _ts; }

    public int incrementTS() { return ++ _ts; }

    public void processEvents() {}

    public boolean isNodeOnline() { return true; }

    public DeviceState getDevState() { return null; }

    public int getNextSeqNo() { return ++ _seqNo; }

    public void addEvent(int ts, Event e) {
        if (!events.containsKey(ts)) {
            events.put(ts, new ArrayList<>());
        }
        events.get(ts).add(e);
    }

    public List<String> getTriggered() {
        return rtnTrigs;
    }

    public void clearTriggered() {
        rtnTrigs.clear();
    }

    public void unicast(String dst, MessageType type, MessagePayload payload, int msgSeqNo) {
        int dstTS = _ts + network.getRouteOWD(myID, dst);
        Message msg = new Message(
            myID, _ts, msgSeqNo, dst, dstTS, type, payload
        );
        network.sendMsg(msg);
    }

    public void recvAndProcessMsgs(int curTS) {}

    public boolean existUnprocessedEvents() { return false; }
}
