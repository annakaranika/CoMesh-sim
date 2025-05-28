package kgroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import kgroup.state.DeviceLock;
import kgroup.state.LockRequest;
import network.Network;
import routine.Routine;

public class DeviceKGroup extends KGroup {
    private final Map<String, List<String>> rtnsToNotify = new HashMap<>();
    
    public DeviceKGroup(
        String nodeID, List<String> devIDs,  Map<String, Membership> membershipList, int F, int K,
        LeaderElectionPolicy lep, LockStrategy ls, KGroupSelectionPolicy kGroupPolicy,
        Network network, Map<String, Routine> rtns
    ) {
        super(nodeID, devIDs, membershipList, KGroupType.DEVICE, F, K, lep, ls, kGroupPolicy, network);
        for (String devID: devIDs) {
            state.put(devID, new DeviceLock());
        }
        for (Entry<String, Routine> rtn: rtns.entrySet()) {
            String rtnID = rtn.getKey();
            List<String> trigDevIDs = rtn.getValue().getTriggerDevIDs();
            for (String devID: trigDevIDs) {
                if (!devIDs.contains(devID)) continue;
                if (!rtnsToNotify.containsKey(devID)) {
                    rtnsToNotify.put(devID, new ArrayList<>());
                }
                if (!rtnsToNotify.get(devID).contains(rtnID))
                    rtnsToNotify.get(devID).add(rtnID);
            }
        }
    }

    private DeviceLock getDevLock(Map<String, Object> state, String devID) {
        return (DeviceLock) state.get(devID);
    }

    public DeviceLock getDevLock(String devID) {
        return (DeviceLock) state.get(devID);
    }

    @Override
    public List<String> getRtnsToNotify(String devIP) {
        return rtnsToNotify.getOrDefault(devIP, new ArrayList<>());
    }

    // called by leader, or also by non-leader in parallel locking
    public boolean isLockRequestReceived(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).isLockRequestReceived(rtnID, rtnSeqNo);
    }

    // called by leader
    public boolean isLockAvailable(String devID) {
        return getDevLock(devID).isAvailable();
    }

    // called by leader
    public boolean isLocked(String devID) {
        return getDevLock(devID).isLocked();
    }

    // called by leader
    public int getLockQueueHeadReqSeqNo(String devID) {
        return getDevLock(devID).getQueueHeadReqSeqNo();
    }

    // called by leader
    @Override
    public LockRequest getLockQueueHead(String devID) {
        return getDevLock(devID).getQueueHead();
    }

    public int getLockQueueSize(String devID) {
        return getDevLock(devID).getQueueSize();
    }

    // called by leader
    public int getLockerReqSeqNo(String devID) {
        return getDevLock(devID).getLockerReqSeqNo();
    }

    // call by leader
    public int getReqSeqNo(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).getLockRequestSeqNo(rtnID, rtnSeqNo);
    }

    // called by leader
    public Map<Integer, LockRequest> getNewLockRequests(String devID) {
        return getDevLock(devID).getNewLockRequests();
    }

    // call by leader; for PARALLEL Locking
    public Map<Integer, LockRequest> getNewPLockRequest(String devID) {
        return getDevLock(devID).getNewPLockRequest();
    }

    // Util function for both leader/non-leader
    public Map<String, Map<Integer, LockRequest>> getAllNewLockRequests() {
        Map<String, Map<Integer, LockRequest>> res = new HashMap<>();
        for (String devID: state.keySet()) {
            res.put(devID, getNewLockRequests(devID));
        }
        return res;
    }

    // called by leader
    public LockRequest getLocker(String devID) {
        return getDevLock(devID).getLocker();
    }

    public boolean isLocker(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).getLocker().contains(rtnID, rtnSeqNo);
    }

    // called by leader
    public boolean isLockAcquired(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).isAcquired(rtnID, rtnSeqNo);
    }

    // called by leader
    public boolean isLockQueueEmpty(String devID) {
        return getDevLock(devID).isQueueEmpty();
    }

    // called by leader
    public boolean isNewLockRequestsEmpty(String devID) {
        return getDevLock(devID).isNewLockRequestsEmpty();
    }

    // called by leader
    public boolean isLockRequestGranted(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).isLockRequestGranted(rtnID, rtnSeqNo);
    }

    // called by leader or non-leader upon receiving plock request
    public int addNewLockRequest(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).addNewRequest(rtnID, rtnSeqNo);
    }

    // called by non-leader
    public void replicateNewLockRequest(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        getDevLock(devID).replicateNewRequest(rtnID, rtnSeqNo, reqSeqNo);
    }

    // called by leader or non-leader upon receiving plock cancel
    public void removeLockRequest(
        String devID, String rtnID, int rtnSeqNo, int reqSeqNo
    ) {
        getDevLock(devID).removeRequest(reqSeqNo, rtnID, rtnSeqNo);
    }

    // called by leader
    public int getLockRequestSeqNo(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).getLockRequestSeqNo(rtnID, rtnSeqNo);
    }

    // called by leader
    public int getLastLockedReqSeqNo(String devID) {
        return getDevLock(devID).getLastLockedReqSeqNo();
    }

    public int addReqToQueue(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).addReqToQueue(new LockRequest(rtnID, rtnSeqNo));
    }

    // called by a non-leader upon request reception from the leader
    public void replicateLockRequest(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        getDevLock(devID).replicateRequest(new LockRequest(rtnID, rtnSeqNo), reqSeqNo);
    }

    // called by leader
    public void lockRequestReplicated(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        getDevLock(devID).requestReplicated(new LockRequest(rtnID, rtnSeqNo), reqSeqNo);
    }

    // called by non-leader
    public void replicateLockAcquisition(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        getDevLock(devID).replicateLock(new LockRequest(rtnID, rtnSeqNo), reqSeqNo);
    }

    // called by leader
    public void lock(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        if (lockStrategy.equals(LockStrategy.SERIAL)) {
            getDevLock(devID).lock(new LockRequest(rtnID, rtnSeqNo), reqSeqNo);
        } else {
            getDevLock(devID).plock(rtnID, rtnSeqNo, reqSeqNo);
        }
    }

    // called by non-leader
    public void replicateLockRelease(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        getDevLock(devID).replicateRelease(new LockRequest(rtnID, rtnSeqNo), reqSeqNo);
    }

    // called by leader
    public LockRequest releaseLock(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        return getDevLock(devID).release(new LockRequest(rtnID, rtnSeqNo), reqSeqNo);
    }

    // called by centralized manager
    public LockRequest releaseLock(String devID, String rtnID, int rtnSeqNo) {
        return getDevLock(devID).release(new LockRequest(rtnID, rtnSeqNo));
    }

    @Override
    public void setState(Map<String, Object> state) {
        this.state.clear();
        for (String devID: state.keySet()) {
            this.state.put(devID, new DeviceLock(getDevLock(state, devID)));
        }
    }

    public void addLocalState(Map<String, Object> localState) {
        countReceivedLocalStates ++;
        for (String devID: entitiesIDs) {
            getDevLock(devID).addLocalState(getDevLock(localState, devID), F);
        }
    }
}
