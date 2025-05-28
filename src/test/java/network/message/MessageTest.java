package network.message;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import kgroup.state.DeviceLock;
import kgroup.state.RoutineStage;
import network.message.payload.MessagePayload;
import network.message.payload.election.*;
import network.message.payload.failure.*;
import network.message.payload.lock.*;
import network.message.payload.monitor.*;
import network.message.payload.routineStage.*;
import network.message.payload.stateTransfer.*;
import routine.DeviceState;

public class MessageTest {
    @Test
    public void objectSerializationTest() {
        Message msg = new Message("0", 0, 1, "8", 2, (MessageType) null, (MessagePayload) null);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            oos.flush();
            System.out.println(bos.toByteArray().length);
        }
        catch (Exception ex) {
            System.out.println("Exception during byte size calculation");
            ex.printStackTrace(System.out);
        }
    }

    private void printMsgSizesHelper(List<Integer> bytes, String type) {
        System.out.println("\nPrinting " + type + " byte summary");
        double mean = bytes.stream().mapToInt(Integer::intValue).average().getAsDouble();
        double std = Math.sqrt(bytes.stream()
                .map(i -> i - mean)
                .map(i -> i*i)
                .mapToDouble(i -> i).average().getAsDouble());
        int min = bytes.stream().mapToInt(Integer::intValue).min().getAsInt();
        int max = bytes.stream().mapToInt(Integer::intValue).max().getAsInt();
        System.out.println("Average bytes per " + type + ": " + mean);
        System.out.println("Standard deviation: " + std);
        System.out.println("Minimum bytes of a " + type + ": " + min);
        System.out.println("Maximum bytes of a " + type + ": " + max);
    }

    @Test
    public void printMsgSizes() {
        List<Integer> msgBytes = new ArrayList<>(), payloadBytes = new ArrayList<>();
        String src = "10", dst = "10", nodeID = "125", rtnID = "15", devID = "250", printStr;
        int srcTS = 100, srcSeqNo = 100, dstTS = 1000, epochNo = 10, rtnSeqNo = 2, reqSeqNo = 15;
        // DeviceState reading = new DeviceState("0.7");
        List<String> entitiesIDs = new ArrayList<>();
        for (int i = 25; i < 200; i += 25) {
            entitiesIDs.add(String.valueOf(i));
        }
        LeaderElectionCause leaderElectionCause = LeaderElectionCause.NEW_EPOCH;
        Map<String, Object> devState = new HashMap<>();
        for (String entityID: entitiesIDs) {
            devState.put(entityID, new DeviceLock());
        }
        Map<String, Object> rtnState = new HashMap<>();
        Map<String, Integer> seqNos = new HashMap<>();
        for (String entityID: entitiesIDs) {
            rtnState.put(entityID, new HashMap<Integer, RoutineStage>());
            seqNos.put(entityID, 10);
        }
        KGroup kgroup = new KGroup(
            "0", entitiesIDs, null, KGroupType.DEVICE, 1, 3, null, null, null, null
        );
        List<Message> waitMsgs = new ArrayList<>();
        MessagePayload payload;
        Message msg;
        for (KGroupType kGroupType: KGroupType.values()) {
            for (MessageType type: MessageType.values()) {
                switch (type) {
                case KGROUP_STATE_DISTRIBUTION:
                    if (kGroupType == KGroupType.DEVICE) {
                        payload = new KGroupStateDistributionMessagePayload(kgroup);
                        printStr = "DeviceKGroupStateDistributionMessage";
                    }
                    else {
                        payload = new KGroupStateDistributionMessagePayload(kgroup);
                        printStr = "RoutineKGroupStateDistributionMessage";
                    }
                    break;
                case LOCAL_KGROUP_STATE:
                    if (kGroupType == KGroupType.DEVICE) {
                        payload = new LocalKGroupStateMessagePayload(kgroup, srcSeqNo);
                        printStr = "LocalDeviceKGroupStateMessage";
                    }
                    else {
                        payload = new LocalKGroupStateMessagePayload(kgroup, srcSeqNo);
                        printStr = "LocalRoutineKGroupStateMessage";
                    }
                    break;
                case NODE_RECRUITEMENT_REQUEST:
                    if (kGroupType == KGroupType.ROUTINE) {
                        payload = new NodeRecruitmentRequestMessagePayload(epochNo, kGroupType, rtnState);
                        printStr = "RoutineKGroupNodeRecruitmentRequestMessage";
                    }
                    else {
                        payload = new NodeRecruitmentRequestMessagePayload(epochNo, kGroupType, devState);
                        printStr = "DeviceKGroupNodeRecruitmentRequestMessage";
                    }
                    break;
                case KGROUP_STATE:
                    if (kGroupType == KGroupType.DEVICE) {
                        payload = new KGroupStateMessagePayload(kgroup, srcSeqNo, waitMsgs);
                        printStr = "DeviceKGroupStateMessage";
                    }
                    else {
                        payload = new KGroupStateMessagePayload(kgroup, srcSeqNo, waitMsgs);
                        printStr = "RoutineKGroupStateMessage";
                    }
                    break;
                default:
                    payload = null;
                    printStr = "default";
                    break;
                }
                if (payload == null)
                    continue;
                payloadBytes.add(payload.getByteSize());
                msg = new Message(src, srcTS, srcSeqNo, dst, dstTS, type, payload);
                msgBytes.add(msg.getByteSize());
                System.out.println(printStr + " byte size: " + msg.getByteSize()
                                  + ", payload byte size: " + payload.getByteSize());
                waitMsgs.add(msg);
            }
        }
        KGroupType kGroupType = KGroupType.DEVICE;
        for (MessageType type: MessageType.values()) {
            switch (type) {
            case DEVICE_KGROUP_LEADER_INFO:
                payload = new DeviceKGroupLeaderInfoMessagePayload(epochNo, entitiesIDs, srcSeqNo, nodeID);
                printStr = "DeviceKGroupLeaderInfoMessage";
                break;
            case DEVICE_STATE:
                payload = new DeviceStateCheckMessagePayload();
                printStr = "DeviceStateCheckMessage";
                break;
            case DEVICE_STATE_CHECK:
                payload = new DeviceStateMessagePayload("0", new DeviceState("val0"), srcSeqNo);
                printStr = "DeviceStateMessage";
                break;
            case ELECTED:
                payload = new ElectedMessagePayload(epochNo, kGroupType, entitiesIDs, leaderElectionCause);
                printStr = "ElectedMessage";
                break;
            case ELECTED_ACK:
                payload = new ElectedAckMessagePayload(kgroup, srcSeqNo);
                printStr = "ElectedAckMessage";
                break;
            case ELECTION:
                payload = new ElectionMessagePayload(epochNo, kGroupType, entitiesIDs, leaderElectionCause);
                printStr = "ElectionMessage";
                break;
            case ELECTION_ACK:
                payload = new ElectionAckMessagePayload(epochNo, kGroupType, entitiesIDs, srcSeqNo);
                printStr = "ElectedAckMessage";
                break;
            case KGROUP_STATE_ACK:
                payload = new KGroupStateAckMessagePayload(kgroup, srcSeqNo);
                printStr = "KGroupStateAckMessage";
                break;
            case KGROUP_STATE_REQUEST:
                payload = new KGroupStateRequestMessagePayload(kgroup, leaderElectionCause);
                printStr = "KGroupStateRequestMessage";
                break;
            case LOCAL_KGROUP_STATE_REQUEST:
                payload = new LocalKGroupStateRequestMessagePayload(epochNo, kGroupType, entitiesIDs);
                printStr = "LocalKGroupStateRequestMessage";
                break;
            case LOCKED:
                payload = new LockedMessagePayload(epochNo, entitiesIDs.subList(0, 1), devID, rtnSeqNo);
                printStr = "LockedMessage";
                break;
            case LOCKED_ACK:
                payload = new LockedAckMessagePayload(epochNo, devID, srcSeqNo);
                printStr = "LockedAckMessage";
                break;
            case LOCKED_QUORUM:
                payload = new LockedQuorumMessagePayload(epochNo, entitiesIDs.subList(0, 1), rtnID, reqSeqNo, rtnSeqNo);
                printStr = "LockedQuorumMessage";
                break;
            case LOCKED_QUORUM_ACK:
                payload = new LockedQuorumAckMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo, rtnID, rtnSeqNo, reqSeqNo);
                printStr = "LockedQuorumAckMessage";
                break;
            case LOCK_RELEASED:
                payload = new LockReleasedMessagePayload(epochNo, entitiesIDs.get(0), devID, rtnSeqNo);
                printStr = "LockReleasedMessage";
                break;
            case LOCK_RELEASED_ACK:
                payload = new LockReleasedAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "LockReleasedMessage";
                break;
            case LOCK_RELEASE_QUORUM:
                payload = new LockReleaseQuorumMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo, reqSeqNo);
                printStr = "LockReleaseQuorumMessage";
                break;
            case LOCK_RELEASE_QUORUM_ACK:
                payload = new LockReleaseQuorumAckMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo, rtnID, reqSeqNo, rtnSeqNo);
                printStr = "LockReleaseQuorumAckMessage";
                break;
            case LOCK_RELEASE_REQUEST:
                payload = new LockReleaseRequestMessagePayload(epochNo, entitiesIDs.subList(0, 1), rtnID, rtnSeqNo);
                printStr = "LockReleaseRequestMessage";
                break;
            case LOCK_RELEASE_REQUEST_ACK:
                payload = new LockReleaseRequestAckMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo);
                printStr = "LockReleaseRequestAckMessage";
                break;
            case LOCK_REQUEST:
                payload = new LockRequestMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo);
                printStr = "LockRequestMessage";
                break;
            case LOCK_REQUESTED:
                payload = new LockRequestedMessagePayload(epochNo, entitiesIDs.subList(0, 1), rtnSeqNo, devID);
                printStr = "LockRequestedMessage";
                break;
            case LOCK_REQUESTED_ACK:
                payload = new LockRequestedAckMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo);
                printStr = "LockRequestedAckMessage";
                break;
            case LOCK_REQUEST_ACK:
                payload = new LockRequestAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "LockRequestAckMessage";
                break;
            case LOCK_REQUEST_QUORUM:
                payload = new LockRequestQuorumMessagePayload(
                    epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo, reqSeqNo
                );
                printStr = "LockRequestQuorumMessage";
                break;
            case LOCK_REQUEST_QUORUM_ACK:
                payload = new LockRequestQuorumAckMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo, rtnID, reqSeqNo, rtnSeqNo);
                printStr = "LockRequestQuorumAckMessage";
                break;
            case NODE_FAILURE:
                payload = new NodeFailureMessagePayload(kGroupType, entitiesIDs, nodeID);
                printStr = "NodeFailureMessage";
                break;
            case NODE_FAILURE_ACK:
                payload = new NodeFailureAckMessagePayload(srcSeqNo);
                printStr = "NodeFailureAckMessage";
                break;
            case NODE_RECRUITED:
                payload = new NodeRecruitedMessagePayload(epochNo, kGroupType, entitiesIDs, srcSeqNo);
                printStr = "NodeRecruitedMessage";
                break;
            case NOT_OLD_LEADER:
                payload = new NotOldLeaderMessagePayload(kgroup, srcSeqNo);
                printStr = "NotOldLeaderMessage";
                break;
            case PLOCK_CANCEL:
                payload = new PLockCancelMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo);
                printStr = "PLockCancelMessage";
                break;
            case PLOCK_CANCELLED:
                payload = new PLockCancelledMessagePayload(epochNo, entitiesIDs.get(0), rtnSeqNo, devID);
                printStr = "PLockCancelledMessage";
                break;
            case PLOCK_CANCELLED_ACK:
                payload = new PLockCancelledAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "PLockCancelledAckMessage";
                break;
            case PLOCK_CANCEL_ACK:
                payload = new PLockCancelAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "PLockCancelAckMessage";
                break;
            case PLOCK_CANCEL_QUORUM:
                payload = new PLockCancelQuorumMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo, reqSeqNo);
                printStr = "PLockCancelQuorumMessage";
                break;
            case PLOCK_CANCEL_QUORUM_ACK:
                payload = new PLockCancelQuorumAckMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo, rtnID, reqSeqNo, rtnSeqNo);
                printStr = "PLockCancelQuorumAckMessage";
                break;
            case PLOCK_FAILED:
                payload = new PLockFailMessagePayload(epochNo, rtnID, rtnSeqNo, devID);
                printStr = "PLockFailMessage";
                break;
            case PLOCK_FAILED_ACK:
                payload = new PLockFailAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "PLockFailAckMessage";
                break;
            case PLOCK_LOCK:
                payload = new PLockLockMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo, reqSeqNo);
                printStr = "PLockLockMessage";
                break;
            case PLOCK_LOCK_ACK:
                payload = new PLockLockAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "PLockLockAckMessage";
                break;
            case PLOCK_REQUEST:
                payload = new LockRequestMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo);
                printStr = "LockRequestMessage";
                break;
            case PLOCK_REQUESTED:
                payload = new PLockRequestedMessagePayload(epochNo, rtnID, rtnSeqNo, reqSeqNo, devID);
                printStr = "PLockRequestedMessage";
                break;
            case PLOCK_REQUESTED_ACK:
                payload = new PLockRequestedAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo, rtnID, rtnSeqNo);
                printStr = "PLockRequestedAckMessage";
                break;
            case PLOCK_REQUEST_ACK:
                payload = new PLockRequestAckMessagePayload(epochNo, rtnID, srcSeqNo);
                printStr = "PLockRequestAckMessage";
                break;
            case PLOCK_REQUEST_QUORUM:
                payload = new PLockRequestQuorumMessagePayload(epochNo, entitiesIDs.get(0), rtnID, rtnSeqNo, reqSeqNo);
                printStr = "PLockRequestQuorumMessage";
                break;
            case PLOCK_REQUEST_QUORUM_ACK:
                payload = new PLockRequestQuorumAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "PLockRequestQuorumAckMessage";
                break;
            case ROUTINE_KGROUP_LEADER_INFO:
                payload = new RoutineKGroupLeaderInfoMessagePayload(epochNo, entitiesIDs.subList(0, 1), srcSeqNo, nodeID);
                printStr = "RoutineKGroupLeaderInfoMessage";
                break;
            case TRIGGER_QUORUM:
                payload = new TriggerQuorumMessagePayload(epochNo, entitiesIDs.get(0), rtnSeqNo);
                printStr = "TriggerQuorumMessage";
                break;
            case TRIGGER_QUORUM_ACK:
                payload = new TriggerQuorumAckMessagePayload(epochNo, entitiesIDs.get(0), srcSeqNo);
                printStr = "TriggerQuorumAckMessage";
                break;
            default:
                payload = null;
                printStr = "default";
                break;
            }
            if (payload == null)
                continue;
                payloadBytes.add(payload.getByteSize());
                msg = new Message(src, srcTS, srcSeqNo, dst, dstTS, type, payload);
                msgBytes.add(msg.getByteSize());
            System.out.println(printStr + " byte size: " + msg.getByteSize()
                              + ", payload byte size: " + payload.getByteSize());
            waitMsgs.add(msg);
        }
        printMsgSizesHelper(msgBytes, "msg");
        printMsgSizesHelper(payloadBytes, "payload");
    }
}