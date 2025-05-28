package device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import kgroup.DeviceKGroup;
import kgroup.KGroup;
import kgroup.KGroupSelectionPolicy;
import kgroup.LeaderElectionPolicy;
import kgroup.LockStrategy;
import kgroup.Membership;
import kgroup.RoutineKGroup;
import kgroup.state.LockRequest;
import metric.LoadMetric;
import network.Network;
import network.message.Message;
import network.message.payload.MessagePayload;
import routine.Routine;
import simulate.Event;

public class CentralizedManager extends SmartDevice {
    private DeviceKGroup devKGrp;
    private RoutineKGroup rtnKGrp;

    public CentralizedManager(
            String myID, int nodeLimit, Network network, List<String> nodesIDs, int initialTS,
            List<String> devIDs, Map<String, Routine> routines,
            int routineNo, int minTriggerOffset, int devMntrPeriod,
            SortedMap<Integer, List<Event>> events, Map<String, Membership> membershipList) {
        super(
                myID, nodeLimit, network, initialTS, minTriggerOffset, devMntrPeriod, events,
                routines, membershipList);

        devKGrp = new DeviceKGroup(
                myID, devIDs, new HashMap<String, Membership>(), 0, 1,
                LeaderElectionPolicy.STATIC, LockStrategy.SERIAL, KGroupSelectionPolicy.STATIC,
                network, routines);

        if (routineNo != 0) {
            rtnKGrp = new RoutineKGroup(
                    myID, routines, new HashMap<String, Membership>(), 0, 1,
                    LeaderElectionPolicy.STATIC, LockStrategy.SERIAL, KGroupSelectionPolicy.STATIC,
                    minTriggerOffset, network);
        }
    }

    @Override
    public boolean isNodeOnline() {
        return isNodeOnline(myID);
    }

    @Override
    public boolean isNodeOnline(String nodeID) {
        return membershipList.get(nodeID) == Membership.ONLINE;
    }

    @Override
    public void processEvents() {
        List<Integer> toRemoveLists = new ArrayList<>();
        int curTS = getCurTS();
        for (Integer checkTS : events.keySet()) {
            if (procCount >= nodeLimit)
                break;
            if (checkTS > curTS) {
                break;
            }
            if (events.get(checkTS) != null) {
                List<Event> toRemove = new ArrayList<>();
                for (Event e : events.get(checkTS)) {
                    if (procCount >= nodeLimit)
                        break;
                    switch (e.getType()) {
                        case NODE_FAILED:
                            if (e.getAffectedEntity().equals(myID))
                                membershipList.replace(myID, Membership.OFFLINE);
                            LoadMetric.recordLoad(myID, curTS);
                            toRemove.add(e);
                            break;
                        case ROUTINE_TRIGGERED:
                            String rtnID = e.getAffectedEntity();
                            if (routines.get(rtnID).shouldTrigger(myID))
                                if (!rtnTrigs.contains(rtnID))
                                    rtnTrigs.add(e.getAffectedEntity());
                            toRemove.add(e);
                            break;
                        case ROUTINE_EXECUTED:
                            if (getRtnKGrp(e.getAffectedEntity()).isLeader(myID)) {
                                finishRtnExec(e.getAffectedEntity(), e.getRtnSeqNo());
                            }
                            LoadMetric.recordLoad(myID, curTS);
                            toRemove.add(e);
                            break;
                        case STATE_MONITOR:
                            monitorDevs(devKGrp.getMonitored());
                            toRemove.add(e);
                            LoadMetric.recordLoad(myID, curTS);
                            break;
                        default:
                            toRemove.add(e);
                            break;
                    }
                    procCount++;
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
    public RoutineKGroup getRtnKGrp(String rtnID) {
        return rtnKGrp;
    }

    @Override
    protected KGroup getResponsibleKGrp(MessagePayload payload) {
        for (KGroup kgrp : List.of(rtnKGrp, devKGrp)) {
            if (kgrp.isInChargeOf(payload)) {
                return kgrp;
            }
        }
        return null;
    }

    @Override
    public void recvAndProcessMsgs(int curTS) {
        Set<Message> rcvdMsgs = network.recvPastMsgs(myID, getCurTS());
        for (Message rcvdMsg : rcvdMsgs) {
            switch (rcvdMsg.type) {
                case DEVICE_STATE:
                case DEVICE_STATE_CHECK:
                case DEVICE_COMMAND:
                case DEVICE_COMMAND_ACK:
                    rcvdMsg.process(this, null);
                    break;
                default:
                    log("DEFAULT Msg " + rcvdMsg.type + " received - not processing!", true);
            }
        }
    }

    public int getProcCount() {
        return procCount;
    }

    @Override
    public int incrementTS() {
        procCount = 0;
        int newTS = ++_ts;
        processEvents();
        return newTS;
    }

    public boolean lockDevForRtn(String rtnID, int rtnSeqNo) {
        String devID = rtnKGrp.nextDevToLock(rtnID, rtnSeqNo);
        if (devID == null) {
            return false;
        }
        log(
                "Next device for routine " + rtnID + "-" + rtnSeqNo + " to lock: " + devID);

        int reqSeqNo;
        if (devKGrp.isLockRequestReceived(devID, rtnID, rtnSeqNo)) {
            reqSeqNo = devKGrp.getLockRequestSeqNo(devID, rtnID, rtnSeqNo);
        } else {
            // sequence number assigned to the device lock request
            reqSeqNo = devKGrp.addReqToQueue(devID, rtnID, rtnSeqNo);
        }

        if (devKGrp.isLockAvailable(devID)) {
            devKGrp.lock(devID, rtnID, rtnSeqNo, reqSeqNo);
            rtnKGrp.acquiredDevLockForRtn(rtnID, rtnSeqNo, devID);

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void releaseDevLockForRtn(String rtnID, int rtnSeqNo, String devID) {
        // TODO
    }

    @Override
    public void releaseDevLocksForRtn(String rtnID, int rtnSeqNo) {
        // TODO: check
        LockRequest nextRtn;
        String nextRtnID;
        int nextRtnSeqNo;
        boolean canLock;
        for (String devID : routines.get(rtnID).getTouchedDevIDs()) {
            nextRtn = devKGrp.releaseLock(devID, rtnID, rtnSeqNo);
            canLock = true;
            while (canLock) {
                nextRtnID = nextRtn.getRtnID();
                nextRtnSeqNo = nextRtn.getRtnSeqNo();
                canLock = lockDevForRtn(nextRtnID, nextRtnSeqNo);
                if (rtnKGrp.areRtnLocksAcquired(nextRtnID, nextRtnSeqNo)) {
                    startRtnExec(nextRtnID, nextRtnSeqNo);
                }
            }
        }
    }
}
