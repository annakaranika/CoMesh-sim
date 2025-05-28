package kgroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kgroup.state.RoutineStage;
import network.Network;
import routine.DeviceState;
import routine.Routine;

public class RoutineKGroup extends KGroup {
    private final Map<String, Integer> seqNos = new HashMap<>();
    private final Map<String, DeviceState> oldDevStates = new HashMap<>();
    private final Map<String, Set<DeviceState>> oldDevStatesRecreation = new HashMap<>();
    private final Map<String, Integer> lastTriggered = new HashMap<>();
    private final int minTriggerOffset;
    private final Map<String, Routine> routines = new HashMap<>();

    public RoutineKGroup(
            String nodeID, Map<String, Routine> routines, Map<String, Membership> membershipList,
            int F, int K, LeaderElectionPolicy lep, LockStrategy ls, KGroupSelectionPolicy kGroupPolicy,
            int minTriggerOffset, Network network) {
        super(
                nodeID, new ArrayList<>(routines.keySet()), membershipList, KGroupType.ROUTINE,
                F, K, lep, ls, kGroupPolicy, network);
        this.minTriggerOffset = minTriggerOffset;
        for (String rtnID : routines.keySet()) {
            state.put(rtnID, new HashMap<Integer, RoutineStage>());
            seqNos.put(rtnID, 0);
            lastTriggered.put(rtnID, -minTriggerOffset);
        }
        if (!routines.isEmpty())
            this.routines.putAll(routines);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Integer, RoutineStage> getRtnState(
            Map<String, Object> state, String rtnID) {
        return ((Map<Integer, RoutineStage>) state.get(rtnID));
    }

    @Override
    public Map<Integer, RoutineStage> getRtnState(String rtnID) {
        return getRtnState(state, rtnID);
    }

    @Override
    public RoutineStage getRtnStage(
            Map<String, Object> state, String rtnID, int rtnSeqNo) {
        return getRtnState(state, rtnID).get(rtnSeqNo);
    }

    @Override
    public RoutineStage getRtnStage(String rtnID, int rtnSeqNo) {
        return getRtnStage(state, rtnID, rtnSeqNo);
    }

    // called by routine k-group leader
    @Override
    public Set<Integer> getRtnSeqNos(String rtnID) {
        return getRtnState(rtnID).keySet();
    }

    @Override
    public boolean existsRtnStage(String rtnID, int rtnSeqNo) {
        return getRtnStage(rtnID, rtnSeqNo) != null;
    }

    @Override
    public List<String> getTouchedDevIDs(String rtnID) {
        return routines.get(rtnID).getTouchedDevIDs();
    }

    // called by routine k-group leader
    @Override
    public Map<String, Integer> getSeqNos() {
        return seqNos;

    }

    // called by routine k-group leader
    @Override
    public void setSeqNos(Map<String, Integer> seqNos) {
        this.seqNos.clear();
        this.seqNos.putAll(seqNos);
    }

    // called by routine k-group leader
    @Override
    public void addLocalSeqNos(Map<String, Integer> localSeqNos) {
        for (String rtnID : entitiesIDs) {
            if (localSeqNos.get(rtnID) > seqNos.get(rtnID)) {
                seqNos.put(rtnID, localSeqNos.get(rtnID));
            }
        }
    }

    // called by routine k-group leader
    @Override
    public Map<String, Integer> getLastTriggered() {
        return lastTriggered;

    }

    // called by routine k-group leader
    @Override
    public void setLastTriggered(Map<String, Integer> lastTriggered) {
        this.lastTriggered.clear();
        this.lastTriggered.putAll(lastTriggered);
    }

    // called by routine k-group leader
    @Override
    public void addLocalLastTriggered(Map<String, Integer> localLastTriggered) {
        for (String rtnID : entitiesIDs) {
            if (localLastTriggered.get(rtnID) > lastTriggered.get(rtnID)) {
                lastTriggered.put(rtnID, localLastTriggered.get(rtnID));
            }
        }
    }

    // called by routine k-group leader
    @Override
    public Map<String, DeviceState> getOldDevStates() {
        return oldDevStates;
    }

    // called by routine k-group leader
    @Override
    public void setOldDevStates(Map<String, DeviceState> oldDevStates) {
        this.oldDevStates.clear();
        if (oldDevStates != null && oldDevStates.size() > 0)
            this.oldDevStates.putAll(oldDevStates);
    }

    // called by routine k-group leader
    @Override
    public void addLocalOldDevStates(Map<String, DeviceState> localOldDevStates) {
        for (String devIP : localOldDevStates.keySet()) {
            if (oldDevStatesRecreation.get(devIP) == null) {
                oldDevStatesRecreation.put(devIP, new HashSet<>());
            }
            oldDevStatesRecreation.get(devIP).add(localOldDevStates.get(devIP));
            if (Collections.frequency(
                    oldDevStatesRecreation.get(devIP), localOldDevStates.get(devIP)) >= 1) {
                oldDevStates.put(devIP, localOldDevStates.get(devIP));
            }
        }
    }

    // called by routine k-group leader
    public int setRtnTriggeredTS(String rtnID, int ts) {
        if (lastTriggered.getOrDefault(rtnID, -minTriggerOffset) + minTriggerOffset > ts) {
            // Network.log("in setRtnTriggeredTS, rtnId: " + rtnID + ", next trigger: " +
            // (lastTriggered.getOrDefault(rtnID, -minTriggerOffset) + minTriggerOffset),
            // true);
            return -1;
        }
        lastTriggered.put(rtnID, ts);
        // assign a sequence number to the new routine triggering, and return it later
        seqNos.put(rtnID, seqNos.get(rtnID) + 1);
        List<String> touchedDevsIDs = routines.get(rtnID).getTouchedDevIDs();
        // create a new RoutineStage instance for the new triggering
        getRtnState(rtnID).put(
                seqNos.get(rtnID), new RoutineStage(lockStrategy, touchedDevsIDs));
        return seqNos.get(rtnID);
    }

    // called by non-leader routine k-group member
    public int setRtnTriggered(String rtnID, int rtnSeqNo) {
        // update the routine's sequence number
        if (rtnSeqNo <= seqNos.get(rtnID))
            return -1;
        Network.log("in setRtnTriggered, rtnId: " + rtnID + "-" + rtnSeqNo);
        seqNos.put(rtnID, rtnSeqNo);
        List<String> touchedDevsIDs = routines.get(rtnID).getTouchedDevIDs();
        // create a new RoutineStage instance for the new triggering
        if (getRtnStage(rtnID, rtnSeqNo) == null) {
            getRtnState(rtnID).put(
                    rtnSeqNo, new RoutineStage(lockStrategy, touchedDevsIDs));
        }
        // log("Routine " + rtnID + ": triggered, seqNo: " + seqNos.get(rtnID));
        return seqNos.get(rtnID);
    }

    public String nextDevToLock(String rtnID, int rtnSeqNo) {
        List<String> touchedDevsIDs = routines.get(rtnID).getTouchedDevIDs();

        return getRtnState(rtnID).get(rtnSeqNo).nextDevToLock(touchedDevsIDs);
    }

    @Override
    /**** Starting utility functions for parallel locking strategy *****/
    public void acquiredDevLockForParallel(
            String rtnID, int rtnSeqNo, int requestSeqNo, String devID) {
        getRtnState(rtnID).get(rtnSeqNo).acquireDeviceLockForParallel(devID, requestSeqNo);
    }

    public boolean acquiredAllLocksForParallel(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).acquiredAllLocksForParallel();
    }

    public int getPreLockDevSeqNo(String rtnID, int rtnSeqNo, String devID) {
        return getAllPreLockDevSeqNo(rtnID, rtnSeqNo).get(devID);
    }

    public Map<String, Integer> getAllPreLockDevSeqNo(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).getAllPreLockDevSeqNo();
    }

    public List<String> getAllDevIdsLockAcquiredNotGranted(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).getDevIdsLockAcquiredNotGranted();
    }

    @Override
    public List<String> getAllUnreleasedDevIDs(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).getUnreleasedDevIDs();
    }

    public void removePreLockRecord(String rtnID, int rtnSeqNo, String devID) {
        getRtnState(rtnID).get(rtnSeqNo).resetSingleDevPreLock(devID);
    }

    public boolean arePreLocksEmpty(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).arePreLocksEmpty();
    }

    @Override
    public boolean acquiredDevLockForRtn(String rtnID, int rtnSeqNo, String devID) {
        boolean rv = getRtnState(rtnID).get(rtnSeqNo).acquiredDevLock(devID);
        String maxTouchedDevID = Collections.max(routines.get(rtnID).getTouchedDevIDs());
        if (lockStrategy == LockStrategy.SERIAL && rv && devID.equals(maxTouchedDevID)) {
            getRtnStage(rtnID, rtnSeqNo).acquiredAllDeviceLocks();
        }
        return rv;
    }

    public void lockAcquireFailedForRtn(String rtnID, int rtnSeqNo) {
        getRtnStage(rtnID, rtnSeqNo).lockAcquireFailed();
    }

    public void releasedDevLockForRtn(String rtnID, int rtnSeqNo, String devID) {
        getRtnStage(rtnID, rtnSeqNo).releasedLock(devID);
    }

    public boolean isLockAcquireFailed(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).isLockAcquireFailed();
    }

    public void startAcquiringLocksForRtn(String rtnID, int rtnSeqNo) {
        getRtnStage(rtnID, rtnSeqNo).startAcquiringLocks();
    }

    public void storeOldDevState(String devIP, DeviceState oldState) {
        oldDevStates.put(devIP, oldState);
    }

    public DeviceState getOldDevState(String devIP) {
        return oldDevStates.get(devIP);
    }

    public void startRtnExec(String rtnID, int rtnSeqNo, int endTS) {
        getRtnState(rtnID).get(rtnSeqNo).startExecution(endTS);
    }

    @Override
    public void setRtnExecuted(String rtnID, int rtnSeqNo) {
        getRtnStage(rtnID, rtnSeqNo).setExecuted();
    }

    public void startReleasingRtnLocks(String rtnID, int rtnSeqNo) {
        List<String> touchedDevsIDs = routines.get(rtnID).getTouchedDevIDs();
        getRtnState(rtnID).get(rtnSeqNo).startReleasingLocks(touchedDevsIDs);
    }

    @Override
    public boolean isRtnAcquiringLocks(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).isAcquiringLocks();
    }

    public boolean areRtnLocksAcquired(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).areLocksAcquired();
    }

    @Override
    public boolean isRtnExecuting(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).isExecuting();
    }

    public int getRtnEndTS(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).getEndTS();
    }

    public boolean isRtnReleasingLocks(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).isReleasingLocks();
    }

    public boolean areRtnLocksReleased(String rtnID, int rtnSeqNo) {
        return getRtnState(rtnID).get(rtnSeqNo).areLocksReleased();
    }

    @Override
    public void setState(Map<String, Object> state) {
        this.state.clear();
        for (String rtnID : state.keySet()) {
            this.state.put(rtnID, new HashMap<Integer, RoutineStage>());
            Map<Integer, RoutineStage> curRtnStates = getRtnState(rtnID);
            Map<Integer, RoutineStage> tgtRtnStates = getRtnState(state, rtnID);
            for (Integer rtnSeqNo : tgtRtnStates.keySet()) {
                curRtnStates.put(
                        rtnSeqNo, new RoutineStage(tgtRtnStates.get(rtnSeqNo), lockStrategy));
            }
        }
    }

    public void addLocalState(Map<String, Object> localState) {
        countReceivedLocalStates++;
        Map<Integer, RoutineStage> localRtnState, rtnState;
        for (String rtnID : entitiesIDs) {
            Network.log(localState.get(rtnID).getClass());
            localRtnState = getRtnState(localState, rtnID);
            rtnState = getRtnState(rtnID);
            for (Integer rtnSeqNo : localRtnState.keySet()) {
                if (rtnState.get(rtnSeqNo) == null) {
                    rtnState.put(rtnSeqNo, new RoutineStage());
                }
                getRtnStage(rtnID, rtnSeqNo)
                        .addLocalState(getRtnStage(localState, rtnID, rtnSeqNo), F);
            }
        }
    }
}
