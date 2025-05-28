package kgroup.state;

import java.util.*;
import java.util.Map.Entry;

import network.Network;

public class DeviceLock extends KGroupState {
    private static final long serialVersionUID = 9018683307565273560L;
    
    private int reqSeqNo;
    // requests that arrive at a k-group leader
    // go here before they are approved to be put at the actual queue
    private SortedMap<Integer, LockRequest> newLockRequests;
    private SortedMap<Integer, LockRequest> queue;
    private boolean locked;
    private int lastLockedReqSeqNo;

    private List<Integer> reqSeqNoRecreation;
    private SortedMap<Integer, List<LockRequest>> queueRecreation;
    private int lockedRecreation;

    public DeviceLock() {
        reqSeqNo = -1;
        newLockRequests = new TreeMap<Integer, LockRequest>();
        queue = new TreeMap<Integer, LockRequest>();
        locked = false;
        lastLockedReqSeqNo = -1;

        reqSeqNoRecreation = new ArrayList<Integer>();
        queueRecreation = new TreeMap<Integer, List<LockRequest>>();
        lockedRecreation = 0;
    }

    public DeviceLock(DeviceLock deviceLock) {
        reqSeqNo = deviceLock.reqSeqNo;
        lastLockedReqSeqNo = deviceLock.lastLockedReqSeqNo;
        newLockRequests = new TreeMap<Integer, LockRequest>(deviceLock.newLockRequests);
        queue = new TreeMap<Integer, LockRequest>(deviceLock.queue);
        locked = deviceLock.locked;

        reqSeqNoRecreation = new ArrayList<Integer>();
        queueRecreation = new TreeMap<Integer, List<LockRequest>>();
        lockedRecreation = 0;
    }

    // called by leader
    public boolean isLockRequestReceived(String rtnID, int rtnSeqNo) {
        for (LockRequest rq: newLockRequests.values()) {
            if (rq.contains(rtnID, rtnSeqNo)) {
                return true;
            }
        }
        for (LockRequest rq: queue.values()) {
            if (rq.contains(rtnID, rtnSeqNo)) {
                return true;
            }
        }
        return false;
    }

    // called by leader
    public boolean isLockRequestGranted(String rtnID, int rtnSeqNo) {
        for (LockRequest rq: queue.values()) {
            if (rq.contains(rtnID, rtnSeqNo)) {
                return true;
            }
        }
        return false;
    }

    // called by leader
    public boolean isAcquired(String rtnID, int rtnSeqNo) {
        if (isQueueEmpty()) {
            return false;
        }
        if (getLocker() != null && getLocker().contains(rtnID, rtnSeqNo)) {
            return locked;
        }
        return false;
    }

    // called by leader
    public boolean isAvailable() {
        return (!locked) & isQueueEmpty() & isNewLockRequestsEmpty();
    }

    // called by leader
    public boolean isLocked() {
        if (isQueueEmpty()) {
            locked = false;
        }
        return locked;
    }

    // called by leader
    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    // called by leader
    public boolean isNewLockRequestsEmpty() {
        return newLockRequests.isEmpty();
    }

    // called by leader
    public int getQueueHeadReqSeqNo() {
        return queue.firstKey();
    }

    // called by leader
    public LockRequest getQueueHead() {
        if (!isQueueEmpty()) {
            return queue.get(queue.firstKey());
        }
        else {
            return null;
        }
    }

    // called by centralized manager
    public int getQueueTailReqSeqNo() {
        return queue.lastKey();
    }

    public int getQueueSize() {
        return queue.size();
    }

    // called by leader
    public Map<Integer, LockRequest> getNewLockRequests() {
        return newLockRequests;
    }

    // called by leader
    public Map<Integer, LockRequest> getNewPLockRequest() {
        if (newLockRequests.size() > 1) {
            Network.log("[ERROR] Having multiple new Plock Requests during state transfer!", true);
            Network.log("    newRequest Queue" + newLockRequests, true);
            System.exit(-1);
        }
        return newLockRequests;
    }

    // called by leader
    public int getLockerReqSeqNo() {
        if (!isQueueEmpty() && locked) {
            return queue.firstKey();
        }
        return -1;
    }

    // called by leader
    public LockRequest getLocker() {
        if (isQueueEmpty()) {
            locked = false;
        }
        if (locked) {
            return queue.get(queue.firstKey());
        }
        return null;
    }

    // called by leader
    public int addNewRequest(String rtnID, int rtnSeqNo) {
        for (LockRequest rq: newLockRequests.values()) {
            if (rq.contains(rtnID, rtnSeqNo)) {
                return -1;
            }
        }
        reqSeqNo ++; // sequence number for queue recreation on leader failure
        newLockRequests.put(reqSeqNo, new LockRequest(rtnID, rtnSeqNo));
        return reqSeqNo;
    }

    // called by non-leader
    public void replicateNewRequest(String rtnID, int rtnSeqNo, int requestSeqNo) {
        newLockRequests.put(requestSeqNo, new LockRequest(rtnID, rtnSeqNo));
        // FIXME: Should we also set reqSeqNo for k-group member (non-leader)? Similar Q for replicateRequest()
    }

    public void removeRequest(int reqSeqNo, String rtnId, int rtnSeqNo) {
        if (!newLockRequests.containsKey(reqSeqNo) && !queue.containsKey(reqSeqNo)) {
            Network.log("[ERROR] #PLRMNR Removing non-existing request with reqSeqNo " +
                    reqSeqNo + " for Routine " + rtnId + '-' + rtnSeqNo);
        }
        newLockRequests.remove(reqSeqNo);
        queue.remove(reqSeqNo);
    }

    // called by leader
    public int getLockRequestSeqNo(String rtnID, int rtnSeqNo) {
        for (Map.Entry<Integer, LockRequest> e: newLockRequests.entrySet()) {
            if (e.getValue().contains(rtnID, rtnSeqNo)) {
                return e.getKey();
            }
        }
        for (Map.Entry<Integer, LockRequest> e: queue.entrySet()) {
            if (e.getValue().contains(rtnID, rtnSeqNo)) {
                return e.getKey();
            }
        }
        return -1;
    }

    public int getLastLockedReqSeqNo() {
        return lastLockedReqSeqNo;
    }

    // called by centralized manager or internally by leader/non-leader alike
    // to move req to queue from new req map
    public int addReqToQueue(LockRequest rq) {
        reqSeqNo ++;
        queue.put(reqSeqNo, new LockRequest(rq));
        return reqSeqNo;
    }

    // this is called by a non-leader k-group member
    // upon request reception from the leader
    public void replicateRequest(LockRequest rq, int reqSeqNo) {
        queue.put(reqSeqNo, new LockRequest(rq));

        // check the newLockRequests
        newLockRequests.remove(reqSeqNo);
    }

    // called by leader as soon as the request has been replicated by the rest of the k-group members
    public void requestReplicated(LockRequest rq, int reqSeqNo) {
        // log("replicated request " + rq + "!!!!!!!!!!");
        replicateRequest(rq, reqSeqNo);
    }

    // called by leader
    public boolean lock(LockRequest rq, int reqSeqNo) {
        if (getQueueHead().equals(rq) && getQueueHeadReqSeqNo() == reqSeqNo && reqSeqNo == lastLockedReqSeqNo + 1) {
            locked = true;
            lastLockedReqSeqNo ++;
        }
        return locked;
    }

    // called by leader. Used for parallel locking
    public boolean plock(String rtnID, int rtnSeqNo, int requestSeqNo) {
        locked = true;
        lastLockedReqSeqNo++;
        return locked;
    }

    // called by non-leader
    public void replicateLock(LockRequest rq, int reqSeqNo) {
        locked = true;
        // lock queue repair
        while (!queue.isEmpty() && queue.firstKey() < reqSeqNo) {
            queue.remove(queue.firstKey());
        }
        queue.put(reqSeqNo, new LockRequest(rq));
    }

    // called by leader
    // returns next routine's ID/seqNo to be locked
    public LockRequest release(LockRequest rq, int reqSeqNo) {
        // unlock device and delete last granted request
        if (locked && getLocker().equals(rq) && getLockerReqSeqNo() == reqSeqNo) {
            locked = false;
        }
        queue.remove(reqSeqNo);

        // return next request to be granted
        if (!queue.isEmpty()) {
            return queue.get(queue.firstKey());
        }
        return null;
    }

    // called by centralized manager
    // returns next routine's ID/seqNo to be locked
    public LockRequest release(LockRequest rq) {
        // unlock device and delete last granted request
        if (locked && getLocker().equals(rq)) {
            locked = false;
        }
        queue.remove(reqSeqNo);

        // return next request to be granted
        if (!queue.isEmpty()) {
            return queue.get(queue.firstKey());
        }
        return null;
    }

    // called by non-leader
    public void replicateRelease(LockRequest rq, int reqSeqNo) {
        if (locked && getLocker().equals(rq) && getLockerReqSeqNo() == reqSeqNo) {
            locked = false;
        }
        queue.remove(reqSeqNo);
        
        // lock queue repair
        while (!queue.isEmpty() && queue.firstKey() < reqSeqNo) {
            queue.remove(queue.firstKey());
        }
    }

    // called by leader
    public void addLocalState(DeviceLock localState, int f) {
        for (Entry<Integer, LockRequest> e: localState.queue.entrySet()) {
            if (!queueRecreation.containsKey(e.getKey())) {
                queueRecreation.put(e.getKey(), new ArrayList<LockRequest>());
            }
            LockRequest rq = e.getValue();
            queueRecreation.get(e.getKey()).add(rq);
            int freq = 0;
            for (LockRequest rqi: queueRecreation.get(e.getKey())) {
                if (rqi.equals(rq)) {
                    freq ++;
                }
            }
            if (freq >= 1) {
                queue.putIfAbsent(e.getKey(), new LockRequest(rq)); // check!!!!!!
            }
        }

        if (localState.locked) {
            lockedRecreation ++;
            if (lockedRecreation >= 1) {
                locked = true;
            }
        }

        reqSeqNoRecreation.add(localState.reqSeqNo);
        int freq = 0;
        for (Integer reqSeqNoi: reqSeqNoRecreation) {
            if (localState.reqSeqNo == reqSeqNoi) {
                freq ++;
            }
            if (freq >= 1) {
                reqSeqNo = reqSeqNoi;
            }
        }
    }

    public String toString() {
        String str = (locked? "L": "Not l") + "ocked";
        str += ", Current queue: " + queue;
        str += ", New requests: " + newLockRequests;

        return str;
    }
}
