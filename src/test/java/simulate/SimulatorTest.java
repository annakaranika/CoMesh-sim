package simulate;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kgroup.*;

import org.junit.jupiter.api.Test;

public class SimulatorTest {
        // Default settings
        private static boolean debug = false;
        private static DevClusterPolicy devClusterPolicy = DevClusterPolicy.LOCALITY;
        private static KGroupSelectionPolicy kGroupPolicy = KGroupSelectionPolicy.LSHMIX;
        private static LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.LSH_SMALLEST_HASH;
        private static LockStrategy lockStrategy = LockStrategy.SERIAL;
        private static int routineNo = 1, deviceNo = 250, seedNo = 10;
        private static int F = 2, K = 2 * F + 1, epochLength = 200 /* time units */;
        private static int end = 300, minTriggerOffset = 1000, devMntrPeriod = 10;
        private static int hopOWD = 1 /* time units */, bwCap = 625000 /* 625 KBps ~= 5Mbps */ ;
        private static float waitCoef = 2;
        private static int deviceKGroupRange = 25, routineKGroupRange = 1, wirelessRange = 1;
        private static int lowerNodeLimit = 20, upperNodeLimit = 50;
        private static int k = 2, l = 2, r = 4; // LSH params
        private static double mean = 0., std = 1.0, randomness = 0.5; // LSH params
        private static String deviceTopologyShape = "grid", deviceTopologyDims = "5,5,10";
        private static String nodePercentage = "0.8", nodeChurnProbability = "0.0";
        private static String nodeScheduleDistribution = "uniform", nodeScheduleClusters = "0";
        private static String routineDeviceMapDistribution = "uniform";
        private static String avgDevicesPerRoutine = "5", deviceClusters = "";
        private static String routineLengthDistribution = "uniform", maxRoutineLength = "5";
        private static String routineScheduleDistribution = "uniform", routineScheduleClusters = "";

        public static String getDevicesLocationFilename(
                        int deviceNo, String deviceTopologyShape, String deviceTopologyDims) {
                return "workloads/device_location/device_location_d" +
                                String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                                deviceTopologyDims + ".txt";
        }

        public static String getDevicesTopologyFilename(
                        int deviceNo, String deviceTopologyShape, String deviceTopologyDims) {
                return "workloads/device_topology/device_topology_d" +
                                String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                                deviceTopologyDims + ".txt";
        }

        public static String getDevicesClustersFilename(
                        int deviceNo, String deviceTopologyShape, String deviceTopologyDims) {
                return "workloads/device_clusters/device_clusters_d" +
                                String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                                deviceTopologyDims + ".txt";
        }

        public static String getNodesListFilename(
                        int deviceNo, String nodePercentage, int seed) {
                return "workloads/node_list/node_list_d" + String.valueOf(deviceNo) +
                                "_np" + nodePercentage + "_s" + String.valueOf(seed) + ".txt";
        }

        public static String getNodesScheduleFilename(
                        int deviceNo, String nodePercentage, String nodeChurnProbability,
                        String nodeScheduleDistribution, String nodeScheduleClusters, int end,
                        int seed) {
                return "workloads/node_schedule/node_schedule_d" + String.valueOf(deviceNo) +
                                "_np" + nodePercentage + "_" + "cp" + nodeChurnProbability +
                                "_" + nodeScheduleDistribution +
                                (nodeScheduleDistribution.equals("clusters") ? nodeScheduleClusters : "")
                                + "_e" + String.valueOf(end) + "_s" + String.valueOf(seed) + ".txt";
        }

        public static String getRoutinesDevicesMapFilename(
                        int routineNo, int deviceNo, String avgDevicesPerRoutine,
                        String routineDeviceMapDistribution, String deviceClusters, int seed) {
                return "workloads/routine_device_map/routine_device_map_r" +
                                String.valueOf(routineNo) + "_d" + String.valueOf(deviceNo) +
                                "_a" + avgDevicesPerRoutine + "_" + routineDeviceMapDistribution +
                                (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "") +
                                "_s" + String.valueOf(seed) + ".txt";
        }

        public static String getRoutinesLengthsFilename(
                        int routineNo, String maxRoutineLength,
                        String routineLengthDistribution, int seed) {
                return "workloads/routine_lengths/routine_lengths_r" +
                                String.valueOf(routineNo) + "_m" + maxRoutineLength + "_" +
                                routineLengthDistribution + "_s" + String.valueOf(seed) + ".txt";
        }

        public static String getRoutinesScheduleFilename(
                        int routineNo, int end, String routineScheduleDistribution,
                        String routineScheduleClusters, int seed) {
                return "workloads/routine_schedule/routine_schedule_r" +
                                String.valueOf(routineNo) + "_e" + String.valueOf(end) + "_" +
                                routineScheduleDistribution +
                                (routineScheduleDistribution.equals("clusters") ? routineScheduleClusters : "") +
                                "_s" + String.valueOf(seed) + ".txt";
        }

        public static String getOutputFilename(
                        TestType testType, int deviceNo, String deviceTopologyShape, String devClusterPolicy,
                        String deviceTopologyDims, String nodePercentage, String nodeChurnProbability,
                        String nodeScheduleDistribution, String nodeScheduleClusters,
                        int K, String kGroupPolicy, String electionPolicy, String lockStrategy,
                        int epochLength, int end, int routineNo,
                        String avgDevicesPerRoutine, String routineDeviceMapDistribution,
                        String deviceClusters, String maxRoutineLength, String routineLengthDistribution,
                        String routineScheduleDistribution, String routineScheduleClusters,
                        int wirelessRange, int seed) {
                String testName = "";
                switch (testType) {
                        case BALANCING:
                                testName = "balance";
                                break;
                        case BANDWIDTH:
                                testName = "bandwidth";
                                break;
                        case BANDWIDTH_BG:
                                testName = "bandwidth_bg";
                                break;
                        case CLIENT_DELAY:
                                testName = "client_delay";
                                break;
                        case DELAY_BREAKDOWN:
                                testName = "delay_bd";
                                break;
                        case INKGROUP_BENCHMARK_WO_FAILURE:
                                testName = "in_kgroup";
                                break;
                        case INKGROUP_BENCHMARK_W_FAILURE:
                                testName = "in_kgroup_wf";
                                break;
                        case INKGROUP_FAILURE_SCHEDULE:
                                testName = "failure_tl";
                                break;
                        case LOCKING_TIME:
                                break;
                        case SYNC_DELAY:
                                testName = "sync_delay";
                                break;
                        case CHURN:
                                testName = "churn_tl";
                                break;
                        default:
                                break;

                }

                return "outputs/" + testName + "/" + testName + "_d" +
                                String.valueOf(deviceNo) + "_" + deviceTopologyShape +
                                deviceTopologyDims + "_" + devClusterPolicy + "_np" + nodePercentage + "_cp" +
                                nodeChurnProbability + "_" + nodeScheduleDistribution +
                                (nodeScheduleDistribution.equals("clusters") ? nodeScheduleClusters : "") +
                                "_k" + String.valueOf(K) + "_" + kGroupPolicy +
                                "_" + electionPolicy + "_" + lockStrategy +
                                "_l" + String.valueOf(epochLength) +
                                "_e" + String.valueOf(end) + "_r" + String.valueOf(routineNo) +
                                (routineNo != 0 ? "_a" + avgDevicesPerRoutine + "_" + routineDeviceMapDistribution +
                                                (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "")
                                                +
                                                "_m" + maxRoutineLength + "_" + routineLengthDistribution +
                                                (routineScheduleDistribution.equals("clusters")
                                                                ? routineScheduleClusters
                                                                : "")
                                                : "")
                                +
                                "_w" + wirelessRange + "_" + String.valueOf(seed) + ".csv";
        }

        public static String getOutputFilename(
                        TestType testType, int deviceNo, String deviceTopologyShape, String devClusterPolicy,
                        String deviceTopologyDims, String nodePercentage, String nodeChurnProbability,
                        String nodeScheduleDistribution, String nodeScheduleClusters,
                        int K, String kGroupPolicy, String electionPolicy, String lockStrategy,
                        int epochLength, int end, int routineNo,
                        String avgDevicesPerRoutine, String routineDeviceMapDistribution,
                        String deviceClusters, String maxRoutineLength, String routineLengthDistribution,
                        String routineScheduleDistribution, String routineScheduleClusters,
                        int wirelessRange, FailureAtStage stage, int seed) {
                String base_addr = getOutputFilename(
                                testType, deviceNo, deviceTopologyShape, devClusterPolicy,
                                deviceTopologyDims, nodePercentage, nodeChurnProbability,
                                nodeScheduleDistribution, nodeScheduleClusters,
                                K, kGroupPolicy, electionPolicy, lockStrategy, epochLength, end, routineNo,
                                avgDevicesPerRoutine, routineDeviceMapDistribution,
                                deviceClusters, maxRoutineLength, routineLengthDistribution,
                                routineScheduleDistribution, routineScheduleClusters, wirelessRange, seed);
                int idx = base_addr.lastIndexOf('_');
                if (stage.equals(FailureAtStage.BEFORE_EPOCH_CHANGE_MEM)) {
                        return base_addr.substring(0, idx) + "_bf" + base_addr.substring(idx);
                } else if (stage.equals(FailureAtStage.DURING_EPOCH_CHANGE_MEM)) {
                        return base_addr.substring(0, idx) + "_dr" + base_addr.substring(idx);
                } else if (stage.equals(FailureAtStage.BEFORE_EPOCH_CHANGE_LEADER)) {
                        return base_addr.substring(0, idx) + "_bl" + base_addr.substring(idx);
                } else if (stage.equals(FailureAtStage.DURING_EPOCH_CHANGE_LEADER)) {
                        return base_addr.substring(0, idx) + "_dl" + base_addr.substring(idx);
                } else {
                        return base_addr.substring(0, idx) + "_no" + base_addr.substring(idx);
                }
        }

        public static double[] lshMetrics(List<Double> data, String type) {
                double mean = 0.;
                double std = 0.;
                int cnt = 0;
                for (double d : data) {
                        mean += d;
                        cnt += 1;
                }
                mean /= cnt;
                for (int i = 0; i < cnt; i++) {
                        std = std + Math.pow((data.get(i) - mean), 2);
                }
                double sq = std / cnt;
                std = Math.sqrt(sq);
                // System.out.format(type + ": %6f std: %6f", mean, std);
                // System.out.println();
                return new double[] { mean, std };
        }

        public String escapeSpecialCharacters(String data) {
                String escapedData = data.replaceAll("\\R", " ");
                if (data.contains(",") || data.contains("\"") || data.contains("'")) {
                        data = data.replace("\"", "\"\"");
                        escapedData = "\"" + data + "\"";
                }
                return escapedData;
        }

        public String convertToCSV(String[] data) {
                return Stream.of(data).map(this::escapeSpecialCharacters).collect(Collectors.joining(" "));
        }

        @Test
        public void debuggingSimulatorTest() {
                // debug = true;
                TestType testType = TestType.CHURN;
                int seed = 0;
                F = 1;
                K = 2 * F + 1;
                routineNo = 100;
                deviceTopologyDims = "5,5,2";
                deviceNo = Arrays.stream(deviceTopologyDims.split(","))
                                .map(Integer::parseInt)
                                .reduce(1, (a, b) -> a * b);
                end = 50 * epochLength - 1;
                nodeChurnProbability = "0.2";
                nodeScheduleDistribution = "clusters";
                nodeScheduleClusters = "3";
                LSHParams hashParams = new LSHParams(seed, k, l, r, mean, std, randomness);

                Simulator.createWorkloads(
                                seed,
                                deviceTopologyShape, deviceTopologyDims, deviceKGroupRange,
                                deviceNo, nodePercentage, lowerNodeLimit, upperNodeLimit,
                                nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                                routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                                routineLengthDistribution, maxRoutineLength,
                                routineScheduleDistribution, routineScheduleClusters);

                String devicesLocationFilename = getDevicesLocationFilename(
                                deviceNo, deviceTopologyShape, deviceTopologyDims);
                String devicesTopologyFilename = getDevicesTopologyFilename(
                                deviceNo, deviceTopologyShape, deviceTopologyDims);
                String devicesClustersFilename = getDevicesClustersFilename(
                                deviceNo, deviceTopologyShape, deviceTopologyDims);
                String nodesListFilename = getNodesListFilename(
                                deviceNo, nodePercentage, seed);
                String nodesScheduleFilename = getNodesScheduleFilename(
                                deviceNo, nodePercentage, nodeChurnProbability,
                                nodeScheduleDistribution, nodeScheduleClusters, end, seed);

                String routinesDevicesMapFilename = getRoutinesDevicesMapFilename(
                                routineNo, deviceNo, avgDevicesPerRoutine,
                                routineDeviceMapDistribution, deviceClusters, seed);
                String routinesLengthsFilename = getRoutinesLengthsFilename(
                                routineNo, maxRoutineLength, routineLengthDistribution, seed);
                String routinesScheduleFilename = getRoutinesScheduleFilename(
                                routineNo, end, routineScheduleDistribution,
                                routineScheduleClusters, seed);

                String outputFilename = getOutputFilename(
                                testType, deviceNo, deviceTopologyShape, devClusterPolicy.name, deviceTopologyDims,
                                nodePercentage, nodeChurnProbability, nodeScheduleDistribution,
                                nodeScheduleClusters, K, kGroupPolicy.name, electionPolicy.name, lockStrategy.name,
                                epochLength, end, routineNo,
                                avgDevicesPerRoutine, routineDeviceMapDistribution, deviceClusters,
                                maxRoutineLength, routineLengthDistribution,
                                routineScheduleDistribution, routineScheduleClusters, wirelessRange, seed);

                Simulator simulator = new Simulator();

                simulator.initializeTopology(
                                deviceTopologyDims, devicesLocationFilename, devicesTopologyFilename,
                                nodesListFilename, nodesScheduleFilename, hopOWD, bwCap, waitCoef, debug);

                simulator.initializeRoutines(
                                routineNo, routinesDevicesMapFilename, routinesLengthsFilename);

                simulator.initializeRtnEvents(routinesScheduleFilename, testType);

                simulator.initializeEvents(nodesScheduleFilename, testType);

                simulator.initializeDevClusters(
                                devClusterPolicy, devicesClustersFilename, deviceKGroupRange);

                simulator.initializeKGroupManagers(
                                F, K, epochLength, minTriggerOffset, devMntrPeriod, devicesClustersFilename,
                                devClusterPolicy,
                                deviceKGroupRange, routineKGroupRange, end, electionPolicy, lockStrategy,
                                kGroupPolicy, hashParams, debug);

                simulator.initializeLSH(
                                kGroupPolicy, hashParams, F, K,
                                deviceTopologyDims, devicesLocationFilename, devicesTopologyFilename);

                simulator.simulateCoMesh(
                                epochLength, kGroupPolicy, end, testType, outputFilename, debug);
        }

        @Test
        public void siebelSimulatorTest() {
                debug = false;
                TestType testType = TestType.SYNC_DELAY;
                F = 2;
                K = 2 * F + 1;
                deviceNo = Arrays.stream(deviceTopologyDims.split(","))
                                .map(Integer::parseInt)
                                .reduce(1, (a, b) -> a * b);
                nodePercentage = "0.2";
                epochLength = 1000;
                end = 10 * epochLength - 1;
                nodeChurnProbability = "0.0";
                nodeScheduleDistribution = "clusters";
                nodeScheduleClusters = "3";

                String devicesTopologyFilename, devicesClustersFilename, nodesListFilename;
                LSHParams hashParams;

                // ============MOTION LIGHT EXPERIMENTS (285 devices, 163
                // routines)=================
                routineNo = 163;
                deviceTopologyDims = "15,19";
                String devicesLocationFilename = "workloads/device_location/device_location_siebel_d285.txt";
                String devicesSchedulesFilename = "workloads/device_schedule/device_schedule_siebel_d285.txt";
                String nodesScheduleFilename = "workloads/node_schedule/node_schedule_d285.txt";
                String routinesDirName = "workloads/routines/routines_163";

                // ===========TEMPERATURE EXPERIMENTS (708 devices, 545
                // routines)====================
                // routineNo = 545;
                // deviceTopologyDims = "59,12";
                // String devicesLocationFilename =
                // "workloads/device_location/device_locations_exp2.txt";
                // String devicesSchedulesFilename =
                // "workloads/device_schedule/device_schedule_siebel_exp2.txt";
                // String nodesScheduleFilename =
                // "workloads/node_schedule/node_schedule_d708.txt";
                // String routinesDirName = "workloads/routines/routines_545";

                int[] ranges = { 40, 100, 120 };
                String[] nodePercentages = { "0.4" }; // , "0.2"};

                for (int seed = 0; seed < seedNo; seed++) {
                        hashParams = new LSHParams(seed, k, l, r, mean, std, randomness);
                        devicesClustersFilename = "workloads/device_clusters/device_clusters_d285_grid15,19_" + seed
                                        + ".txt";
                        // devicesClustersFilename =
                        // "workloads/device_clusters/device_clusters_d708_grid59,12_" + seed + ".txt";

                        for (String np : nodePercentages) {
                                nodesListFilename = "workloads/node_list/node_list_d285_np" + np + "_s" + seed + ".txt";
                                // "workloads/node_list/node_list_d708_np" + np + "_s" + seed + ".txt";
                                for (Integer range : ranges) {
                                        devicesTopologyFilename = "workloads/device_topology/device_topology_d285_"
                                                        + range + ".txt";
                                        // "workloads/device_topology/device_topology_d708_" + range + ".txt";

                                        System.out.println("Range: " + range + ", NP: " + np + ", seed: " + seed);

                                        String outputFilename = getOutputFilename(
                                                        testType, deviceNo, deviceTopologyShape, devClusterPolicy.name,
                                                        deviceTopologyDims,
                                                        np, nodeChurnProbability, nodeScheduleDistribution,
                                                        nodeScheduleClusters, K, kGroupPolicy.name, electionPolicy.name,
                                                        lockStrategy.name,
                                                        epochLength, end, routineNo,
                                                        avgDevicesPerRoutine, routineDeviceMapDistribution,
                                                        deviceClusters,
                                                        maxRoutineLength, routineLengthDistribution,
                                                        routineScheduleDistribution, routineScheduleClusters, range,
                                                        seed);

                                        File f = new File(outputFilename);
                                        if (f.exists())
                                                continue;

                                        Simulator simulator = new Simulator();

                                        simulator.initializeTopology(
                                                        deviceTopologyDims, devicesLocationFilename,
                                                        devicesTopologyFilename,
                                                        nodesListFilename, nodesScheduleFilename, hopOWD, bwCap,
                                                        waitCoef, debug);

                                        simulator.initializeRoutines(
                                                        routinesDirName, WorkloadOption.SIEBEL);

                                        simulator.initializeEvents(nodesScheduleFilename, testType);

                                        simulator.initializeDevEvents(devicesSchedulesFilename, testType);

                                        simulator.initializeDevClusters(
                                                        devClusterPolicy, devicesClustersFilename, deviceKGroupRange);

                                        simulator.initializeKGroupManagers(
                                                        F, K, epochLength, minTriggerOffset, devMntrPeriod,
                                                        devicesClustersFilename,
                                                        devClusterPolicy,
                                                        deviceKGroupRange, routineKGroupRange, end, electionPolicy,
                                                        lockStrategy,
                                                        kGroupPolicy, hashParams, debug);

                                        simulator.initializeLSH(
                                                        kGroupPolicy, hashParams, F, K,
                                                        deviceTopologyDims, devicesLocationFilename,
                                                        devicesTopologyFilename);

                                        simulator.simulateCoMesh(
                                                        epochLength, kGroupPolicy, end, testType, outputFilename,
                                                        debug);
                                }
                        }
                }
        }
}
