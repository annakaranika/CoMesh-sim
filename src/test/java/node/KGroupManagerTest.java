package node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import kgroup.KGroupSelectionPolicy;
import kgroup.LockStrategy;
import kgroup.Membership;

import org.junit.jupiter.api.Test;

import device.KGroupManager;
import kgroup.LeaderElectionPolicy;
import network.Network;
import routine.DumbRoutine;
import routine.Routine;
import simulate.Event;

public class KGroupManagerTest {
    @Test
    public void shouldReturnCorrectChargeStatus() {
        int deviceNo = 9, routineNo = 9, routineKGroupRange = 1, hopOWD = 1, bwCap = 10000;
        float waitCoef = 2;
        Network network = new Network(
                "workloads/sample_node_topology.txt", hopOWD, bwCap, waitCoef, false);
        List<String> devIDs = new ArrayList<>();
        for (int i = 0; i < deviceNo; i++) {
            devIDs.add(String.valueOf(i));
        }
        Map<String, Routine> routines = new HashMap<>();
        for (int i = 0; i < routineNo; i++) {
            if (i <= (i + deviceNo / routineNo) % deviceNo) {
                routines.put(String.valueOf(i),
                        new DumbRoutine(null, devIDs.subList(i, (i + deviceNo / routineNo) % deviceNo)));
            } else {
                List<String> touchedDevIDs = new ArrayList<>();
                touchedDevIDs.addAll(devIDs.subList(0, (i + deviceNo / routineNo) % deviceNo));
                touchedDevIDs.addAll(devIDs.subList(i, deviceNo));
                routines.put(String.valueOf(i), new DumbRoutine(null, touchedDevIDs));
            }
        }
        List<List<String>> deviceClusters = new ArrayList<>();
        deviceClusters.add(devIDs);
        KGroupManager kGroupManager = new KGroupManager(
                "0", 10, 1, 5, 50, 10, 10,
                devIDs, devIDs,
                routines,
                deviceClusters, routineKGroupRange,
                network, 0, 0,
                new TreeMap<Integer, List<Event>>(), new HashMap<String, Membership>(),
                LeaderElectionPolicy.SMALLEST_HASH, LockStrategy.SERIAL, KGroupSelectionPolicy.RANDOM);

        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add("5");
        assertEquals(entitiesIDs, kGroupManager.getDevKGrp(entitiesIDs).entitiesIDs);
    }

    @Test
    public void electionTest() {
        int F = 1, K = 2 * F + 1, epochLength = 1 * 15 * 1000 /* time units */, devMntrPeriod = 5000,
                deviceNo = 9, routineNo = 9, routineKGroupRange = 1, hopOWD = 1, bwCap = 10000;
        float waitCoef = 2;

        List<String> devIDs = new ArrayList<>();
        for (int i = 0; i < deviceNo; i++) {
            devIDs.add(String.valueOf(i));
        }

        Map<String, Routine> routines = new HashMap<>();
        for (int i = 0; i < routineNo; i++) {
            if (i <= (i + deviceNo / routineNo) % deviceNo) {
                routines.put(String.valueOf(i),
                        new DumbRoutine(null, devIDs.subList(i, (i + deviceNo / routineNo) % deviceNo)));
            } else {
                List<String> touchedDevIDs = new ArrayList<>();
                touchedDevIDs.addAll(devIDs.subList(0, (i + deviceNo / routineNo) % deviceNo));
                touchedDevIDs.addAll(devIDs.subList(i, deviceNo));
                routines.put(String.valueOf(i), new DumbRoutine(null, touchedDevIDs));
            }
        }

        Network network = new Network(
                "workloads/sample_node_topology.txt", hopOWD, bwCap, waitCoef, false);
        List<List<String>> deviceClusters = new ArrayList<>();
        deviceClusters.add(devIDs);

        Map<String, KGroupManager> kGroupManagers = new HashMap<>();
        for (String nodeID : network.getMembershipList()) {
            kGroupManagers.put(
                    nodeID,
                    new KGroupManager(
                            nodeID, 10, F, K, epochLength, 10, devMntrPeriod,
                            devIDs, devIDs, routines, deviceClusters, routineKGroupRange,
                            network, 0, 0, new TreeMap<Integer, List<Event>>(),
                            new HashMap<String, Membership>(),
                            LeaderElectionPolicy.SMALLEST_HASH, LockStrategy.SERIAL,
                            KGroupSelectionPolicy.RANDOM));
        }

        try {
            Thread.sleep(10);
        } catch (Exception e) {
            System.out.println(e);
        }

        System.out.println("Starting to remove node 4");
        network.nodeFailed("4");
        kGroupManagers.remove("4");
        for (String nodeID : network.getMembershipList()) {
            if (!nodeID.equals("4")) {
                kGroupManagers.get(nodeID).nodeFailureDetected("4", true);
            }
        }
        System.out.println("Removed node 4");

        try {
            Thread.sleep(100);
        } catch (Exception e) {
            System.out.println(e);
        }

        while (network.getMembershipList().size() != 0) {
        }
    }
}
