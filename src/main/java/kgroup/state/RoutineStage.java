package kgroup.state;

import kgroup.LockStrategy;
import network.Network;

import java.util.*;

// Have a different RoutineStage instance for each routine triggering
public class RoutineStage extends KGroupState {
    private static final long serialVersionUID = 3882050344443438437L;

    private String latestLockedDevID;
    private RoutineStageType stage;
    private int endTS;
    private LockStrategy lockStrategy = LockStrategy.SERIAL;
    private final List<String> unreleasedDevIDs = new ArrayList<>();

    // Devices that has not gotten the pre-lock.
    // Used for parallel lock strategy only.
    private final List<String> touchedDevIDs = new ArrayList<>();
    private final List<String> pendingDevIDs = new ArrayList<>();
    private final Map<String, Integer> preLockDevReqNo = new HashMap<>();
    private final List<String> lockedDevIds = new ArrayList<>();

    private final List<String> latestLockedDevIDRecreation = new ArrayList<String>();
    private final List<RoutineStageType> stageRecreation = new ArrayList<RoutineStageType>();
    private final List<String> unreleasedDevIDsRecreation = new ArrayList<>();
    private final List<String> pendingDevIdsRecreation = new ArrayList<>();
    private final List<Map.Entry<String, Integer>> preLockDevReqNoRecreation = new ArrayList<>();
    private final List<String> lockedDevIdsRecreation = new ArrayList<>();

    public RoutineStage() {
        latestLockedDevID = null;
        stage = RoutineStageType.ACQUIRING_LOCKS;
        endTS = -1;
    }

    public RoutineStage(RoutineStage routineStage) {
        latestLockedDevID = routineStage.latestLockedDevID;
        stage = routineStage.stage;
        endTS = routineStage.endTS;
        touchedDevIDs.addAll(routineStage.touchedDevIDs);
        unreleasedDevIDs.addAll(routineStage.unreleasedDevIDs);
        pendingDevIDs.addAll(routineStage.pendingDevIDs);
        preLockDevReqNo.putAll(routineStage.preLockDevReqNo);
        lockedDevIds.addAll(routineStage.lockedDevIds);
    }

    public RoutineStage(LockStrategy ls, List<String> touchedDevs) {
        this();
        lockStrategy = ls;
        if (touchedDevs != null) {
            touchedDevIDs.addAll(touchedDevs);
            pendingDevIDs.addAll(touchedDevs);
        }
    }

    public RoutineStage(RoutineStage routineStage, LockStrategy ls) {
        this(routineStage);
        lockStrategy = ls;
    }

    public String nextDevToLock(List<String> touchedDevIDs) {
        if (touchedDevIDs.isEmpty())
            return null;
        Collections.sort(touchedDevIDs);
        Network.log(touchedDevIDs + ", last locked: " + latestLockedDevID);
        if (latestLockedDevID == null) {
            return touchedDevIDs.get(0);
        } else if (latestLockedDevID == Collections.max(touchedDevIDs)) {
            stage = RoutineStageType.ACQUIRED_LOCKS;
            return null;
        } else {
            for (String devID : touchedDevIDs) {
                if (devID.compareTo(latestLockedDevID) > 0) {
                    return devID;
                }
            }
        }
        return null;
    }

    public void acquireDeviceLockForParallel(String devID, int requestSeqNo) {
        preLockDevReqNo.put(devID, requestSeqNo);
        pendingDevIDs.remove(devID);
    }

    public void resetAllDevPreLocks(List<String> ids) {
        pendingDevIDs.clear();
        pendingDevIDs.addAll(ids);
        preLockDevReqNo.clear();
        lockedDevIds.clear();
    }

    public void resetSingleDevPreLock(String dev) {
        if (!pendingDevIDs.contains(dev)) {
            pendingDevIDs.add(dev);
        }
        preLockDevReqNo.remove(dev);
    }

    public boolean arePreLocksEmpty() {
        return preLockDevReqNo.isEmpty();
    }

    public boolean acquiredAllLocksForParallel() {
        return pendingDevIDs.size() == 0;
    }

    public boolean acquiredDevLock(String devID) {
        // For PARALLEL locking
        if (lockStrategy.equals(LockStrategy.PARALLEL)) {
            if (!lockedDevIds.contains(devID)) {
                lockedDevIds.add(devID);
            }
            if (pendingDevIDs.isEmpty() && lockedDevIds.size() == touchedDevIDs.size()) {
                acquiredAllDeviceLocks();
            }
            return true;
        }
        // For SERIAL locking
        if (latestLockedDevID == null || devID.compareTo(latestLockedDevID) > 0) {
            latestLockedDevID = devID;
            return true;
        }
        return false;
    }

    public void acquiredAllDeviceLocks() {
        stage = RoutineStageType.ACQUIRED_LOCKS;
    }

    public void lockAcquireFailed() {
        stage = RoutineStageType.AC_LOCK_FAILED;
    }

    public void startAcquiringLocks() {
        stage = RoutineStageType.ACQUIRING_LOCKS;
    }

    public void startExecution(int endTS) {
        stage = RoutineStageType.EXECUTING;
        this.endTS = endTS;
    }

    public void setExecuted() {
        stage = RoutineStageType.RELEASING_LOCKS;
    }

    public void startReleasingLocks(List<String> touchedDevIDs) {
        stage = RoutineStageType.RELEASING_LOCKS;
        unreleasedDevIDs.clear();
        unreleasedDevIDs.addAll(touchedDevIDs);
    }

    public void releasedLock(String devID) {
        unreleasedDevIDs.remove(devID);
    }

    public List<String> getUnreleasedDevIDs() {
        return unreleasedDevIDs;
    }

    public boolean isAcquiringLocks() {
        return stage == RoutineStageType.ACQUIRING_LOCKS;
    }

    public String getLatestLockedDevID() {
        return latestLockedDevID;
    }

    public boolean areLocksAcquired() {
        return stage == RoutineStageType.ACQUIRED_LOCKS;
    }

    public boolean isExecuting() {
        return stage == RoutineStageType.EXECUTING;
    }

    public int getEndTS() {
        return endTS;
    }

    public boolean isReleasingLocks() {
        return stage == RoutineStageType.RELEASING_LOCKS;
    }

    public boolean areLocksReleased() {
        return stage == RoutineStageType.RELEASING_LOCKS && unreleasedDevIDs.isEmpty();
    }

    public void addLocalState(RoutineStage localState, int f) {
        if (latestLockedDevID != localState.latestLockedDevID) {
            latestLockedDevIDRecreation.add(localState.latestLockedDevID);
            if (Collections.frequency(latestLockedDevIDRecreation, localState.latestLockedDevID) >= 1) {
                latestLockedDevID = localState.latestLockedDevID;
            }
        }
        if (stage != localState.stage) {
            stageRecreation.add(localState.stage);
            if (Collections.frequency(stageRecreation, localState.stage) >= 1) {
                stage = localState.stage;
            }
        }
        if (touchedDevIDs.size() == 0 && localState.touchedDevIDs.size() > 0) {
            touchedDevIDs.addAll(localState.touchedDevIDs);
        }
        if (pendingDevIDs != localState.pendingDevIDs) {
            pendingDevIdsRecreation.addAll(localState.pendingDevIDs);
            for (String dev_id : localState.pendingDevIDs) {
                if (pendingDevIDs.contains(dev_id)) {
                    continue;
                }
                if (Collections.frequency(pendingDevIdsRecreation, dev_id) >= 1) {
                    pendingDevIDs.add(dev_id);
                }
            }
        }
        if (preLockDevReqNo != localState.preLockDevReqNo) {
            preLockDevReqNoRecreation.addAll(localState.preLockDevReqNoRecreation);
            for (Map.Entry<String, Integer> entry : localState.preLockDevReqNo.entrySet()) {
                if (preLockDevReqNo.containsKey(entry.getKey())) {
                    continue;
                }
                if (Collections.frequency(preLockDevReqNoRecreation, entry) >= 1) {
                    preLockDevReqNo.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (lockedDevIds != localState.lockedDevIds) {
            lockedDevIdsRecreation.addAll(localState.lockedDevIds);
            for (String dev_id : localState.lockedDevIds) {
                if (lockedDevIds.contains(dev_id)) {
                    continue;
                }
                if (Collections.frequency(lockedDevIdsRecreation, dev_id) >= 1) {
                    lockedDevIds.add(dev_id);
                }
            }
        }
        if (unreleasedDevIDs != localState.unreleasedDevIDs) {
            unreleasedDevIDsRecreation.addAll(localState.unreleasedDevIDs);
            for (String dev_id : localState.unreleasedDevIDs) {
                if (unreleasedDevIDs.contains(dev_id)) {
                    continue;
                }
                if (Collections.frequency(lockedDevIdsRecreation, dev_id) >= 1) {
                    unreleasedDevIDs.add(dev_id);
                }
            }
        }
    }

    public String toString() {
        String lock_info;
        if (lockStrategy.equals(LockStrategy.SERIAL)) {
            lock_info = "latest locked device ID: " + latestLockedDevID
                    + ", unreleased devices: " + unreleasedDevIDs;
        } else {
            lock_info = "pre-locked: " + pendingDevIDs +
                    ", locked-queue: " + preLockDevReqNo.keySet() +
                    ", lock-confirmed: " + lockedDevIds;
        }
        return "Stage: " + stage + ", " + lock_info;
    }

    public List<String> getPendingDevIDs() {
        return pendingDevIDs;
    }

    public Map<String, Integer> getAllPreLockDevSeqNo() {
        return preLockDevReqNo;
    }

    public List<String> getDevIdsLockAcquiredNotGranted() {
        List<String> res = new ArrayList<>(touchedDevIDs);
        res.removeAll(lockedDevIds);
        return res;
    }

    public boolean isLockAcquireFailed() {
        return stage.equals(RoutineStageType.AC_LOCK_FAILED);
    }
}
