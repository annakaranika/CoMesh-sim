package kgroup;

import java.io.FileWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import kgroup.state.*;
import network.Network;
import network.message.Message;
import network.message.payload.MessagePayload;
import routine.DeviceState;

public class KGroup {
    // entitiesIDs refers to the ID(s) of the entities (device(s) or routine(s))
    // this k-group is responsible for
    // membershipList the IDs of all the nodes in the network with an Online/Offline
    // status
    // oldMemberIDs and curMemberIDs refer to the IDs of the nodes that are members
    // of the old and current k-group respectively
    // failedNodesIDs consists of the IDs of nodes that have been detected as failed
    // type refers to whether the k-group is in charge of device(s) or routine(s)
    // K is the minimum number of nodes that are members of the k-group
    // N is the number of all nodes in the network

    public List<String> entitiesIDs, oldMemberIDs, curMemberIDs, failedNodesIDs, oldLeaderCandidates;
    protected String curKGroupLeaderNodeID, oldKGroupLeaderNodeID;
    public KGroupType type;
    protected List<Message> waitingMessageQueue;
    protected final Map<String, Object> state = new HashMap<String, Object>();
    protected int F, K, N, countReceivedLocalStates, countFailedNodes, epochNo;
    public final int kgrpID;
    protected boolean receivingEndOpen;
    protected LeaderElectionPolicy electionPolicy;
    protected LockStrategy lockStrategy;
    public LeaderElectionStage ldrElctnStg;
    public StateTransferStage stateTrnsfrStg;
    public FileWriter outputWriter;
    protected static final Map<String, Integer> ids = new HashMap<>();

    public KGroupSelectionPolicy kGroupPolicy;
    public Network network;

    public KGroup(
            String nodeID, List<String> entitiesIDs, Map<String, Membership> membershipList,
            KGroupType type, int F, int K, LeaderElectionPolicy lep, LockStrategy ls,
            KGroupSelectionPolicy kGroupPolicy, Network network) {
        this.entitiesIDs = new ArrayList<String>(entitiesIDs);
        N = membershipList.size();
        this.type = type;
        this.F = F;
        if (K != 2 * F + 1) {
            Network.log("K cannot be different from 2 * F + 1!", true);
            System.exit(-1);
        }
        this.K = K;
        epochNo = 0;

        oldMemberIDs = null;
        oldLeaderCandidates = null;
        oldKGroupLeaderNodeID = null;
        curMemberIDs = new CopyOnWriteArrayList<String>();
        curKGroupLeaderNodeID = null;
        failedNodesIDs = new CopyOnWriteArrayList<String>();
        receivingEndOpen = false;
        electionPolicy = lep;
        ldrElctnStg = LeaderElectionStage.NOT_STARTED;
        stateTrnsfrStg = StateTransferStage.NOT_STARTED;
        countReceivedLocalStates = 0;
        waitingMessageQueue = new ArrayList<Message>();
        countFailedNodes = 0;

        if (!ids.containsKey(nodeID))
            ids.put(nodeID, -1);
        ids.put(nodeID, ids.get(nodeID) + 1);
        kgrpID = ids.get(nodeID);

        lockStrategy = ls;
        this.kGroupPolicy = kGroupPolicy;
        this.network = network;
    }

    public static void clearIDs() {
        ids.clear();
    }

    public void log(Object text, boolean debug) {
        text = this + " - " + text;
        Network.log(text, debug);
    }

    public void log(Object text) {
        log(text, false);
    }

    public String hash(String nodeID, int epochNo) {
        String value = nodeID + kgrpID + type.toString() + entitiesIDs.toString()
                + String.valueOf(epochNo);
        String sha1 = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            digest.update(value.getBytes("utf8"));
            sha1 = String.format("%040x", new BigInteger(1, digest.digest()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sha1;
    }

    // returns the nodes that should be added to the k-group based on the
    // recruitement strategy
    public List<String> newKGroupMembers(
            Map<String, Membership> membershipList, KMemberMix kMemberMix,
            int desired, int epochNo, String type) {
        // if this is a replacement of failures, choose randomly
        if (kGroupPolicy.equals(KGroupSelectionPolicy.RANDOM) || desired < K) {
            return newKGroupMembersRandom(membershipList, desired, epochNo);
        } else {
            // use LSH for new epoch
            if (type.equals("previous")) {
                return new ArrayList<>(
                        kMemberMix.getAllCenters().get(kgrpID).getPreviousKGroup());
            }
            return newKGroupMembersLSHMix(membershipList, kMemberMix, K);
        }
    }

    // return the K group of CentersForDeviceKGroup.get(kgrpID) if kgrpID <
    // getCentersForDeviceKGroup().size();
    // otherwise kgrpID = kgrpID - CentersForDeviceKGroup.size() and return
    // accordingly

    public List<String> newKGroupMembersLSHMix(
            Map<String, Membership> membershipList, KMemberMix kMemberMix, int desired) {
        // return a list of KGroup members: first F+1 are based on LSH and the last F
        // from random selection
        // notice that first F+1 may also have random selected nodes if not enough from
        // LSH
        List<String> temp = new ArrayList<>(kMemberMix.getAllCenters().get(kgrpID).getOrderedKGroup());
        return temp;
    }

    // returns the nodes that should be added to the k-group based on the
    // recruitement strategy
    public List<String> newKGroupMembersRandom(
            Map<String, Membership> membershipList, int desired, int epochNo) {
        SortedMap<String, String> hashes = new TreeMap<>();
        for (String nodeID : membershipList.keySet()) {
            if (membershipList.get(nodeID) == Membership.ONLINE
                    && (desired >= K || (curMemberIDs != null && !curMemberIDs.contains(nodeID)))) {
                hashes.put(hash(nodeID, epochNo), nodeID);
            }
        }
        if (desired > hashes.size()) {
            log("\nNode hashes collide!!!!!!!\n");
        }
        int c = 0;
        List<String> newMembers = new ArrayList<>();
        for (String nodeID : hashes.values()) {
            if (c < desired) {
                newMembers.add(nodeID);
                c++;
            } else {
                break;
            }
        }
        return newMembers;
    }

    public void updateKGroup(
            Map<String, Membership> membershipList, KMemberMix kMemberMix, int epochNo) {
        if (curMemberIDs.isEmpty()) {
            oldMemberIDs = new ArrayList<>(newKGroupMembers(
                    membershipList, kMemberMix, K, epochNo - 1, "previous"));
            oldLeaderCandidates = new ArrayList<>(newKGroupMembers(
                    membershipList, kMemberMix, K, epochNo - 1, "previous"));
        } else {
            oldMemberIDs = new ArrayList<>(curMemberIDs);
            oldLeaderCandidates = new ArrayList<>(curMemberIDs);
        }
        oldKGroupLeaderNodeID = curKGroupLeaderNodeID;
        curMemberIDs = new CopyOnWriteArrayList<>(
                newKGroupMembers(membershipList, kMemberMix, K, epochNo, "current"));
        curKGroupLeaderNodeID = null;
        this.epochNo = epochNo;
        ldrElctnStg = LeaderElectionStage.NOT_STARTED;
        stateTrnsfrStg = StateTransferStage.NOT_STARTED;
    }

    public List<String> getDifferentNodes() {
        List<String> result = new ArrayList<>();
        for (String nodeID : curMemberIDs) {
            if (!oldMemberIDs.contains(nodeID)) {
                result.add(nodeID);
            }
        }

        return result;
    }

    public boolean updateFailedNodes(String failedNodeID) {
        if (curMemberIDs.contains(failedNodeID)) {
            curMemberIDs.remove(failedNodeID);
            countFailedNodes++;
            if (curKGroupLeaderNodeID == null) {
                return true;
            }
            if (curKGroupLeaderNodeID.equals(failedNodeID)) {
                oldKGroupLeaderNodeID = failedNodeID;
                curKGroupLeaderNodeID = null;
                ldrElctnStg = LeaderElectionStage.NOT_STARTED;
                return true;
            }
        }
        return false;
    }

    public int getCountFailedNodes() {
        return countFailedNodes;
    }

    public List<String> replaceFailedNodes(Map<String, Membership> membershipList, int epochNo) {
        failedNodesIDs.clear();
        // parameter cnt = -1 is not used
        return newKGroupMembers(membershipList, null, F, epochNo, "current");
    }

    public void nodeRecruited(String newMemberNodeID) {
        curMemberIDs.add(newMemberNodeID);
    }

    public void resetCountFailedNodes() {
        countFailedNodes = 0;
    }

    public String chooseLeader(boolean oldKGroup, int epochNo) {
        List<String> nodesIDs = oldKGroup ? oldLeaderCandidates : curMemberIDs;
        int epoch = epochNo - (oldKGroup ? 1 : 0);
        switch (electionPolicy) {
            case SMALLEST_HASH:
                SortedMap<String, String> hashes = new TreeMap<>();
                for (String nodeID : nodesIDs) {
                    hashes.put(hash(nodeID, epoch), nodeID);
                }
                return hashes.get(hashes.firstKey());
            case LSH_SMALLEST_HASH:
                hashes = new TreeMap<>();
                for (String nodeID : nodesIDs.subList(0, Math.min(F + 1, nodesIDs.size()))) {
                    hashes.put(hash(nodeID, epoch), nodeID);
                }
                return hashes.get(hashes.firstKey());
            case SMALLEST_ID:
                return Collections.min(nodesIDs);
            case CENTRAL_NODE:
            default:
                return null;
        }
    }

    // returns true if node1 is more appropriate for leader than node2
    public boolean moreAppr(String nodeID1, String nodeID2, int epochNo) {
        switch (electionPolicy) {
            case SMALLEST_HASH:
                return hash(nodeID1, epochNo).compareTo(hash(nodeID2, epochNo)) < 0;
            case SMALLEST_ID:
                return nodeID1.compareTo(nodeID2) < 0;
            case LSH_SMALLEST_HASH:
                int pos1 = curMemberIDs.indexOf(nodeID1);
                int pos2 = curMemberIDs.indexOf(nodeID2);
                if (pos1 <= F & pos2 > F)
                    return true;
                else if (pos2 <= F & pos1 > F)
                    return false;
                else
                    return hash(nodeID1, epochNo)
                            .compareTo(hash(nodeID2, epochNo)) < 0;
            case CENTRAL_NODE:
            default:
                return false;
        }
    }

    public void updateLeader(String newLeaderNodeID) {
        curKGroupLeaderNodeID = newLeaderNodeID;
    }

    public void updateOldLeader(String oldLeaderNodeID) {
        oldKGroupLeaderNodeID = oldLeaderNodeID;
    }

    public String getLeader() {
        return curKGroupLeaderNodeID;
    }

    public boolean isLeader(String nodeID) {
        if (isLeaderNull() || nodeID == null)
            return false;
        return getLeader().equals(nodeID);
    }

    public boolean isLeaderNull() {
        return getLeader() == null;
    }

    public String getOldLeader() {
        return oldKGroupLeaderNodeID;
    }

    public boolean isOldLeader(String nodeID) {
        if (isOldLeaderNull() || nodeID == null)
            return false;
        return getOldLeader().equals(nodeID);
    }

    public boolean isOldLeaderNull() {
        return getOldLeader() == null;
    }

    public void removeOldLdrCnddt(String nodeIP) {
        oldLeaderCandidates.remove(nodeIP);
    }

    public List<String> getOldLdrCnddts() {
        return oldLeaderCandidates;
    }

    public boolean noOldLdrCnddts() {
        return oldLeaderCandidates.isEmpty();
    }

    public String getMostProbableLeader(boolean oldKGroup) {
        if (oldKGroup) {
            if (oldKGroupLeaderNodeID != null) {
                return oldKGroupLeaderNodeID;
            }
            return chooseLeader(true, epochNo);
        } else {
            if (curKGroupLeaderNodeID != null) {
                return curKGroupLeaderNodeID;
            }
            return chooseLeader(false, epochNo);
        }
    }

    public int ascNodeOrder(String nodeID) {
        Collections.sort(curMemberIDs);
        int order = 0;
        for (String member : curMemberIDs) {
            if (member.equals(nodeID)) {
                return order;
            }
            order++;
        }
        return -1;
    }

    public List<String> nodesBefore(String nodeID) {
        Collections.sort(curMemberIDs);
        List<String> lowerNodesIDs = new ArrayList<>();
        for (String curNodeID : curMemberIDs) {
            if (curNodeID.compareTo(nodeID) >= 0) {
                break;
            }
            lowerNodesIDs.add(curNodeID);
        }
        return lowerNodesIDs;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(Map<String, Object> state) {
        this.state.clear();
        this.state.putAll(state);
    }

    // called by new leader
    public void addLocalState(Map<String, Object> localState) {
    }

    public void addLocalSeqNos(Map<String, Integer> localSeqNos) {
    }

    public void addLocalLastTriggered(Map<String, Integer> localLastTriggered) {
    }

    public int getCountReceivedLocalStates() {
        return countReceivedLocalStates;
    }

    public DeviceState getOldDevState(String devIP) {
        return null;
    }

    public Map<String, DeviceState> getOldDevStates() {
        return null;
    }

    public void setOldDevStates(Map<String, DeviceState> oldDevStates) {
    }

    public void addLocalOldDevStates(Map<String, DeviceState> localOldDevStates) {
    }

    public boolean isReceivingEndOpen() {
        return receivingEndOpen;
    }

    public void openReceivingEnd() {
        receivingEndOpen = true;
    }

    public void closeReceivingEnd() {
        receivingEndOpen = false;
    }

    public void addToWaitingMessageQueue(Message msg) {
        waitingMessageQueue.add(msg);
    }

    public List<Message> getWaitingMessageQueue() {
        return waitingMessageQueue;
    }

    public void setWaitingMessageQueue(List<Message> unprocessedMessages) {
        waitingMessageQueue = unprocessedMessages;
    }

    public void setSeqNos(Map<String, Integer> seqNos) {
    }

    public Map<String, Integer> getSeqNos() {
        return null;
    }

    public void setLastTriggered(Map<String, Integer> lastTriggered) {
    }

    public Map<String, Integer> getLastTriggered() {
        return null;
    }

    public int size() {
        return curMemberIDs.size();
    }

    public int getEpochNo() {
        return epochNo;
    }

    public KGroupType getType() {
        return type;
    }

    public boolean isType(KGroupType type) {
        if (type == null)
            return false;
        return type.equals(this.type);
    }

    public List<String> getCurMemberIDs() {
        return curMemberIDs;
    }

    public List<String> getMonitored() {
        return entitiesIDs;
    }

    public boolean isMember(String nodeID) {
        return curMemberIDs.contains(nodeID);
    }

    public boolean isOldMember(String nodeID) {
        return oldMemberIDs.contains(nodeID);
    }

    public boolean isInChargeOf(String entityID) {
        return entitiesIDs.contains(entityID);
    }

    public boolean isInChargeOf(List<String> entitiesIDs) {
        return this.entitiesIDs.containsAll(entitiesIDs);
    }

    public boolean isInChargeOf(MessagePayload msgPayload) {
        return type.equals(msgPayload.kGroupType) &&
                entitiesIDs.containsAll(msgPayload.entitiesIDs);
    }

    public boolean isFunc() {
        return curMemberIDs.size() > F;
    }

    public boolean isLeaderAndFunc(String nodeID) {
        return isLeader(nodeID) && isFunc();
    }

    public boolean isLockStrategy(LockStrategy lockStrategy) {
        return this.lockStrategy.equals(lockStrategy);
    }

    public void advLdrElctnStg(LeaderElectionStage newLdrElctnStg) {
        ldrElctnStg = newLdrElctnStg;
    }

    public LeaderElectionStage getLdrElctnStg() {
        return ldrElctnStg;
    }

    public boolean isLdrElctnStg(LeaderElectionStage stage) {
        return ldrElctnStg.equals(stage);
    }

    public void advStateTrnsfrStg(StateTransferStage newStateTrnsfrStg) {
        stateTrnsfrStg = newStateTrnsfrStg;
    }

    public StateTransferStage getStateTrnsfrStg() {
        return stateTrnsfrStg;
    }

    public boolean isStateTrnsfrStg(StateTransferStage stage) {
        return stateTrnsfrStg.equals(stage);
    }

    public String toString() {
        return type + " k-group in charge of " + entitiesIDs + " with members "
                + curMemberIDs + " and leader " + curKGroupLeaderNodeID;
    }

    // Device k-group methods
    public DeviceKGroup getDevKGrp() {
        return (DeviceKGroup) this;
    }

    public DeviceLock getDevLock(String devID) {
        return null;
    }

    public List<String> getRtnsToNotify(String devIP) {
        return List.of();
    }

    public int addNewLockRequest(String devID, String rtnID, int rtnSeqNo) {
        return -1;
    }

    public void replicateNewLockRequest(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
    }

    public void lockRequestReplicated(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
    }

    public Map<Integer, LockRequest> getNewLockRequests(String devID) {
        return null;
    }

    public boolean isNewLockRequestsEmpty(String devID) {
        return true;
    }

    public void lock(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
    }

    public int getLockerReqSeqNo(String devID) {
        return -1;
    }

    public int getLastLockedReqSeqNo(String devID) {
        return -1;
    }

    public LockRequest getLockQueueHead(String devID) {
        return null;
    }

    public int getLockReqSeqNo(String devID, String rtnID, int rtnSeqNo) {
        return -1;
    }

    public void replicateLockAcquisition(
            String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
    }

    public void removeLockRequest(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
    }

    public boolean isLocked(String devID) {
        return false;
    }

    public boolean isLocker(String devID, String rtnID, int rtnSeqNo) {
        return false;
    }

    public boolean isLockRequestReceived(String devID, String rtnID, int rtnSeqNo) {
        return false;
    }

    public boolean isLockRequestGranted(String devID, String rtnID, int rtnSeqNo) {
        return false;
    }

    public void replicateLockRelease(String devID, String rtnID, int rtnSeqNo) {
    }

    public void lockReleaseAcked(String devID, String rtnID, int rtnSeqNo) {
    }

    // Routine k-group methods
    public RoutineKGroup getRtnKGrp() {
        return (RoutineKGroup) this;
    }

    public List<String> getTouchedDevIDs(String rtnID) {
        return List.of();
    }

    public Map<Integer, RoutineStage> getRtnState(
            Map<String, Object> state, String rtnID) {
        return null;
    }

    public Map<Integer, RoutineStage> getRtnState(String rtnID) {
        return null;
    }

    public RoutineStage getRtnStage(
            Map<String, Object> state, String rtnID, int rtnSeqNo) {
        return null;
    }

    public RoutineStage getRtnStage(String rtnID, int rtnSeqNo) {
        return null;
    }

    // called by routine k-group leader
    public Set<Integer> getRtnSeqNos(String rtnID) {
        return null;
    }

    public int setRtnTriggered(String rtnID, int rtnSeqNo) {
        return -1;
    }

    public boolean existsRtnStage(String rtnID, int rtnSeqNo) {
        return false;
    }

    public boolean acquiredDevLockForRtn(String rtnID, int rtnSeqNo, String devID) {
        return false;
    }

    public void lockAcquireFailedForRtn(String rtnID, int rtnSeqNo) {
    }

    public boolean isRtnAcquiringLocks(String rtnID, int rtnSeqNo) {
        return false;
    }

    public boolean isLockAcquired(String devID, String rtnID, int rtnSeqNo) {
        return false;
    }

    public boolean isLockAcquireFailed(String rtnID, int rtnSeqNo) {
        return true;
    }

    public boolean areRtnLocksAcquired(String rtnID, int rtnSeqNo) {
        return false;
    }

    /**** Starting utility functions for parallel locking strategy *****/
    public void acquiredDevLockForParallel(
            String rtnID, int rtnSeqNo, int requestSeqNo, String devID) {
    }

    public boolean acquiredAllLocksForParallel(String rtnID, int rtnSeqNo) {
        return false;
    }

    public int getPreLockDevSeqNo(String rtnID, int rtnSeqNo, String devID) {
        return -1;
    }

    public Map<String, Integer> getAllPreLockDevSeqNo(String rtnID, int rtnSeqNo) {
        return null;
    }

    public List<String> getAllDevIdsLockAcquiredNotGranted(String rtnID, int rtnSeqNo) {
        return null;
    }

    public List<String> getAllUnreleasedDevIDs(String rtnID, int rtnSeqNo) {
        return null;
    }

    public int getRtnEndTS(String rtnID, Integer rtnSeqNo) {
        return -1;
    }

    public boolean isRtnExecuting(String rtnID, int rtnSeqNo) {
        return false;
    }

    public void setRtnExecuted(String rtnID, int rtnSeqNo) {
    }

    public LockRequest releaseLock(String devID, String rtnID, int rtnSeqNo, int reqSeqNo) {
        return null;
    }

    public void releasedDevLockForRtn(String rtnID, int rtnSeqNo, String devID) {
    }

    public boolean isRtnReleasingLocks(String rtnID, int rtnSeqNo) {
        return false;
    }

    public boolean areRtnLocksReleased(String rtnID, int rtnSeqNo) {
        return false;
    }

    public void removePreLockRecord(String rtnID, int rtnSeqNo, String devID) {
    }

    public boolean arePreLocksEmpty(String rtnID, int rtnSeqNo) {
        return false;
    }

    public void removeRtnStage(String rtnID, int rtnSeqNo) {
    }
}
