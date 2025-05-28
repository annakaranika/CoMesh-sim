package device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import kgroup.DeviceKGroup;
import kgroup.KGroup;
import kgroup.Membership;
import kgroup.RoutineKGroup;
import metric.NodeMetrics;
import metric.NodeRole;
import metric.RoutineMetricSingleton;
import network.Network;
import network.message.Message;
import network.message.MessageType;
import network.message.MsgInfo;
import network.message.QuorumMsgInfo;
import network.message.UnicastMsgInfo;
import network.message.payload.MessagePayload;
import network.message.payload.execution.DeviceCommandMessagePayload;
import network.message.payload.monitor.DeviceStateCheckMessagePayload;
import routine.DeviceState;
import routine.Routine;
import simulate.Event;

public class SmartDevice extends Device {
    protected int nodeLimit, procCount = 0;
    protected int minTriggerOffset, devMntrPeriod;
    protected Map<String, Membership> membershipList = new HashMap<>();
    protected Map<Integer, MsgInfo> msgsInfo = new HashMap<>();
    private DeviceState prevState = null;
    private final Map<String, NavigableMap<Integer, DeviceState>> deviceHistories = new HashMap<>();

    protected RoutineMetricSingleton rtnMetric = RoutineMetricSingleton.getInstance();
    protected NodeMetrics nodeMetric;

    public SmartDevice(
            String myID, int nodeLimit, Network network, int initialTS, int minTriggerOffset,
            int devMntrPeriod, Map<Integer, List<Event>> events, Map<String, Routine> routines,
            Map<String, Membership> membershipList) {
        super(myID, network, initialTS, events, routines);
        this.nodeLimit = nodeLimit;
        this.minTriggerOffset = minTriggerOffset;
        this.devMntrPeriod = devMntrPeriod;
        this.membershipList.putAll(membershipList);
        nodeMetric = new NodeMetrics(myID, initialTS);
    }

    public void log(KGroup kgrp, Object text, boolean debug) {
        text = (kgrp != null ? kgrp + " - " : "") + text;
        Network.log(myID, text, debug);
    }

    public void log(KGroup kgrp, Object text) {
        text = (kgrp != null ? kgrp + " - " : "") + text;
        Network.log(myID, text);
    }

    public void logMsgsInfo() {
        Network.log(myID, msgsInfo, true);
    }

    public int getDevMntrPeriod() {
        return devMntrPeriod;
    }

    protected KGroup getResponsibleKGrp(Message msg) {
        return getResponsibleKGrp(msg.getPayload());
    }

    protected KGroup getResponsibleKGrp(MessagePayload payload) {
        return null;
    }

    public Routine getRtn(String rtnID) {
        return routines.get(rtnID);
    }

    public RoutineKGroup getRtnKGrp(String rtnID) {
        return null;
    }

    public RoutineKGroup getRtnKGrp(List<String> rtnID) {
        return null;
    }

    public DeviceKGroup getDevKGrp(String devID) {
        return null;
    }

    public DeviceKGroup getDevKGrp(List<String> devID) {
        return null;
    }

    public boolean isNodeOnline(String nodeID) {
        return false;
    }

    public Map<String, DeviceState> getDevStates() {
        Map<String, DeviceState> devStates = new HashMap<>();
        for (Entry<String, NavigableMap<Integer, DeviceState>> devHistory : deviceHistories.entrySet()) {
            if (!devHistory.getValue().isEmpty())
                devStates.put(
                        devHistory.getKey(), devHistory.getValue().lastEntry().getValue());
        }
        return devStates;
    }

    public DeviceState getDevState(String devID) {
        return getDevStates().get(devID);
    }

    @Override
    public DeviceState getDevState() {
        return getDevStates().get(getMyID());
    }

    public Map<String, DeviceState> getDevStates(String rtnID) {
        if (getRtn(rtnID) == null)
            log(rtnID + " not in routines: " + routines.keySet(), true); // return Map.of();
        return getRtn(rtnID).getTriggerDevIDs().stream().distinct()
                .filter(x -> getDevState(x) != null)
                .collect(Collectors.toMap(x -> x, this::getDevState));
    }

    public boolean areAllDevStatesNull() {
        return getDevStates().values().stream().allMatch(s -> s == null);
    }

    public boolean updatePrevDevState(DeviceState newState) {
        boolean diff;
        if (prevState == null) {
            diff = newState != null;
        } else {
            diff = !prevState.equals(newState);
        }
        log("Smart Device");
        updateDevState(newState);
        prevState = newState;
        return diff;
    }

    public boolean updateDevState(DeviceState newDevState) {
        return updateDevState(getMyID(), newDevState);
    }

    public boolean updateDevState(String devID, DeviceState newDevState) {
        DeviceState oldDevState; // = devStates.put(devID, newDevState);
        if (!deviceHistories.containsKey(devID))
            deviceHistories.put(devID, new TreeMap<>());
        if (deviceHistories.get(devID).isEmpty())
            oldDevState = null;
        else
            oldDevState = deviceHistories.get(devID).get(deviceHistories.get(devID).lastKey());
        boolean diff = true;
        if (oldDevState == null) {
            if (newDevState == null)
                diff = false;
        } else {
            if (oldDevState.isList()) {
                List<String> keys = new ArrayList<String>(newDevState.getList().keySet());
                for (Entry<String, DeviceState> e : oldDevState.getList().entrySet()) {
                    if (keys.contains(e.getKey()))
                        continue;
                    newDevState.putProperty(e.getKey(), e.getValue());
                }
            }
            diff = !oldDevState.equals(newDevState);
        }
        deviceHistories.get(devID).put(getCurTS(), newDevState);
        log("Updated dev " + devID + "'s state from " + oldDevState + " to " + newDevState); // ,
                                                                                             // devID.equals("tsa_1304"));
        return diff;
    }

    public void removeMsgInfo(int seqNo) {
        msgsInfo.remove(seqNo);
    }

    public UnicastMsgInfo getUnicastMsgInfo(int srcSeqNo) {
        if (msgsInfo.get(srcSeqNo) instanceof QuorumMsgInfo) {
            log("quorum srcSeqNo " + srcSeqNo + ": " + getQuorumMsgInfo(srcSeqNo));
        }
        return (UnicastMsgInfo) msgsInfo.get(srcSeqNo);
    }

    public QuorumMsgInfo getQuorumMsgInfo(int srcSeqNo) {
        if (msgsInfo.get(srcSeqNo) instanceof UnicastMsgInfo) {
            log("unicast srcSeqNo " + srcSeqNo + ": " + getUnicastMsgInfo(srcSeqNo));
        }
        return (QuorumMsgInfo) msgsInfo.get(srcSeqNo);
    }

    public Map<String, NavigableMap<Integer, DeviceState>> getDeviceHistories() {
        return deviceHistories;
    }

    public void clearDeviceHistories(String devId) {
        deviceHistories.get(devId).clear();
    }

    public void resetAllDeviceHistories() {
        for (Map<Integer, DeviceState> inner_map : deviceHistories.values())
            inner_map.clear();
        deviceHistories.clear();
    }

    public int remoteCall(
            String dst, MessageType type, MessagePayload payload, boolean boundedWait) {
        int routeRTT = network.getRouteOWD(myID, dst) * 2;
        int seqNo = getNextSeqNo();
        UnicastMsgInfo msgInfo = new UnicastMsgInfo(
                type, payload, null, null, dst, boundedWait, routeRTT, getCurTS());
        msgsInfo.put(seqNo, msgInfo);
        // log("Increased msg seq no to " + seqNo + "\n\t" + msgsInfo);
        unicast(dst, type, payload, seqNo);
        return seqNo;
    }

    public void monitorDevs(List<String> devIDs) {
        DeviceStateCheckMessagePayload payload = new DeviceStateCheckMessagePayload();
        log("Checking up on dev " + devIDs + "'s states");
        for (String devID : devIDs) {
            remoteCall(devID, MessageType.DEVICE_STATE_CHECK, payload, false);
        }
    }

    public void actuateCmd(String devIP, DeviceState newState, String rtnID) {
        MessagePayload payload = new DeviceCommandMessagePayload(newState, rtnID);
        remoteCall(devIP, MessageType.DEVICE_COMMAND, payload, false);
    }

    public int setRtnTriggered(String rtnID) {
        return -1;
    }

    public int setRtnTriggered(String rtnID, int rtnSeqNo) {
        return -1;
    }

    public int setRtnTriggeredTS(String rtnID, int ts) {
        return -1;
    }

    public boolean isRtnTriggered(String rtnID) {
        if (getRtn(rtnID) == null)
            log(rtnID + " not in routines: " + routines.keySet(), true);
        return getRtn(rtnID).isTriggered(deviceHistories, _ts);
    }

    public void startRtnExec(String rtnID, int rtnSeqNo) {
        recordStartExecTime(rtnID, rtnSeqNo);
        int rtnEnd = getCurTS() + getRtn(rtnID).getLength();
        getRtnKGrp(rtnID).startRtnExec(rtnID, rtnSeqNo, rtnEnd);
        if (!events.containsKey(rtnEnd)) {
            events.put(rtnEnd, new ArrayList<Event>());
        }
        events.get(rtnEnd).add(new Event(rtnID, rtnSeqNo));

        List<String> touchedDevIDs = getRtnKGrp(rtnID).getTouchedDevIDs(rtnID);
        for (String devIP : touchedDevIDs) {
            if (getRtn(rtnID).getLength() > 0)
                getRtnKGrp(rtnID).storeOldDevState(devIP, getDevState(devIP));
            actuateCmd(devIP, getRtn(rtnID).getNewState(devIP), rtnID);
        }

        updateRtnPreRtnLockRelease(rtnID, rtnSeqNo, touchedDevIDs);
        printDevLockReleaseMap(touchedDevIDs);
    }

    public void releaseDevLockForRtn(String rtnID, int rtnSeqNo, String devIP) {
    }

    public void releaseDevLocksForRtn(String rtnID, int rtnSeqNo) {
    }

    public void finishRtnExec(String rtnID, Integer rtnSeqNo) {
        List<String> touchedDevIDs = getRtnKGrp(rtnID).getTouchedDevIDs(rtnID);
        if (getRtn(rtnID).getLength() > 0) {
            for (String devIP : touchedDevIDs) {
                if (getRtnKGrp(rtnID).getOldDevState(devIP) != null)
                    actuateCmd(devIP, getRtnKGrp(rtnID).getOldDevState(devIP), rtnID);
            }
        }

        getRtnKGrp(rtnID).setRtnExecuted(rtnID, rtnSeqNo);

        recordRtnLockReleaseTime(rtnID, rtnSeqNo);
        recordMultiDevLockReleaseTime(touchedDevIDs);
        log(
                getRtnKGrp(rtnID),
                "Routine " + rtnID + "-" + rtnSeqNo + " finished execution, releasing devices "
                        + touchedDevIDs);
        getRtnKGrp(rtnID).startReleasingRtnLocks(rtnID, rtnSeqNo);
        for (String devIP : touchedDevIDs) {
            releaseDevLockForRtn(rtnID, rtnSeqNo, devIP);
        }
    }

    public void startRtnRoles(NodeRole nodeRole, List<String> rtnIDs) {
        nodeMetric.startRtnRoles(nodeRole, rtnIDs, getCurTS());
    }

    public void startDevRoles(NodeRole nodeRole, List<String> devIPs) {
        nodeMetric.startDevRoles(nodeRole, devIPs, getCurTS());
    }

    public void finalizeRole() {
        finalizeRole(getCurTS());
    }

    public void finalizeRole(int expLength) {
        nodeMetric.finishRtnRoles(expLength);
        nodeMetric.finishDevRoles(expLength);
    }

    public String getRoleTimeInString() {
        return nodeMetric.getRoleTimeInString();
    }

    public Map<Integer, Integer> getRoleCountOverTime(NodeRole role) {
        return nodeMetric.getRoleCountOverTime(role);
    }

    public void recordTriggerUsrTime(String rtnID, int rtnSeqNo) {
        rtnMetric.recordTriggerUsrTime(rtnID, rtnSeqNo, getCurTS());
    }

    public void recordTriggerSysTime(String rtnID, int rtnSeqNo) {
        rtnMetric.recordTriggerSysTime(rtnID, rtnSeqNo, getCurTS());
    }

    public void recordTriggerAckTime(String rtnID, int rtnSeqNo) {
        rtnMetric.recordTriggerAckTime(rtnID, rtnSeqNo, getCurTS());
    }

    public void recordStartExecTime(String rtnID, int rtnSeqNo) {
        rtnMetric.recordStartExecutionTime(rtnID, rtnSeqNo, getCurTS());
    }

    public void updateRtnPreRtnLockRelease(
            String rtnID, int rtnSeqNo, List<String> touchedDevIPs) {
        rtnMetric.updateRtnPreRtnLockRelease(rtnID, rtnSeqNo, touchedDevIPs);
    }

    public void printDevLockReleaseMap(List<String> touchedDevIPs) {
        rtnMetric.printDevLockReleaseMap(touchedDevIPs);
    }

    public void recordRtnLockReleaseTime(String rtnID, int rtnSeqNo) {
        rtnMetric.recordRtnLockReleaseTime(rtnID, rtnSeqNo, getCurTS());
    }

    public void recordMultiDevLockReleaseTime(List<String> touchedDevIPs) {
        rtnMetric.recordMultiDevLockReleaseTime(touchedDevIPs, getCurTS());
    }
}
