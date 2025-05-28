package device;

import java.util.*;
import java.util.Map.Entry;

import kgroup.*;
import kgroup.state.*;
import metric.KGroupMetric;
import metric.LoadMetric;
import metric.NodeRole;
import network.Network;
import network.message.*;
import network.message.payload.*;
import network.message.payload.election.*;
import network.message.payload.failure.*;
import network.message.payload.lock.*;
import network.message.payload.routineStage.*;
import network.message.payload.stateTransfer.*;
import routine.*;
import simulate.Event;
import simulate.EventType;

public class KGroupManager extends SmartDevice {
    // current node's variables
    private int F, epochLength;
    private Integer epochNo;

    // current node's database of device and routine k-groups, routines,
    // device-to-routine mappings
    private final List<DeviceKGroup> deviceKGroups = new ArrayList<DeviceKGroup>();
    private final List<RoutineKGroup> routineKGroups = new ArrayList<RoutineKGroup>();
    // devices and routines' IDs the k-groups of which the current node is a member
    // of right now
    private final List<String> curDevIDs = new ArrayList<String>();
    private final List<String> curRtnIDs = new ArrayList<String>();
    private KMemberMix kMemberMix;

    // lock strategy for routines to lock devices
    private int max_backoff = 300; // max backoff for a routine to retry lock request if it
                                   // fails before. Available only for parallel lock strategy.

    // mapping of quorums' ts to payload, counts for positive replies and related
    // k-group

    // A k-group manager is maintained in every programmable node of the network
    // myID specifies the current node's ID
    // K is the number of faults a k-group can tolerate
    // epochLength is the length of an epoch in time units
    // RTT is the expected RTT until a message reply is received and is measured in
    // time units
    // nodesIDs is a list of all the programmable nodes' IDs
    // devIDs is a list of all the devices's IDs (including programmable nodes)
    // routines are the routine classes for every rtnID
    // device/routineKGroupRange specify how many devices/routines will be handled
    // by the same k-group
    // network is the connection among all nodes/k-group managers, simulates the
    // real network
    public KGroupManager(
            String myID, int nodeLimit, int F, int K, int epochLength, int minTriggerOffset,
            int devMntrPeriod, List<String> nodesIDs, List<String> devIDs, Map<String, Routine> routines,
            List<List<String>> devClusters, int routineKGroupRange,
            Network network, int initialTS, int endTS, SortedMap<Integer, List<Event>> events,
            Map<String, Membership> membershipList,
            LeaderElectionPolicy lep, LockStrategy ls, KGroupSelectionPolicy kGroupPolicy) {
        super(
                myID, nodeLimit, network, initialTS, minTriggerOffset, devMntrPeriod, events,
                routines, membershipList);
        this.F = F;
        this.epochLength = epochLength;
        epochNo = 0;

        DeviceKGroup newDeviceKGroup;
        for (List<String> devCluster : devClusters) {
            newDeviceKGroup = new DeviceKGroup(
                    myID, devCluster, membershipList, F, K, lep, ls, kGroupPolicy, network, routines);
            if (newDeviceKGroup.isMember(myID)) {
                curDevIDs.addAll(devCluster);
                startDevRoles(NodeRole.MEMBER, curDevIDs);
            }
            deviceKGroups.add(newDeviceKGroup);
        }

        List<String> rtnIDs = new ArrayList<String>(routines.keySet());
        List<String> routinesIDsSublist;
        Map<String, Routine> rtnsSubmap = new HashMap<>();
        RoutineKGroup newRoutineKGroup;
        for (int counter = 0; counter < rtnIDs.size(); counter += routineKGroupRange) {
            rtnsSubmap.clear();
            routinesIDsSublist = rtnIDs.subList(
                    counter, Math.min(counter + routineKGroupRange, rtnIDs.size()));
            for (String rtnID : routinesIDsSublist) {
                rtnsSubmap.put(rtnID, routines.get(rtnID));
            }
            newRoutineKGroup = new RoutineKGroup(
                    myID, rtnsSubmap, membershipList, F, K,
                    lep, ls, kGroupPolicy, minTriggerOffset, network);
            if (newRoutineKGroup.isMember(myID)) {
                curRtnIDs.addAll(routinesIDsSublist);
                nodeMetric.startRtnRoles(NodeRole.MEMBER, curRtnIDs, _ts);
            }
            routineKGroups.add(newRoutineKGroup);
        }
    }

    public int getF() {
        return F;
    }

    public int getEpochLen() {
        return epochLength;
    }

    public void setKMemberMix(KMemberMix kMemberMix) {
        this.kMemberMix = kMemberMix;
    }

    public KMemberMix getKMemberMix() {
        return kMemberMix;
    }

    public boolean existUnprocessedEvents() {
        return !events.isEmpty();
    }

    public void addRecurringEvent(int initDelay, int period, int epochLen, Event e) {
        for (int addTS = _ts + initDelay; addTS < _ts + epochLen; addTS += period) {
            addEvent(addTS, e);
        }
    }

    public List<Integer> getUnicastSeqNosForDevStateReq(String devIP, int nextSeqNo) {
        List<Integer> relSeqNos = new ArrayList<>();
        int seqNo;
        MsgInfo msgInfo;
        for (Map.Entry<Integer, MsgInfo> msgInfoEntry : msgsInfo.entrySet()) {
            seqNo = msgInfoEntry.getKey();
            if (seqNo >= nextSeqNo)
                continue;
            msgInfo = msgInfoEntry.getValue();
            if (msgInfo instanceof QuorumMsgInfo)
                continue;
            if (!msgInfo.isMsgType(MessageType.DEVICE_STATE))
                continue;
            if (msgInfo.getMsgPayload().getMonitored().contains(devIP)) {
                relSeqNos.add(seqNo);
            }
        }
        return relSeqNos;
    }

    @Override
    public void processEvents() {
        int ts = getCurTS();
        // check if node has just joined
        if (events.get(ts) != null) {
            for (Event e : events.get(ts)) {
                if (procCount >= nodeLimit)
                    break;
                if (e.getType() == EventType.NODE_JOINED && e.getAffectedEntity().equals(myID)) {
                    nodeJoined(myID, true);
                    events.get(ts).remove(e);
                    for (DeviceKGroup kgroup : deviceKGroups) {
                        kgroup.updateKGroup(membershipList, kMemberMix, epochNo);
                    }
                    for (RoutineKGroup kgroup : routineKGroups) {
                        kgroup.updateKGroup(membershipList, kMemberMix, epochNo);
                    }
                    break;
                }
                procCount++;
            }
        }

        // check for new event
        List<Integer> toRemoveLists = new ArrayList<>();
        for (Integer checkTS : events.keySet()) {
            if (checkTS > ts) {
                break;
            }
            if (events.get(checkTS) != null) {
                List<Event> toRemove = new ArrayList<>();
                for (Event e : events.get(checkTS)) {
                    switch (e.getType()) {
                        case NODE_JOINED:
                            nodeJoined(e.getAffectedEntity(), true);
                            LoadMetric.recordLoad(myID, ts);
                            toRemove.add(e);
                            break;
                        case NODE_FAILED:
                            nodeFailureDetected(e.getAffectedEntity(), true);
                            LoadMetric.recordLoad(myID, ts);
                            toRemove.add(e);
                            break;
                        case ROUTINE_EXECUTED:
                            if (getRtnKGrp(e.getAffectedEntity()).isLeader(myID)) {
                                finishRtnExec(e.getAffectedEntity(), e.getRtnSeqNo());
                            }
                            LoadMetric.recordLoad(myID, ts);
                            toRemove.add(e);
                            break;
                        case ROUTINE_TRIGGERED:
                            String rtnID = e.getAffectedEntity();
                            log("checking routine triggered event for " + rtnID);
                            log("kgroup leader: " + getRtnKGrp(rtnID).getLeader());
                            if (routines.get(rtnID).shouldTrigger(myID)) {
                                log("should trigger " + rtnID, true);
                                if (!rtnTrigs.contains(e.getAffectedEntity()))
                                    rtnTrigs.add(e.getAffectedEntity());
                                toRemove.add(e);
                            } else if (!getRtnKGrp(rtnID).isMember(myID)) {
                                toRemove.add(e);
                                log("remove " + rtnID);
                            } else if (getRtnKGrp(rtnID).isLeader(myID)) {
                                int rtnSeqNo = setRtnTriggeredTS(rtnID, ts);
                                if (rtnSeqNo != -1) {
                                    gatherQuorumForRtnTrigger(rtnID, rtnSeqNo);

                                    recordTriggerUsrTime(rtnID, rtnSeqNo);
                                    recordTriggerSysTime(rtnID, rtnSeqNo);

                                    log(
                                            getRtnKGrp(rtnID),
                                            "Routine " + rtnID + "-" + rtnSeqNo + " triggered",
                                            true);
                                }
                                toRemove.add(e);
                            } else if (!getRtnKGrp(rtnID).isLeaderNull()) {
                                toRemove.add(e);
                            } else {
                                log("neither " + rtnID, true);
                            }
                            break;
                        case STATE_MONITOR:
                            monitorDevs(curDevIDs);
                            toRemove.add(e);
                            LoadMetric.recordLoad(myID, ts);
                            break;
                        case STATE_UPDATE:
                            if (e.getAffectedEntity().equals(myID)) {
                                log("Event");
                                updateDevState(new DeviceState(e.getNewDevState()));
                            }
                        default:
                            toRemove.add(e);
                            break;
                    }
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

    public void resendUnacked() {
        // resend messages that took too long to be acked
        List<Integer> toRemove = new ArrayList<>();
        int msgSeqNo, routeRTT, resendTO, timePassedFst, timePassedLst;
        MsgInfo msgInfo;
        UnicastMsgInfo unicastMsgInfo;
        Map<KGroup, LeaderElectionCause> elections = new HashMap<>();
        Map<KGroup, Entry<Integer, MsgInfo>> bullys = new HashMap<>();
        List<KGroup> localStates = new ArrayList<>();
        try {
            for (Entry<Integer, MsgInfo> unicastMsgInfoEntry : msgsInfo.entrySet()) {
                msgSeqNo = unicastMsgInfoEntry.getKey();
                msgInfo = unicastMsgInfoEntry.getValue();
                if (msgInfo instanceof QuorumMsgInfo)
                    continue;
                unicastMsgInfo = msgInfo.getUniMsgInfo();
                if (unicastMsgInfo.isAcknowledged()) {
                    toRemove.add(msgSeqNo);
                    continue;
                }
                routeRTT = msgInfo.getRouteRTT();
                if (routeRTT == 0) {
                    routeRTT = 1;
                }
                timePassedFst = msgInfo.getTimePassedSinceFstSend(_ts);
                timePassedLst = msgInfo.getTimePassedSinceLstSend(_ts);
                resendTO = network.getResendTO(routeRTT);
                // if we want to resend (unbounded or bound not exceeded yet)
                if (!msgInfo.isBounded() || (timePassedFst < network.getResendBound(routeRTT))) {
                    if (timePassedLst < resendTO)
                        continue;
                    // epoch has not changed: resend to same destination
                    if (epochNo == msgInfo.getFstEpoch()) {
                        log(
                                unicastMsgInfo.getThisKGrp(),
                                "Resending " + msgInfo.getMsgType()
                                        + " msg to " + unicastMsgInfo.getDst()
                                        + " (fstSendTS " + msgInfo.getFstTS() + ") at time " + _ts
                                        + ", send epoch: " + msgInfo.getFstEpoch() + " to k-group: "
                                        + ", " + unicastMsgInfo.getOtherKGrp());
                    }
                    // epoch has changed: resend after updating the destination (best guess)
                    else {
                        KGroup thisKGrp = unicastMsgInfo.getThisKGrp();
                        KGroup otherKgrp = unicastMsgInfo.getOtherKGrp();
                        if (otherKgrp != null) {
                            thisKGrp.log(
                                    "Resending " + msgInfo.getMsgPayload() + " to new destination");
                            String newDst = thisKGrp.getMostProbableLeader(false);
                            unicastMsgInfo.updateDst(newDst);
                        }
                    }
                    msgInfo.updateLastTS(_ts);
                    unicast(
                            unicastMsgInfo.getDst(), msgInfo.getMsgType(),
                            msgInfo.getMsgPayload(), msgSeqNo);
                }
                // if we do not want to resend anymore (bounded and bound exceeded)
                // we still want to act on the knowledge that some msg was not acked
                else {
                    // waiting no more for Election msgs; become bully
                    if (msgInfo.getMsgType() == MessageType.ELECTION) {
                        KGroup kgroup = unicastMsgInfo.getThisKGrp();
                        if (kgroup.ldrElctnStg == LeaderElectionStage.WAITING_ON_ELECTION_ACK) {
                            toRemove.add(msgSeqNo);
                            bullys.put(kgroup, unicastMsgInfoEntry);
                        } else if (kgroup.ldrElctnStg == LeaderElectionStage.WAITING_ON_ELECTED) {
                            if (timePassedFst > routeRTT * 8) {
                                toRemove.add(msgSeqNo);
                                log(
                                        kgroup,
                                        "Routine " + kgroup.entitiesIDs + " k-group: node " +
                                                myID + " not waiting on ELECTED anymore");
                                // must not modify the unicastMsgsInfo map (send new msgs)
                                // during its traversal
                                // therefore, call leader election after the loop
                                elections.put(
                                        kgroup, msgInfo.getMsgPayload().getLdrElctnCause());
                            }
                        } else {
                            toRemove.add(msgSeqNo);
                        }
                    }
                    // waiting no more for state transfer from old leader
                    // contact current/old k-group members
                    else if (msgInfo.getMsgType() == MessageType.KGROUP_STATE_REQUEST) {
                        toRemove.add(msgSeqNo);
                        KGroup kgroup = unicastMsgInfo.getThisKGrp();
                        localStates.add(kgroup);
                    } else {
                        toRemove.add(msgSeqNo);
                    }
                }
            }
        } catch (Exception e) {
            log("Error - " + msgsInfo);
            e.printStackTrace();
            e.printStackTrace(Network.getLogger());
        }
        for (Entry<KGroup, Entry<Integer, MsgInfo>> entry : bullys.entrySet()) {
            KGroup kgroup = entry.getKey();
            int oldSeqNo = entry.getValue().getKey();
            LeaderElectionCause cause = entry.getValue().getValue()
                    .getMsgPayload().getLdrElctnCause();
            int seqNo = quorum(
                    kgroup, MessageType.ELECTED, new ElectedMessagePayload(
                            epochNo, kgroup.type, kgroup.entitiesIDs, cause),
                    false, true);
            KGroupMetric.addLdrElctnSeqNo(kgroup, oldSeqNo, seqNo);
        }
        for (Entry<KGroup, LeaderElectionCause> entry : elections.entrySet()) {
            leaderElection(entry.getKey(), entry.getValue());
        }
        for (KGroup kgrp : localStates) {
            quorum(
                    kgrp, MessageType.LOCAL_KGROUP_STATE_REQUEST,
                    new LocalKGroupStateRequestMessagePayload(
                            epochNo, kgrp.getType(), kgrp.getMonitored()),
                    true, false);
        }
        for (Integer _msgSeqNo : toRemove) {
            removeMsgInfo(_msgSeqNo);
        }
        toRemove.clear();

        QuorumMsgInfo quorumMsgInfo;
        for (Entry<Integer, MsgInfo> quorumMsgInfoEntry : msgsInfo.entrySet()) {
            msgSeqNo = quorumMsgInfoEntry.getKey();
            msgInfo = quorumMsgInfoEntry.getValue();
            if (msgInfo instanceof UnicastMsgInfo)
                continue;
            quorumMsgInfo = msgInfo.getQuorumMsgInfo();
            if (quorumMsgInfo.isApproved()) {
                toRemove.add(msgSeqNo);
                continue;
            }
            routeRTT = msgInfo.getRouteRTT();
            if (routeRTT == 0) {
                routeRTT = 1;
            }
            timePassedFst = msgInfo.getTimePassedSinceFstSend(_ts);
            timePassedLst = msgInfo.getTimePassedSinceLstSend(_ts);
            resendTO = network.getResendTO(routeRTT);
            // if we want to resend (unbounded or bound not exceeded yet)
            if (!msgInfo.isBounded() || (timePassedFst < network.getResendBound(routeRTT))) {
                // if timeout since last send exceeded and same epoch
                // if diff epoch: kgroup has changed
                if (timePassedLst >= resendTO && epochNo == msgInfo.getFstEpoch()) {
                    log(
                            "Resending " + msgInfo.getMsgType() + " msg (initialSendTS "
                                    + msgInfo.getFstTS() + ") at time " + _ts
                                    + ", initial epoch: " + msgInfo.getFstEpoch()
                                    + " about k-group " + msgInfo.getKGrp()
                                    + ", already replied: " + quorumMsgInfo.getRepliedNodesIDs());
                    multicast(
                            quorumMsgInfo.getNoReplyNodesIDs(), msgInfo.getMsgType(),
                            msgSeqNo, msgInfo.getMsgPayload());
                    msgInfo.updateLastTS(_ts);
                }
            } else {
                toRemove.add(msgSeqNo);
            }
        }
        for (Integer _msgSeqNo : toRemove) {
            removeMsgInfo(_msgSeqNo);
        }
    }

    @Override
    public int incrementTS() {
        int newTS = super.incrementTS();

        // check for new epoch
        if (epochNo == 0 || newTS == epochNo * epochLength) {
            incrementEpoch();
        }

        processEvents();

        return newTS;
    }

    public int incrementEpoch() {
        int currentEpoch = ++epochNo;

        if (!isNodeOnline()) {
            return currentEpoch;
        }

        // updating all k-group members and moving on to the leader election phase
        curDevIDs.clear();
        nodeMetric.finishDevRoles(_ts);
        for (DeviceKGroup kgroup : deviceKGroups) {
            kgroup.updateKGroup(membershipList, kMemberMix, epochNo);
            kgroup.resetCountFailedNodes();
            if (kgroup.isMember(myID)) {
                curDevIDs.addAll(kgroup.entitiesIDs);
                nodeMetric.startDevRoles(NodeRole.MEMBER, curDevIDs, _ts);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
            }
        }
        curRtnIDs.clear();
        nodeMetric.finishRtnRoles(_ts);
        for (RoutineKGroup kgroup : routineKGroups) {
            kgroup.updateKGroup(membershipList, kMemberMix, epochNo);
            kgroup.resetCountFailedNodes();
            if (kgroup.isMember(myID)) {
                curRtnIDs.addAll(kgroup.entitiesIDs);
                nodeMetric.startRtnRoles(NodeRole.MEMBER, curRtnIDs, _ts);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
            }
        }

        return currentEpoch;
    }

    public int changeTS(int newTS) {
        int oldTS = _ts;
        _ts = newTS;
        log("new ts: " + newTS);

        if (oldTS < newTS + epochLength) {
            changeEpoch(newTS / epochLength + 1);
        }

        processEvents();
        return newTS;
    }

    // this method is not used when implementing LSH, if need to use it in the
    // future,
    // need to take care about the LSH part
    public int changeEpoch(int newEpochNo) {
        epochNo = newEpochNo;

        if (!isNodeOnline()) {
            return newEpochNo;
        }

        log(
                "******************************************************************** new epoch " +
                        newEpochNo,
                true);

        curDevIDs.clear();
        for (DeviceKGroup kgroup : deviceKGroups) {
            kgroup.updateKGroup(membershipList, kMemberMix, epochNo);
            if (kgroup.isMember(myID)) {
                curDevIDs.addAll(kgroup.entitiesIDs);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
            }
        }
        curRtnIDs.clear();
        for (RoutineKGroup kgroup : routineKGroups) {
            kgroup.updateKGroup(membershipList, kMemberMix, epochNo);
            if (kgroup.isMember(myID)) {
                curRtnIDs.addAll(kgroup.entitiesIDs);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
            }
        }

        return newEpochNo;
    }

    public int getEpochNo() {
        return epochNo;
    }

    private void processWaitMsgQueue(List<? extends KGroup> kgrps) {
        Message msg;
        for (KGroup kgroup : kgrps) {
            while (kgroup.isReceivingEndOpen() && !kgroup.getWaitingMessageQueue().isEmpty()) {
                msg = kgroup.getWaitingMessageQueue().remove(0);
                if (!kgroup.isMember(myID)) {
                    if (kgroup.getOldLeader().equals(myID)) {
                        msg.dst = kgroup.getLeader();
                    } else {
                        msg.dst = kgroup.getMostProbableLeader(false);
                    }
                    network.sendMsg(msg); // could be remote call
                } else {
                    if (kgroup.getLeader().equals(myID)) {
                        msg.process(this, kgroup);
                    } else {
                        msg.dst = kgroup.getLeader();
                        network.sendMsg(msg); // could be remote call
                    }
                }
            }
        }
    }

    @Override
    public void recvAndProcessMsgs(int curTS) {
        processWaitMsgQueue(deviceKGroups);
        processWaitMsgQueue(routineKGroups);

        Set<Message> rcvdMsgs = network.recvPastMsgs(myID, curTS);
        // log("Received " + rcvdMsgs);
        KGroup kgroup;
        for (Message rcvdMsg : rcvdMsgs) {
            kgroup = getResponsibleKGrp(rcvdMsg);
            if (kgroup != null)
                log(kgroup, "Received " + rcvdMsg);
            else
                log(kgroup, "Received " + rcvdMsg);

            switch (rcvdMsg.type) {
                case ELECTION:
                case ELECTION_ACK:
                case ELECTED:
                case ELECTED_ACK:
                case KGROUP_STATE_REQUEST:
                case KGROUP_STATE:
                case NOT_OLD_LEADER:
                case LOCAL_KGROUP_STATE_REQUEST:
                case LOCAL_KGROUP_STATE:
                case KGROUP_STATE_DISTRIBUTION:
                case KGROUP_STATE_ACK:
                case NODE_RECRUITEMENT_REQUEST:
                case NODE_RECRUITED:
                    if (kgroup == null) {
                        log(
                                "TOP Msg received: referenced k-group in charge of "
                                        + rcvdMsg.payload.kGroupType + " "
                                        + rcvdMsg.payload.entitiesIDs + " which does not exist!");
                        continue;
                    }
                case NODE_FAILURE:
                case NODE_FAILURE_ACK:
                case DEVICE_STATE_CHECK:
                case DEVICE_STATE:
                case DEVICE_COMMAND:
                case DEVICE_COMMAND_ACK:
                    rcvdMsg.process(this, kgroup);
                    break;
                default:
                    if (kgroup == null) {
                        log(
                                kgroup,
                                "DEFAULT Msg " + rcvdMsg.type
                                        + " received: referenced k-group in charge of "
                                        + rcvdMsg.payload.kGroupType + " "
                                        + rcvdMsg.payload.entitiesIDs + " which does not exist!");
                        continue;
                    }
                    if (kgroup.isReceivingEndOpen()) {
                        rcvdMsg.process(this, kgroup);
                    } else {
                        kgroup.addToWaitingMessageQueue(rcvdMsg);
                    }
            }
        }
    }

    public boolean isMemberOfDevKGrp(String devID) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devID)) {
                return kgroup.isMember(myID);
            }
        }
        return false;
    }

    public boolean isMemberOfDevKGrp(List<String> devIDs) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devIDs)) {
                return kgroup.isMember(myID);
            }
        }
        return false;
    }

    public boolean isMemberOfRtnKGrp(String rtnID) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(rtnID)) {
                return kgroup.isMember(myID);
            }
        }
        return false;
    }

    public boolean isMemberOfRtnKGrp(List<String> rtnIDs) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(rtnIDs)) {
                return kgroup.isMember(myID);
            }
        }
        return false;
    }

    @Override
    public DeviceKGroup getDevKGrp(String devID) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devID)) {
                return kgroup;
            }
        }
        return null;
    }

    @Override
    public DeviceKGroup getDevKGrp(List<String> devIDs) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devIDs)) {
                return kgroup;
            }
        }
        return null;
    }

    @Override
    public RoutineKGroup getRtnKGrp(String rtnID) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(rtnID)) {
                return kgroup;
            }
        }
        return null;
    }

    @Override
    public RoutineKGroup getRtnKGrp(List<String> rtnIDs) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(rtnIDs)) {
                return kgroup;
            }
        }
        return null;
    }

    @Override
    protected KGroup getResponsibleKGrp(MessagePayload payload) {
        for (KGroup kgrp : deviceKGroups) {
            if (kgrp.isType(payload.getKGroupType())) {
                if (kgrp.isInChargeOf(payload)) {
                    return kgrp;
                }
            }
        }
        for (KGroup kgrp : routineKGroups) {
            if (kgrp.isType(payload.getKGroupType())) {
                if (kgrp.isInChargeOf(payload)) {
                    return kgrp;
                }
            }
        }
        return null;
    }

    public int countAllKGrps() {
        return deviceKGroups.size() + routineKGroups.size();
    }

    public int countLeaderFuncKGrps() {
        int leaderFuncKGrps = 0;

        for (KGroup kgrp : deviceKGroups) {
            if (kgrp.isLeaderAndFunc(myID)) {
                leaderFuncKGrps++;
            }
        }
        for (KGroup kgrp : routineKGroups) {
            if (kgrp.isLeaderAndFunc(myID)) {
                leaderFuncKGrps++;
            }
        }

        return leaderFuncKGrps;
    }

    @Override
    public void unicast(String dst, MessageType type, MessagePayload payload, int msgSeqNo) {
        log("dst: " + dst + ", dstTS: " + (_ts + network.getRouteOWD(myID, dst)) + ", type: " + type);
        Message msg = new Message(
                myID, _ts, msgSeqNo, dst, _ts + network.getRouteOWD(myID, dst), type, payload);
        network.sendMsg(msg);
    }

    public void multicast(
            KGroup kgroup, MessageType type, MessagePayload payload, int msgSeqNo, boolean oldKGroup) {
        for (String dst : oldKGroup ? kgroup.oldMemberIDs : kgroup.curMemberIDs) {
            Message msg = new Message(
                    myID, _ts, msgSeqNo, dst, _ts + network.getRouteOWD(myID, dst), type, payload);
            network.sendMsg(msg);
        }
    }

    public void multicast(
            List<String> recipients, MessageType type, int msgSeqNo, MessagePayload payload) {
        for (String dst : recipients) {
            Message msg = new Message(
                    myID, _ts, msgSeqNo, dst, _ts + network.getRouteOWD(myID, dst), type, payload);
            network.sendMsg(msg);
        }
    }

    public int quorum(
            KGroup kgroup, MessageType msgType, MessagePayload payload,
            boolean oldKGroup, boolean boundedWait) {
        int routeOWD, maxRouteOWD = -1;
        for (String dst : (oldKGroup ? kgroup.oldMemberIDs : kgroup.curMemberIDs)) {
            routeOWD = network.getRouteOWD(myID, dst);
            if (maxRouteOWD == -1 || routeOWD > maxRouteOWD) {
                maxRouteOWD = routeOWD;
            }
        }
        int seqNo = getNextSeqNo();
        msgsInfo.put(
                seqNo, new QuorumMsgInfo(
                        msgType, payload, kgroup, boundedWait, maxRouteOWD, _ts, epochNo, seqNo));
        multicast(kgroup, msgType, payload, seqNo, oldKGroup);
        log(
                kgroup,
                "Sent " + msgType + " msg with seqNo " + seqNo
                        + " and maxRouteOWD " + maxRouteOWD);
        return seqNo;
    }

    public int remoteCallwithDelay(
            KGroup thisKGrp, KGroup otherKGrp, String dst, MessageType type,
            MessagePayload payload, boolean boundedWait, int delay) {
        int routeOWD = network.getRouteOWD(myID, dst);
        int seqNo = getNextSeqNo();
        msgsInfo.put(
                seqNo, new UnicastMsgInfo(
                        type, payload, thisKGrp, otherKGrp, dst, boundedWait, 2 * routeOWD, _ts + delay));
        Message msg = new Message(
                myID, _ts + delay, seqNo, dst, _ts + delay + routeOWD, type, payload);
        network.sendMsg(msg);
        return seqNo;
    }

    public int remoteCallwithDelay(
            KGroup thisKGrp, String dst, MessageType type,
            MessagePayload payload, boolean boundedWait, int delay) {
        return remoteCallwithDelay(thisKGrp, null, dst, type, payload, boundedWait, delay);
    }

    public int remoteCall(
            KGroup thisKGrp, KGroup otherKGrp, String dst, MessageType type,
            MessagePayload payload, boolean boundedWait) {
        if (dst == null) {
            dst = otherKGrp.getMostProbableLeader(false);
        }
        int routeOWD = network.getRouteOWD(myID, dst);
        int seqNo = getNextSeqNo();
        UnicastMsgInfo msgInfo = new UnicastMsgInfo(
                type, payload, thisKGrp, otherKGrp, dst, boundedWait, routeOWD, getCurTS());
        msgsInfo.put(seqNo, msgInfo);
        log(
                thisKGrp,
                "Sent " + type + " msg with seqNo " + seqNo
                        + " and routeOWD " + routeOWD + " to " + dst);
        // log("Increased msg seq no to " + seqNo + "\n\t" + msgsInfo);
        unicast(dst, type, payload, seqNo);
        return seqNo;
    }

    public int remoteCall(
            KGroup thisKGrp, String dst, MessageType type, MessagePayload payload, boolean boundedWait) {
        return remoteCall(thisKGrp, (KGroup) null, dst, type, payload, boundedWait);
    }

    public int remoteCall(
            KGroup thisKGrp, KGroupType kGrpType, String entityID,
            MessageType msgType, MessagePayload payload, boolean boundedWait) {
        KGroup dstKGrp;
        switch (kGrpType) {
            case ROUTINE:
                dstKGrp = getRtnKGrp(entityID);
                break;
            case DEVICE:
                dstKGrp = getDevKGrp(entityID);
                break;
            default:
                dstKGrp = null;
        }
        return remoteCall(thisKGrp, dstKGrp, null, msgType, payload, boundedWait);
    }

    public int remoteCall(
            KGroup thisKGrp, KGroupType kGrpType, List<String> entityIDs,
            MessageType msgType, MessagePayload payload, boolean boundedWait) {
        KGroup dstKGrp;
        switch (kGrpType) {
            case ROUTINE:
                dstKGrp = getRtnKGrp(entityIDs);
                break;
            case DEVICE:
                dstKGrp = getDevKGrp(entityIDs);
                break;
            default:
                dstKGrp = null;
        }
        return remoteCall(thisKGrp, dstKGrp, null, msgType, payload, boundedWait);
    }

    @Override
    public boolean isNodeOnline() {
        return isNodeOnline(myID);
    }

    @Override
    public boolean isNodeOnline(String nodeID) {
        return membershipList.get(nodeID) == Membership.ONLINE;
    }

    public void nodeJoined(String joinedNodeID, boolean instant) {
        membershipList.replace(joinedNodeID, Membership.ONLINE);
        if (instant) {
            log("Node " + joinedNodeID + " joined", true);
            return;
        }
    }

    public void nodeFailureDetected(String failedNodeID, boolean instant) {
        membershipList.replace(failedNodeID, Membership.OFFLINE);
        if (!isNodeOnline())
            return;
        log("Node " + failedNodeID + "'s failure detected", true);
        if (instant) {
            handleNodeFailure(failedNodeID);
            return;
        }
        MessagePayload payload;
        List<String> recipients = new ArrayList<>();
        String recipient;
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isMember(failedNodeID)) {
                payload = new NodeFailureMessagePayload(kgroup.type, kgroup.entitiesIDs, failedNodeID);
                if (kgroup.isMember(myID)) {
                    recipient = kgroup.getLeader();
                } else {
                    recipient = kgroup.getMostProbableLeader(false);
                }
                if (!recipients.contains(recipient)) {
                    remoteCall(
                            kgroup, recipient, MessageType.NODE_FAILURE, payload, false);
                    recipients.add(recipient);
                }
            }
        }
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isMember(failedNodeID)) {
                payload = new NodeFailureMessagePayload(kgroup.type, kgroup.entitiesIDs, failedNodeID);
                if (kgroup.isMember(myID)) {
                    recipient = kgroup.getLeader();
                } else {
                    recipient = kgroup.getMostProbableLeader(false);
                }
                if (!recipients.contains(recipient)) {
                    remoteCall(
                            kgroup, recipient, MessageType.NODE_FAILURE, payload, false);
                    recipients.add(recipient);
                }
            }
        }
    }

    // called by node when it is informed of the failure
    public void handleNodeFailure(String failedNodeID) {
        if (myID.equals(failedNodeID)) {
            network.clearMsgs(failedNodeID);
        }

        membershipList.replace(failedNodeID, Membership.OFFLINE);
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isMember(failedNodeID)) {
                boolean failedLeader = kgroup.updateFailedNodes(failedNodeID);
                if (kgroup.isMember(myID)) {
                    if (failedLeader || kgroup.getLeader() == null) {
                        log(kgroup, "leader died -> new leader election", true);
                        leaderElection(kgroup, LeaderElectionCause.LEADER_FAILURE);
                    } else if (kgroup.getLeader().equals(myID)) {
                        if (kgroup.getCountFailedNodes() >= F) {
                            List<String> replacementNodes = kgroup.replaceFailedNodes(membershipList, epochNo);
                            replaceKGroupNodes(kgroup, replacementNodes);
                            log(kgroup, "replacing failed nodes with " + replacementNodes, true);
                        }
                    }
                }
            }
        }
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isMember(failedNodeID)) {
                boolean failedLeader = kgroup.updateFailedNodes(failedNodeID);
                if (kgroup.isMember(myID)) {
                    if (failedLeader || kgroup.getLeader() == null) {
                        log(kgroup, "leader died -> new leader election", true);
                        leaderElection(kgroup, LeaderElectionCause.LEADER_FAILURE);
                    } else if (kgroup.getLeader().equals(myID)) {
                        if (kgroup.getCountFailedNodes() >= F) {
                            List<String> replacementNodes = kgroup.replaceFailedNodes(membershipList, epochNo);
                            replaceKGroupNodes(kgroup, replacementNodes);
                            log(kgroup, "replacing failed nodes with " + replacementNodes, true);
                        }
                    }
                }
            }
        }
    }

    public void replaceKGroupNodes(KGroup kgroup, List<String> replacementNodes) {
        NodeRecruitmentRequestMessagePayload payload;
        payload = new NodeRecruitmentRequestMessagePayload(epochNo, kgroup.type, kgroup.getState());

        for (String nodeID : replacementNodes) {
            switch (kgroup.type) {
                case ROUTINE:
                    remoteCall(
                            kgroup, nodeID, MessageType.NODE_RECRUITEMENT_REQUEST, payload, true);
                    break;
                case DEVICE:
                    remoteCall(
                            kgroup, nodeID, MessageType.NODE_RECRUITEMENT_REQUEST, payload, true);
            }

        }
        kgroup.resetCountFailedNodes();
    }

    public void recruited(KGroup kgrp) {
        if (kgrp instanceof DeviceKGroup)
            curDevIDs.addAll(kgrp.getMonitored());
        else
            curRtnIDs.addAll(kgrp.getMonitored());
    }

    // Bully Leader Election Algorithm
    public void leaderElection(KGroup kgroup, LeaderElectionCause cause) {
        if ((cause == LeaderElectionCause.NEW_EPOCH && kgroup.getLeader() == null) ||
                (cause == LeaderElectionCause.LEADER_FAILURE
                        && !kgroup.curMemberIDs.contains(kgroup.getLeader()))
                ||
                (kgroup.isLdrElctnStg(LeaderElectionStage.NOT_STARTED))) {
            log(kgroup, "Starting election");
            kgroup.closeReceivingEnd();
            kgroup.advLdrElctnStg(LeaderElectionStage.ONGOING);

            // current node is the bully
            if (myID.equals(kgroup.getMostProbableLeader(false))) {
                int seqNo = quorum(
                        kgroup, MessageType.ELECTED, new ElectedMessagePayload(
                                epochNo, kgroup.type, kgroup.entitiesIDs, cause),
                        false, true);
                KGroupMetric.startLdrElctn(kgroup, seqNo, _ts);
                if (kgroup.type.equals(KGroupType.ROUTINE)) {
                    nodeMetric.startRtnRoles(NodeRole.LEADER, kgroup.entitiesIDs, _ts);
                } else {
                    nodeMetric.startDevRoles(NodeRole.LEADER, kgroup.entitiesIDs, _ts);
                }
            } else {
                // initiate an election
                MessagePayload payload = new ElectionMessagePayload(
                        epochNo, kgroup.type, kgroup.entitiesIDs, cause);
                int lastSeqNo = -1, newSeqNo;
                for (String nodeID : kgroup.curMemberIDs) {
                    // send Election msg to every node in the k-group
                    // which is more appropriate for leader
                    if (kgroup.moreAppr(nodeID, myID, epochNo)) {
                        newSeqNo = remoteCall(
                                kgroup, nodeID, MessageType.ELECTION, payload, true);
                        if (lastSeqNo == -1) {
                            KGroupMetric.startLdrElctn(kgroup, newSeqNo, _ts);
                        } else {
                            KGroupMetric.addLdrElctnSeqNo(kgroup, lastSeqNo, newSeqNo);
                        }
                    }
                }
                kgroup.advLdrElctnStg(LeaderElectionStage.WAITING_ON_ELECTION_ACK);
            }
        }
    }

    public void stateTransfer(KGroup kgroup, LeaderElectionCause cause) {
        // leader election is complete -- moving on to state transfer now
        if (kgroup.getLeader().equals(myID) && epochNo != 1
                && !kgroup.isStateTrnsfrStg(StateTransferStage.COMPLETE)) {
            kgroup.advStateTrnsfrStg(StateTransferStage.ONGOING);
            int seqNo;
            if (cause == LeaderElectionCause.NEW_EPOCH) {
                // only request state transfer if current node was not the leader
                // in the last epoch as well
                if (kgroup.getOldLeader() == null || !kgroup.getOldLeader().equals(myID)) {
                    if (kgroup.getOldLeader() == null && kgroup.oldMemberIDs.contains(myID)) {
                        // in case I just joined
                        // but the system believes I should have been the leader
                        kgroup.oldLeaderCandidates.remove(myID);
                    }

                    seqNo = remoteCall(
                            kgroup, kgroup.getMostProbableLeader(true),
                            MessageType.KGROUP_STATE_REQUEST, new KGroupStateRequestMessagePayload(
                                    kgroup, cause),
                            true);
                } else {
                    seqNo = quorum(
                            kgroup, MessageType.KGROUP_STATE_DISTRIBUTION,
                            new KGroupStateDistributionMessagePayload(kgroup), false, false);
                }
            } else {
                seqNo = quorum(
                        kgroup, MessageType.LOCAL_KGROUP_STATE_REQUEST,
                        new LocalKGroupStateRequestMessagePayload(
                                epochNo, kgroup.type, kgroup.entitiesIDs),
                        false, false);
            }
            KGroupMetric.startStateTrnsfr(kgroup, seqNo, _ts);
        } else if (epochNo == 1) {
            kgroup.openReceivingEnd();
            if (kgroup.isType(KGroupType.DEVICE) && kgroup.isLeader(myID)) {
                addRecurringEvent(
                        0, devMntrPeriod, getEpochLen(), new Event(EventType.STATE_MONITOR));
            }
        }
    }

    @Override
    public int setRtnTriggered(String rtnID, int rtnSeqNo) {
        return getRtnKGrp(rtnID).setRtnTriggered(rtnID, rtnSeqNo);
    }

    @Override
    public int setRtnTriggeredTS(String rtnID, int ts) {
        return getRtnKGrp(rtnID).setRtnTriggeredTS(rtnID, ts);
    }

    public void gatherQuorumForRtnTrigger(String rtnID, int rtnSeqNo) {
        quorum(
                getRtnKGrp(rtnID), MessageType.TRIGGER_QUORUM,
                new TriggerQuorumMessagePayload(epochNo, rtnID, rtnSeqNo),
                false, false);
    }

    public void requestDevLockForRtn(String rtnID, int rtnSeqNo) {
        String devID = getRtnKGrp(rtnID).nextDevToLock(rtnID, rtnSeqNo);
        if (devID == null) {
            return;
        }
        log(
                getRtnKGrp(rtnID),
                "Next device for routine " + rtnID + "-" + rtnSeqNo + " to lock: " + devID,
                true);
        LockRequestMessagePayload payload = new LockRequestMessagePayload(
                epochNo, devID, rtnID, rtnSeqNo);
        remoteCall(
                getRtnKGrp(rtnID), KGroupType.DEVICE, devID,
                MessageType.LOCK_REQUEST, payload, false);
    }

    public void requestDevLockForRtnInParallelWithDelay(
            String rtnID, int rtnSeqNo, int delay) {
        List<String> devToTouch = routines.get(rtnID).getTouchedDevIDs();
        for (String devID : devToTouch) {
            System.out.println("getting k group for: " + devID);
            DeviceKGroup deviceKGroup = getDevKGrp(devID);
            List<String> entitiesIDs = new ArrayList<>();
            entitiesIDs.add(devID);
            LockRequestMessagePayload payload = new LockRequestMessagePayload(
                    epochNo, devID, rtnID, rtnSeqNo);
            remoteCallwithDelay(
                    deviceKGroup, deviceKGroup.getMostProbableLeader(false),
                    MessageType.PLOCK_REQUEST, payload, false, delay);
        }
    }

    public void requestDevLockForRtnInParallel(String rtnID, int rtnSeqNo) {
        requestDevLockForRtnInParallelWithDelay(rtnID, rtnSeqNo, 0);
    }

    public void requestDevLockCancel(String rtnID, int rtnSeqNo) {
        RoutineKGroup routineKGroup = getRtnKGrp(rtnID);
        if (routineKGroup.arePreLocksEmpty(rtnID, rtnSeqNo)) {
            Random rand = new Random();
            int delay = rand.nextInt(max_backoff);
            routineKGroup.startAcquiringLocksForRtn(rtnID, rtnSeqNo);
            log(routineKGroup, "Routine " + rtnID + "-" + rtnSeqNo + " all dev cancelled.", true);
            Map<Integer, RoutineStage> stages = routineKGroup.getRtnState(rtnID);
            log("    local state: " + stages.get(rtnSeqNo).toString());
            requestDevLockForRtnInParallelWithDelay(rtnID, rtnSeqNo, delay);
        }
    }

    public void gatherQuorumForDevLockRequest(
            String rtnID, int rtnSeqNo, String devID, int reqSeqNo) {
        MessagePayload payload = new LockRequestQuorumMessagePayload(
                epochNo, devID, rtnID, rtnSeqNo, reqSeqNo);
        quorum(
                getDevKGrp(devID), MessageType.LOCK_REQUEST_QUORUM,
                payload, false, false);
    }

    public void gatherQuorumForDevicePLockRequest(
            String rtnID, int rtnSeqNo, String devID, int reqSeqNo) {
        MessagePayload payload = new PLockRequestQuorumMessagePayload(
                epochNo, devID, rtnID, rtnSeqNo, reqSeqNo);
        quorum(
                getDevKGrp(devID) /* kgroup */,
                MessageType.PLOCK_REQUEST_QUORUM, payload,
                false /* oldKGroup */, false /* boundedWait */
        );
    }

    public void gatherQuorumForDevicePLockCancel(
            String rtnID, int rtnSeqNo, String devID, int reqSeqNo) {
        MessagePayload payload = new PLockCancelQuorumMessagePayload(
                epochNo, devID, rtnID, rtnSeqNo, reqSeqNo);
        quorum(
                getDevKGrp(devID) /* kgroup */,
                MessageType.PLOCK_CANCEL_QUORUM, payload,
                false /* oldKGroup */, false /* boundedWait */
        );
    }

    /**
     * Gather quorum for actually getting the lock (lock acquisition)
     * For SERIAL locking only.
     */
    public void gatherQuorumForDevLockAcquisition(
            String rtnID, int rtnSeqNo, String devID) {
        int requestSeqNo = getDevKGrp(devID).getLockQueueHeadReqSeqNo(devID);
        gatherQuorumForDevLockAcquisition(rtnID, rtnSeqNo, devID, requestSeqNo);
    }

    /**
     * Gather quorum for actually getting the lock (lock acquisition)
     * This request if sent from device leader in SERIAL locking, but from routine
     * leader in PARALLEL
     * locking. Thus, SERIAL locking does not need requestSeqNo in the interface.
     * All SERIAL locking
     * related call should go with gatherQuorumForDevLockAcquisition(String rtnID,
     * int
     * rtnSeqNo, String devID).
     **/
    public void gatherQuorumForDevLockAcquisition(
            String rtnID, int rtnSeqNo, String devID, int requestSeqNo) {
        List<String> entitiesIDs = List.of(devID);
        MessagePayload payload = new LockedQuorumMessagePayload(
                epochNo, entitiesIDs, rtnID, requestSeqNo, rtnSeqNo);
        quorum(
                getDevKGrp(devID), MessageType.LOCKED_QUORUM,
                payload, false, false);
    }

    public void gatherQuorumForDevPLockRequest(
            String rtnID, int rtnSeqNo, String devID, int reqSeqNo) {
        MessagePayload payload = new PLockRequestQuorumMessagePayload(
                epochNo, devID, rtnID, rtnSeqNo, reqSeqNo);
        quorum(
                getRtnKGrp(rtnID), MessageType.PLOCK_REQUEST_QUORUM, payload,
                false /* oldKGroup */, false /* boundedWait */
        );
    }

    @Override
    public void releaseDevLocksForRtn(String rtnID, int rtnSeqNo) {
        List<String> touchedDevIDs = routines.get(rtnID).getTouchedDevIDs();
        log(
                getRtnKGrp(rtnID),
                "routine " + rtnID + "-" + rtnSeqNo + "'s unreleased devices: "
                        + getRtnKGrp(rtnID).getRtnStage(rtnID, rtnSeqNo).getUnreleasedDevIDs(),
                true);
        rtnMetric.recordRtnLockReleaseTime(rtnID, rtnSeqNo, _ts);
        rtnMetric.recordMultiDevLockReleaseTime(touchedDevIDs, _ts);
        log(
                getRtnKGrp(rtnID),
                "Routine " + rtnID + "-" + rtnSeqNo + " finished execution, releasing devices "
                        + touchedDevIDs,
                true);
        getRtnKGrp(rtnID).startReleasingRtnLocks(rtnID, rtnSeqNo);
        for (String devID : touchedDevIDs) {
            releaseDevLockForRtn(rtnID, rtnSeqNo, devID);
        }
    }

    @Override
    public void releaseDevLockForRtn(String rtnID, int rtnSeqNo, String devID) {
        DeviceKGroup deviceKGroup = getDevKGrp(devID);
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(devID);
        LockReleaseRequestMessagePayload payload;
        payload = new LockReleaseRequestMessagePayload(epochNo, entitiesIDs, rtnID, rtnSeqNo);
        remoteCall(
                deviceKGroup, deviceKGroup.getMostProbableLeader(false),
                MessageType.LOCK_RELEASE_REQUEST, payload, false);
    }

    public void gatherQuorumForDevLockRelease(
            String rtnID, int rtnSeqNo, String devID, int reqSeqNo) {
        MessagePayload payload = new LockReleaseQuorumMessagePayload(
                epochNo, devID, rtnID, rtnSeqNo, reqSeqNo);
        quorum(getDevKGrp(devID), MessageType.LOCK_RELEASE_QUORUM, payload, false, false);
    }

    public void recordLockRequestAckTime(String rtnID, String devID, int rtnSeqNo) {
        rtnMetric.recordLockRequestAckTime(rtnID, devID, rtnSeqNo, _ts);
    }

    public void setBackoffMaxDelay(int backoff) {
        max_backoff = backoff;
    }

    public Map<String, Membership> getMembershipList() {
        return membershipList;
    }

    public List<DeviceKGroup> getDevKGrps() {
        return deviceKGroups;
    }

    public List<RoutineKGroup> getRtnKGrps() {
        return routineKGroups;
    }
}
