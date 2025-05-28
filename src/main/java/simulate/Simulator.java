package simulate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.*;
import java.util.Map.Entry;

import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.io.*;

import device.CentralizedManager;
import device.IoTDevice;
import device.KGroupManager;
import device.Device;
import kgroup.*;
import metric.KGroupMetric;
import metric.NodeRole;
import metric.RoutineMetricSingleton;
import network.Network;
import routine.*;
import routine.command.*;
import routine.statement.*;
import routine.statement.condition.*;

public class Simulator {
    public static String helpText = "Help:\nTo run, type: mvn exec:java\n"
            + "You can set arguments by also typing: "
            + "-Dexec.args=\"<arg0> ... <argN>\"";
    public static Runtime run = Runtime.getRuntime();

    private Network network;
    private List<String> devIDs, nodesIDs = new ArrayList<>();
    private List<List<String>> devClusters = new ArrayList<>();
    private Map<String, Integer> nodesLimits = new HashMap<>();
    private Map<String, Routine> routines = new HashMap<>();
    private Map<String, Device> nodes = new HashMap<>();
    private Map<String, Membership> membershipList = new HashMap<>();
    private SortedMap<Integer, List<Event>> events = new TreeMap<>();

    public Simulator() {
        KGroup.clearIDs();
        RoutineMetricSingleton.getInstance().clearRecords();
    }

    private static String getPythonScriptOutput(Process pr) {
        String output = "Standard output:\n", s;
        try {
            BufferedReader stdOutput = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            while ((s = stdOutput.readLine()) != null) {
                output += s + "\n";
            }

            output += "\nStandard error:\n";
            BufferedReader stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
            while ((s = stdError.readLine()) != null) {
                output += s + "\n";
            }
        } catch (IOException e) {
            System.out.println("Python script failed!");
            e.printStackTrace();
            System.exit(-1);
        }
        return output;
    }

    private static void runPythonScript(String pythonScript, String errorMsg) {
        System.out.printf("Executing '%s'\n", pythonScript);
        try {
            Process pr = run.exec(pythonScript);
            pr.waitFor();
            System.out.println(getPythonScriptOutput(pr));
        } catch (IOException e) {
            System.out.println("Device topology workload creation failed!");
            System.exit(-1);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Verify deviceNo = product(deviceTopologyDims)
     * There might be fileNotFound error if we don't check this.
     * Reason: in createDeviceTopology, the field of device number in the file name
     * is based on the product of deviceTopologyDims instead of deviceNo (which is
     * not passed in as a parameter). So if there is not such verification. The code
     * might not be able to run.
     */
    private static boolean validDevNoTopoDims(int deviceNo, String deviceTopologyDims) {
        int dim_product = Arrays.stream(deviceTopologyDims.split(","))
                .map(Integer::parseInt)
                .reduce(1, (a, b) -> a * b);

        return deviceNo == dim_product;
    }

    public static void createWorkloads(
            int seed,
            String deviceTopologyShape, String deviceTopologyDims, int deviceKGroupRange,
            int deviceNo, String nodePercentage, int lowerNodeLimit, int upperNodeLimit,
            String nodeChurnProbability, String nodeScheduleDistribution, String nodeScheduleClusters, int end,
            int routineNo, String routineDeviceMapDistribution, String avgDevicesPerRoutine, String deviceClusters,
            String routineLengthDistribution, String maxRoutineLength,
            String routineScheduleDistribution, String routineScheduleClusters) {
        String pythonScript, errorMsg;

        if (!validDevNoTopoDims(deviceNo, deviceTopologyDims)) {
            System.out.println("[ERROR] Invalid Setting. Total device number does not " +
                    "match dev topology dims.");
            System.exit(-1);
        }

        // create device topology workload
        pythonScript = "python3 workloads/scripts/createDeviceTopology.py -s "
                + deviceTopologyShape + " -d " + deviceTopologyDims;
        errorMsg = "Device topology workload creation failed!";
        runPythonScript(pythonScript, errorMsg);

        // create device clusters workload
        String devicesLocationFilename = "workloads/device_location/device_location_d"
                + String.valueOf(deviceNo) + "_" + deviceTopologyShape
                + deviceTopologyDims + ".txt";
        pythonScript = "python3 workloads/scripts/createDeviceClusters.py -s "
                + deviceTopologyShape + " -d " + deviceTopologyDims
                + " -l " + devicesLocationFilename + " -r " + deviceKGroupRange
                + " -seed " + seed;
        errorMsg = "Device topology workload creation failed!";
        runPythonScript(pythonScript, errorMsg);

        // create node list workload
        String deviceTopologySetup = deviceTopologyShape + deviceTopologyDims;
        pythonScript = "python3 workloads/scripts/createDeviceTopologyToNodeSelection.py -dn "
                + String.valueOf(deviceNo) + " -s " + deviceTopologySetup + " -np "
                + nodePercentage + " -ll " + lowerNodeLimit + " -ul " + upperNodeLimit
                + " -seed " + seed;
        errorMsg = "Node list workload creation failed!";
        runPythonScript(pythonScript, errorMsg);

        // create node schedule workload
        pythonScript = "python3 workloads/scripts/createNodeSchedule.py -dn "
                + String.valueOf(deviceNo) + " -np " + nodePercentage
                + " -cp " + nodeChurnProbability + " -d " + nodeScheduleDistribution
                + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "")
                + " -e " + String.valueOf(end) + " -s " + seed;
        errorMsg = "Node schedule workload creation failed!";
        runPythonScript(pythonScript, errorMsg);

        if (routineNo <= 0) {
            return;
        }
        // create routine to device map workload
        String devicesTopologyFilename = "workloads/device_topology/device_topology_d"
                + String.valueOf(deviceNo) + "_" + deviceTopologySetup + ".txt";
        pythonScript = "python3 workloads/scripts/createRoutinesToDevicesMap.py -r "
                + String.valueOf(routineNo) + " -d " + devicesTopologyFilename
                + " -p " + routineDeviceMapDistribution + " -a " + avgDevicesPerRoutine
                + (routineDeviceMapDistribution.equals("clusters") ? " -c " + deviceClusters : "")
                + " -s " + seed;
        errorMsg = "Routine to device map workload creation failed!";
        runPythonScript(pythonScript, errorMsg);

        // create routine lengths workload
        pythonScript = "python3 workloads/scripts/createRoutineLengths.py -r "
                + String.valueOf(routineNo) + " -d " + routineLengthDistribution
                + " -m " + maxRoutineLength + " -s " + seed;
        errorMsg = "Routine lengths workload creation failed!";
        runPythonScript(pythonScript, errorMsg);

        // create routine schedule workload
        pythonScript = "python3 workloads/scripts/createRoutineSchedule.py -r "
                + String.valueOf(routineNo) + " -d " + routineScheduleDistribution
                + (routineScheduleDistribution.equals("cluster") ? " -c " + routineScheduleClusters : "")
                + " -e " + String.valueOf(end) + " -s " + seed;
        errorMsg = "Routine schedule workload creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createInKGroupMessagesPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_msgs.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        String errorMsg = "In k-group messages' plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createInKGroupDelaysPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_in_kgroup_delays.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        String errorMsg = "In k-group delays' plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createInKGroupWFDelaysPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_in_kgroup_delays_wf.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        String errorMsg = "In k-group delays' plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createClientSyncDelayPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_client_sync_delay.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        String errorMsg = "Output client/sync delay's plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createBandwidthPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_bandwidth.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        String errorMsg = "Bandwidth's plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createBandwidthBGPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/bw_verification.py " +
                outputCsvFilename;
        String errorMsg = "Bandwidth background checking plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createBandwidthAggregatedBGPlot(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/bw_verification.py " +
                outputCsvFilename + " " + seedNo;
        String errorMsg = "Bandwidth aggregated background checking plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createBalanceCDF(String outputCsvFilename, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_balance_cdf.py " +
                outputCsvFilename + " " + String.valueOf(seedNo);
        String errorMsg = "Balance's cdf plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createBalancePDFComp() {
        String pythonScript = "python3 outputs/scripts/plot_comp_balance_PDF.py";
        String errorMsg = "Balance's pdf plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createTimelinePlot() {
        String pythonScript = "python3 outputs/scripts/plot_timeline.py";
        String errorMsg = "Failure timeline plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    public static void createDelayBreakdownPlot(String outFn, int seedNo) {
        String pythonScript = "python3 outputs/scripts/plot_delay_breakdown.py " +
                outFn + " " + seedNo;
        String errorMsg = "Delay Breakdown plot creation failed!";
        runPythonScript(pythonScript, errorMsg);
    }

    private KGroupManager getKGrpMngr(Device node) {
        if (node instanceof KGroupManager) {
            return (KGroupManager) node;
        }
        return null;
    }

    private CentralizedManager getCentralizedMngr() {
        for (Device node : nodes.values())
            if (node instanceof CentralizedManager)
                return (CentralizedManager) node;
        return null;
    }

    public void initializeTopology(
            String deviceTopologyDims, String devicesLocationFilename, String devicesTopologyFilename,
            String nodesListFilename, String nodesScheduleFilename,
            int hopOWD, int bwCap, float waitCoef, boolean debug) {
        // Device topology and network initialization
        network = new Network(devicesLocationFilename, devicesTopologyFilename, bwCap, waitCoef, debug);
        // if (debug) {
        // network.printRoutingTable();
        // }

        devIDs = new ArrayList<>();
        devIDs.addAll(network.getMembershipList());

        // Node initialization
        String[] line = {};
        try (Scanner sc = new Scanner(new File(nodesListFilename))) {
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                nodesIDs.add(line[0]);
                nodesLimits.put(line[0], Integer.parseInt(line[1]));
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Node list file " + nodesListFilename + " does not exist");
            System.exit(-1);
        }

        // if (debug)
        // System.out.println("Nodes: " + nodesIDs + "\n");
    }

    public void initializeRoutines(
            int routineNo, String routinesDevicesMapFilename, String routinesLengthsFilename) {
        String[] line = {}, triggerDevIDsArr, touchedDevIDsArr;
        String rtnID;
        List<String> triggerDevIDs = null;
        List<String> touchedDevIDs = null;

        if (routineNo != 0) {
            try (Scanner sc = new Scanner(new File(routinesDevicesMapFilename))) {
                while (sc.hasNextLine()) {
                    line = sc.nextLine().split(" ");
                    rtnID = line[0];

                    triggerDevIDsArr = line[1].split(",");

                    if (triggerDevIDsArr[0].equals("-1"))
                        triggerDevIDs = List.of();
                    else
                        triggerDevIDs = Arrays.asList(triggerDevIDsArr);

                    touchedDevIDsArr = line[2].split(",");
                    if (touchedDevIDsArr[0].equals("-1"))
                        touchedDevIDs = List.of();
                    else
                        touchedDevIDs = Arrays.asList(touchedDevIDsArr);
                    routines.put(rtnID, new DumbRoutine(triggerDevIDs, touchedDevIDs));
                }

            } catch (FileNotFoundException ex) {
                Network.log(
                        "Routine map file " + routinesDevicesMapFilename + " does not exist", true);
                System.exit(-1);
            }

            // About routine: based on the above code, only has one routine; if need more
            // routines, change

            int length;
            try (Scanner sc = new Scanner(new File(routinesLengthsFilename))) {
                while (sc.hasNextLine()) {
                    line = sc.nextLine().split(" ");
                    rtnID = line[0];
                    length = Integer.parseInt(line[1]);
                    routines.get(rtnID).setLength(length);
                }
            } catch (FileNotFoundException ex) {
                Network.log(
                        "Routine length file " + routinesLengthsFilename + " does not exist", true);
                System.exit(-1);
            }
        }
    }

    public void initializeRoutines(String routineDirName, WorkloadOption option) {
        if (routineDirName != null) {
            Routine routine = null;

            if (option == WorkloadOption.SIEBEL) {
                File dir = new File(routineDirName);
                Network.log("dir name: " + dir);

                for (File subDir : dir.listFiles()) {
                    Network.log("subdir name: " + subDir);
                    if (subDir.isDirectory() && subDir.listFiles() != null) {
                        for (File rtnFile : subDir.listFiles()) {

                            if (!rtnFile.isDirectory()) {
                                String rtnFileName = routineDirName + "/" + subDir.getName() + "/" + rtnFile.getName();
                                String jsonText = null;

                                try {
                                    InputStream is = new FileInputStream(rtnFileName);
                                    jsonText = IOUtils.toString(is, "UTF-8");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Network.log(rtnFileName);
                                JSONObject rtnObj = new JSONObject(jsonText);

                                Network.log(rtnObj.getString("rtnID"));
                                routine = new DetailedRoutine(parseStatement(rtnObj.getJSONObject("conds")),
                                        parseCommand(rtnObj.getJSONArray("cmds")));

                                if (routine.getTriggerDevIDs().isEmpty()) {

                                    if (!events.containsKey(routine.getInitialTime())) {
                                        events.put(routine.getInitialTime(), new ArrayList<>());
                                    }
                                    events.get(routine.getInitialTime()).add(new Event(
                                            EventType.ROUTINE_TRIGGERED, rtnObj.getString("rtnID")));
                                }
                                routines.put(rtnObj.getString("rtnID"), routine);
                                Network.log(routine);
                            }
                        }
                    }
                }

                Network.log("routines: " + routines);
            } else {
                String rtnFileName = routineDirName;
                try (Scanner sc = new Scanner(new File(rtnFileName))) {

                    String rtnID = sc.next();
                    String jsonText = null;

                    try {
                        InputStream is = new FileInputStream("workloads/routines/" + rtnID + ".json");
                        jsonText = IOUtils.toString(is, "UTF-8");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    JSONObject rtnObj = new JSONObject(jsonText);

                    routine = new DetailedRoutine(parseStatement(rtnObj.getJSONObject("conds")),
                            parseCommand(rtnObj.getJSONArray("cmds")));
                    routines.put(rtnID, routine);

                } catch (FileNotFoundException ex) {
                    Network.log("Routine list file " + rtnFileName + " does not exist");
                    System.exit(-1);
                }
            }

            for (Entry<String, Routine> r : routines.entrySet()) {
                Network.log("Routine " + r.getKey() + " takes " + r.getValue().getLength()
                        + " time units to complete and touches devices " + r.getValue().getTouchedDevIDs());
            }
        }
    }

    public void initializeRtnEvents(String routinesScheduleFilename, TestType testType) {
        String rtnID;
        Routine rtn;
        String[] line;
        int eventTS;
        try (Scanner sc = new Scanner(new File(routinesScheduleFilename))) {
            // boolean fst_rtn_started = false;
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                eventTS = Integer.parseInt(line[0]);
                rtnID = line[1];
                rtn = routines.get(rtnID);
                if (events.get(eventTS) == null) {
                    events.put(eventTS, new ArrayList<Event>());
                }
                events.get(eventTS).add(new Event(EventType.ROUTINE_TRIGGERED, rtnID));
                Network.log("Routine " + rtnID + " starts at time " + eventTS + ": " + rtn, true);
            }
        } catch (FileNotFoundException ex) {
            Network.log("Routine schedule file " + routinesScheduleFilename + " does not exist", true);
            System.exit(-1);
        }
    }

    public void initializeEvents(
            String nodesScheduleFilename, TestType testType) {
        String[] line;
        int eventTS;
        try (Scanner sc = new Scanner(new File(nodesScheduleFilename))) {
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                eventTS = Integer.parseInt(line[0]);
                if (events.get(eventTS) == null) {
                    events.put(eventTS, new ArrayList<Event>());
                }
                DeviceState newState = new DeviceState(line[2]);
                events.get(eventTS).add(new Event(line[1], newState));
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Node schedule file " + nodesScheduleFilename + " does not exist");
            System.exit(-1);
        }

        // membership list creation
        for (String nodeID : nodesIDs) {
            membershipList.put(nodeID, Membership.ONLINE);
        }
        // set nodes' membership as offline if they join later
        for (Integer checkTS : events.keySet()) {
            if (checkTS > 0 && events.get(checkTS) != null) {
                for (Event e : events.get(checkTS)) {
                    if (e.getType() == EventType.NODE_JOINED) {
                        membershipList.replace(e.getAffectedEntity(), Membership.OFFLINE);
                    }
                }
            }
        }
    }

    public void initializeDevEvents(
            String devicesScheduleFilename, TestType testType) {
        String[] line, devProperty;
        int eventTS;
        try (Scanner sc = new Scanner(new File(devicesScheduleFilename))) {
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                eventTS = Integer.parseInt(line[0]);
                if (events.get(eventTS) == null) {
                    events.put(eventTS, new ArrayList<Event>());
                }
                devProperty = line[1].split("\\$");
                if (devProperty.length == 1)
                    events.get(eventTS).add(new Event(devProperty[0], new DeviceState(line[2])));
                else if (devProperty.length == 2)
                    events.get(eventTS).add(
                            new Event(devProperty[0], new DeviceState(line[2], devProperty[1])));
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Device schedule file " + devicesScheduleFilename + " does not exist");
            System.exit(-1);
        }
    }

    public void initializeDevClusters(
            DevClusterPolicy devClusterPolicy, String devClustersFilename, int deviceKGroupRange) {
        // Device clusters initialization
        if (devClusterPolicy == DevClusterPolicy.LOCALITY) {
            try (Scanner sc = new Scanner(new File(devClustersFilename))) {
                while (sc.hasNextLine()) {
                    devClusters.add(Arrays.asList(sc.nextLine().split(" ")));
                }
            } catch (FileNotFoundException ex) {
                System.out.println("Device clusters file " + devClustersFilename + " does not exist");
                System.exit(-1);
            }
        } else { // random clusters
            for (int counter = 0; counter < devIDs.size(); counter += deviceKGroupRange) {
                int end_index = Math.min(counter + deviceKGroupRange, devIDs.size());
                devClusters.add(devIDs.subList(counter, end_index));
            }
        }
    }

    public void initializeLSH(
            KGroupSelectionPolicy kGroupPolicy, LSHParams hashParams, int F, int K,
            String deviceTopologyDims, String devicesLocationFilename,
            String devicesTopologyFilename) {
        if (kGroupPolicy.equals(KGroupSelectionPolicy.LSHMIX)) {
            List<List<String>> rtnTriggerDevs = new ArrayList<>();
            if (routines.size() != 0) {

                for (Routine rtn : routines.values()) {
                    if (!rtn.getTriggerDevIDs().isEmpty()) {
                        rtnTriggerDevs.add(new ArrayList<String>(rtn.getTriggerDevIDs()));
                    } else
                        rtnTriggerDevs.add(new ArrayList<String>(rtn.getTouchedDevIDs()));
                }
            }
            KGroupManager mngr;
            for (String nodeID : nodesIDs) {
                if (nodes.get(nodeID) instanceof KGroupManager) {
                    mngr = (KGroupManager) nodes.get(nodeID);

                    mngr.setKMemberMix(new KMemberMix(
                            deviceTopologyDims, F, K, hashParams, devicesLocationFilename,
                            devicesTopologyFilename, devClusters, rtnTriggerDevs, nodesIDs, nodesLimits));
                }
            }
        }
    }

    public void initializeKGroupManagers(
            int F, int K, int epochLength, int minTriggerOffset, int devMntrPeriod,
            String devClustersFilename, DevClusterPolicy devClusterPolicy,
            int deviceKGroupRange, int routineKGroupRange, int end,
            LeaderElectionPolicy electionPolicy, LockStrategy lockStrategy,
            KGroupSelectionPolicy kGroupPolicy, LSHParams hashParams, boolean debug) {

        if (nodesIDs.size() < K) {
            System.out.println("number of smart nodes and K : " + nodesIDs.size() + " " + K);
            System.out.println("mission impossible!");
            System.exit(-1);
        }

        int nodeLimit;
        Device node;
        for (String devID : devIDs) {
            if (nodesIDs.contains(devID)) {
                nodeLimit = nodesLimits.get(devID);
                node = new KGroupManager(
                        devID, nodeLimit, F, K, epochLength, minTriggerOffset, devMntrPeriod,
                        nodesIDs, devIDs, routines, devClusters, routineKGroupRange,
                        network, 0, end, events, membershipList,
                        electionPolicy, lockStrategy, kGroupPolicy);
            } else
                node = new IoTDevice(
                        devID, network, 0, events, routines, Membership.ONLINE);

            nodes.put(devID, node);
        }
    }

    public void initializeCentralizedMngrAndIoTDevs(
            int routineNo, int minTriggerOffset, int devMntrPeriod, boolean debug) {
        if (nodesIDs.size() < 1) {
            Network.log("No smart nodes, mission impossible!", true);
            System.exit(-1);
        }

        boolean first = true;
        Device node;

        for (String devID : devIDs) {
            if (first) {
                node = new CentralizedManager(
                        devID, nodesLimits.get(devID), network, nodesIDs, 0, devIDs,
                        routines, routineNo, minTriggerOffset, devMntrPeriod, events, membershipList);
                first = false;
            } else
                node = new IoTDevice(
                        devID, network, 0, events, routines, membershipList.get(devID));

            nodes.put(devID, node);
        }
    }

    private void writeString(String outFn, String text) {
        try {
            File fout = new File(outFn);
            if (!fout.exists()) {
                fout.createNewFile();
            }
            FileWriter writer = new FileWriter(outFn);
            writer.close();
            writer = new FileWriter(fout, true);
            writer.write(text);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.out.println("Error while writing results to file " + outFn);
        }
    }

    public static RoutineStatement parseStatement(JSONObject rj) {
        // System.out.println("In parse statement");
        String k = rj.getString("kind");
        switch (k) {
            case "and":
                List<RoutineStatement> rStatements = new ArrayList<>();
                rStatements.add(parseStatement((JSONObject) rj.get("op1")));
                rStatements.add(parseStatement((JSONObject) rj.get("op2")));
                return new AndStatement(rStatements);
            case "or":
                // System.out.println("In parse statement or");
                rStatements = new ArrayList<>();
                rStatements.add(parseStatement((JSONObject) rj.get("op1")));
                rStatements.add(parseStatement((JSONObject) rj.get("op2")));
                return new OrStatement(rStatements);
            case "not":
                // System.out.println("In parse statement not");
                return new NotStatement(parseStatement((JSONObject) rj.get("op1")));
            default:
                // System.out.println("In parse condition ==");
                return new Statement(parseCondition(rj));
        }
    }

    public static RoutineCondition parseCondition(JSONObject rj) {
        ConditionRelation relation = ConditionRelation.getCondFromVal(rj.getString("kind"));
        String op1 = rj.getString("op1");
        String value = rj.getString("op2");

        if (op1.equals("time")) {
            return new TimeCondition(relation, Float.valueOf(value));
        }

        if (op1.contains("acc")) {
            return new DeviceStateCondition(op1, relation, value);
        }

        if (op1.contains("$")) {
            int index = op1.indexOf("$");
            String property = op1.substring(index + 1);
            op1 = op1.substring(0, index);
            return new DeviceStateCondition(op1, relation, value, property);
        }

        return new DeviceStateCondition(op1, relation, value);
    }

    public static SetOfCommands parseCommand(JSONArray rj) {
        List<Subroutine> rCommands = new ArrayList<>();
        for (int i = 0; i < rj.length(); i++) {
            JSONObject obj = rj.getJSONObject(i);
            RoutineCommand rc;
            String devID = obj.getString("devID");
            String devProperty;
            if (obj.has("len")) {
                if (devID.contains("$")) {
                    devProperty = devID.split("\\$")[1];
                    devID = devID.split("\\$")[0];
                    rc = new LengthyCommand(
                            devID, obj.getString("newState"), devProperty, obj.getInt("len"));
                } else {
                    rc = new LengthyCommand(
                            devID, obj.getString("newState"), obj.getInt("len"));
                }
            } else {
                if (devID.contains("$")) {
                    devProperty = devID.split("\\$")[1];
                    devID = devID.split("\\$")[0];
                    rc = new RoutineCommand(devID, obj.getString("newState"), devProperty);
                } else {
                    rc = new RoutineCommand(devID, obj.getString("newState"));
                }
            }
            rCommands.add(new Command(rc));
        }
        return new SetOfCommands(rCommands);
    }

    public void simulateCoMesh(
            int epochLength, KGroupSelectionPolicy kGroupPolicy, int end,
            TestType testType, String outFn, boolean debug) {
        File outF = null;
        if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE
                || testType == TestType.INKGROUP_BENCHMARK_W_FAILURE) {
            try {
                outF = new File(outFn);
                outF.createNewFile();
                FileWriter outputWriter = new FileWriter(outF);
                outputWriter.write("Leader Election time,Quorum time,State Transfer time,All k-group time");
                outputWriter.close();
            } catch (IOException e) {
                System.out.println("An error occurred while creating file " + outFn + ".");
                e.printStackTrace();
            }
        }

        if (testType == TestType.INKGROUP_FAILURE_SCHEDULE) {
            try {
                outF = new File(outFn);
                outF.createNewFile();
                FileWriter outputWriter = new FileWriter(outF);
                outputWriter.write("time,num_active_kgroup\n");
                outputWriter.close();
            } catch (IOException e) {
                System.out.println("An error occurred while creating file " + outFn + ".");
                e.printStackTrace();
            }

            // ** Insert failure **
            int fs_idx = outFn.lastIndexOf('_');
            String fs = outFn.substring(fs_idx - 2, fs_idx);
            // Generate fail node
            Random rand = new Random();
            String fail_node = nodesIDs.get(rand.nextInt(nodesIDs.size()));
            // Create failure event
            if (fs.equals("dr") || fs.equals("dl")) { // The expr is for during the epoch change
                // Force the failure happens at the 2 time unit after the 1st epoch change
                events.computeIfAbsent(epochLength + 2, k -> new ArrayList<Event>());
                events.get(epochLength + 2).add(new Event("f", fail_node));
            } else if (fs.equals("bf") || fs.equals("bl")) { // Failure happens before epoch change.
                // Insert failure to a random time of 2nd half of the 1st epoch
                int fail_time = rand.nextInt(epochLength / 2) + epochLength / 2;
                events.computeIfAbsent(fail_time, k -> new ArrayList<Event>());
                events.get(fail_time).add(new Event("f", fail_node));
            }
        }

        int checkpoint_ts = 0;
        Event checkpoint_event = null;
        if (testType.equals(TestType.BANDWIDTH_BG) && !nodesIDs.isEmpty()) {
            nodes.get(nodesIDs.get(0)).addEvent(checkpoint_ts, checkpoint_event);
        }

        // boolean existUnprocessedEvents = true;
        int t_terminate = testType.equals(TestType.BANDWIDTH_BG) ? end * 2 : end;
        if (testType.equals(TestType.BANDWIDTH_BG) && !nodesIDs.isEmpty()) {
            nodes.get(nodesIDs.get(0)).addEvent(t_terminate, checkpoint_event);
        }

        int epoch = 0;

        // Used for DELAY_BREAKDOWN expr only
        boolean failure_inserted = false;

        for (int curTS = 0; curTS <= t_terminate; curTS = Network.incrTS()) {
            // maintain membership list for LSH k-group updates
            List<Integer> toRemoveLists = new ArrayList<>();
            for (Integer checkTS : events.keySet()) {
                if (checkTS > curTS) {
                    break;
                }
                if (events.get(checkTS) != null) {
                    List<Event> toRemove = new ArrayList<>();
                    for (Event e : events.get(checkTS)) {
                        switch (e.getType()) {
                            case NODE_JOINED:
                                membershipList.replace(e.getAffectedEntity(), Membership.ONLINE);
                                toRemove.add(e);
                                break;
                            case NODE_FAILED:
                                membershipList.replace(e.getAffectedEntity(), Membership.OFFLINE);
                                toRemove.add(e);
                                break;
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
            // membership list maintainance end

            if (curTS % epochLength == 0) {
                System.out.println("\n----------------EPOCH " + (curTS / epochLength + 1) + "----------------\n");
                epoch = curTS / epochLength + 1;

                if (kGroupPolicy.equals(KGroupSelectionPolicy.LSHMIX)) {
                    // System.out.println("getting the new k groups for new epoch from lshmix");
                    String nodeID;
                    Device node;
                    KGroupManager mngr;
                    for (Entry<String, Device> nodeEntry : nodes.entrySet()) {
                        nodeID = nodeEntry.getKey();
                        node = nodeEntry.getValue();
                        if (node instanceof KGroupManager) {
                            mngr = (KGroupManager) node;
                            if (mngr.getKMemberMix() == null)
                                Network.log(nodeID, "null kMemberMix", true);
                            else
                                mngr.getKMemberMix().getKMembersNewEpoch(epoch, membershipList);
                        }
                    }
                }
            }
            List<String> offline_nodes = new ArrayList<>();
            for (Entry<String, Membership> e : membershipList.entrySet()) {
                String nodeID = e.getKey();
                Membership mem = e.getValue();
                if (mem == Membership.OFFLINE) {
                    network.clearMsgs(nodeID);
                    offline_nodes.add(nodeID);
                }
            }

            if (testType == TestType.INKGROUP_FAILURE_SCHEDULE && curTS > 10) {
                for (Device node : nodes.values()) {
                    if (!offline_nodes.contains(node.getMyID()) && node instanceof KGroupManager) {
                        // Go into one non-failed node to get kgroup "membership" info
                        int num_wgroup = 0;
                        List<DeviceKGroup> allDevGroups = getKGrpMngr(node).getDevKGrps();
                        // In each kgroup, get the leaderElectionStage to determine whether this kgroup
                        // is processing or needs to delay processing (due to leader failure/epoch
                        // change).
                        for (DeviceKGroup group : allDevGroups) {
                            // Only the member in the kgroup will have the correct leader related info.
                            // Thus, we need to find the DeviceKGroup instance that contains real info.

                            // First get all members of this deviceKGroup
                            List<String> members = group.curMemberIDs;
                            List<String> dev_ids = group.entitiesIDs;
                            // Go to the KGroupManager of one of the member to get the leader of the
                            // deviceKGroup g;
                            DeviceKGroup group_g = getKGrpMngr(nodes.get(members.get(0)))
                                    .getDevKGrp(dev_ids.get(0));
                            String leader_g = group_g.getMostProbableLeader(false);
                            // Get the deviceKGroup instance in the leader for group g.
                            DeviceKGroup group_l = getKGrpMngr(nodes.get(leader_g))
                                    .getDevKGrp(dev_ids.get(0));
                            // Get the leaderElectionStage of this deviceKGroup
                            LeaderElectionStage e_stage = group_l.ldrElctnStg;
                            // Only when the election stage is COMPLETE, the group is ready to directly
                            // process.
                            if (e_stage.equals(LeaderElectionStage.COMPLETE)) {
                                num_wgroup += 1;
                            }
                        }

                        List<RoutineKGroup> allRtnGroups = getKGrpMngr(node).getRtnKGrps();
                        for (RoutineKGroup group : allRtnGroups) {
                            List<String> members = group.curMemberIDs;
                            List<String> rtn_ids = group.entitiesIDs;
                            RoutineKGroup group_g = getKGrpMngr(nodes.get(members.get(0)))
                                    .getRtnKGrp(rtn_ids.get(0));
                            String leader_g = group_g.getMostProbableLeader(false);
                            RoutineKGroup group_l = getKGrpMngr(nodes.get(leader_g))
                                    .getRtnKGrp(rtn_ids.get(0));
                            LeaderElectionStage e_stage = group_l.ldrElctnStg;
                            if (e_stage.equals(LeaderElectionStage.COMPLETE)) {
                                num_wgroup += 1;
                            }
                        }
                        // Record to file
                        try {
                            outF = new File(outFn);
                            outF.createNewFile();
                            FileWriter outputWriter = new FileWriter(outF, true);
                            outputWriter.write(curTS + "," + num_wgroup + "\n");
                            outputWriter.close();
                        } catch (IOException e) {
                            System.out.println("An error occurred while creating file " + outFn + ".");
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }

            if (!failure_inserted && testType == TestType.DELAY_BREAKDOWN && curTS > 10) {
                // ** Insert failure **
                int fs_idx = outFn.lastIndexOf('_');
                String fs = outFn.substring(fs_idx - 2, fs_idx);
                // Get any routine leader
                String leader_g = null;
                KGroupManager node = (KGroupManager) nodes.values().toArray()[0];
                List<RoutineKGroup> allRtnGroups = node.getRtnKGrps();
                for (RoutineKGroup group : allRtnGroups) {
                    List<String> members = group.curMemberIDs;
                    List<String> rtn_ids = group.entitiesIDs;
                    RoutineKGroup group_g = getKGrpMngr(nodes.get(members.get(0)))
                            .getRtnKGrp(rtn_ids.get(0));
                    leader_g = group_g.getMostProbableLeader(false);
                }
                // Decide failure node
                Random rand = new Random();
                String fail_node = "";
                if (fs.equals("dl") || fs.equals("bl")) { // Failure needs to a leader of routine
                    fail_node = leader_g;
                } else if (fs.equals("dr") || fs.equals("bf")) { // Failure is a member of routine.
                    do {
                        fail_node = nodesIDs.get(rand.nextInt(nodesIDs.size()));
                    } while (fail_node.equals(leader_g));
                }
                // Create failure event
                if (!fail_node.equals("")) {
                    if (fs.equals("dr") || fs.equals("dl")) { // The expr is for during the epoch change
                        // Force the failure happens at the 2 time unit after the 1st epoch change
                        events.computeIfAbsent(epochLength + 2, k -> new ArrayList<Event>());
                        events.get(epochLength + 2).add(new Event("f", fail_node));
                    } else { // Failure happens before epoch change.
                        // Insert failure to a random time of 2nd half of the 1st epoch
                        int fail_time = rand.nextInt(epochLength / 2) + epochLength / 2;
                        events.computeIfAbsent(fail_time, k -> new ArrayList<Event>());
                        events.get(fail_time).add(new Event("f", fail_node));
                    }
                    failure_inserted = true;
                }
            }

            for (Device node : nodes.values()) {
                node.incrementTS();
                if (!node.isNodeOnline()) {
                    continue;
                }
                node.recvAndProcessMsgs(curTS);
            }
        }
        // moved the LSH metrics to lshTest
        if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE
                || testType == TestType.INKGROUP_BENCHMARK_W_FAILURE) {
            int lastDotIndex = outFn.lastIndexOf(".");
            String outFnPrefix = outFn.substring(0, lastDotIndex + 1);

            String delays = KGroupMetric.stringifyQuorumDelays();
            outFn = outFnPrefix + "quorum_delays.csv";
            writeString(outFn, delays);
            System.out.println("Wrote file " + outFn);

            delays = KGroupMetric.stringifyLdrElctnDelays();
            outFn = outFnPrefix + "elctn_delays.csv";
            writeString(outFn, delays);
            System.out.println("Wrote file " + outFn);

            delays = KGroupMetric.stringifyStateTrnsfrDelays();
            outFn = outFnPrefix + "state_delays.csv";
            writeString(outFn, delays);
            System.out.println("Wrote file " + outFn);
        }
        // // Get routine delay metrics and write/append to file.
        else if (testType == TestType.CLIENT_DELAY) {
            System.out.println("in client delay tests");
            RoutineMetricSingleton routineMetrics = RoutineMetricSingleton.getInstance();
            routineMetrics.clearRecords();
            List<String> delayStrings = routineMetrics.getClientDelaysInString();
            try {
                File fout = new File(outFn);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("RoutineID,SeqNo,ClientDelayUsr,ClientDelaySys,ClientDelayAck\n");
                writer.close();
                writer = new FileWriter(fout, true);
                for (String delay : delayStrings) {
                    writer.write(delay + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (testType == TestType.SYNC_DELAY) {
            RoutineMetricSingleton routineMetrics = RoutineMetricSingleton.getInstance();
            List<String> delayStrings = routineMetrics.getSyncDelayInString();
            try {
                File fout = new File(outFn);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("RoutineID,SeqNo,SyncDelay\n");
                writer.close();
                writer = new FileWriter(fout, true);
                for (String delay : delayStrings) {
                    writer.write(delay + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            outFn = outFn.replaceAll("sync", "client");
            delayStrings = routineMetrics.getClientDelaysInString();
            try {
                File fout = new File(outFn);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("RoutineID,SeqNo,ClientDelayUsr,ClientDelaySys,ClientDelayAck\n");
                writer.close();
                writer = new FileWriter(fout, true);
                for (String delay : delayStrings) {
                    writer.write(delay + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            routineMetrics.clearRecords();
        } else if (testType == TestType.BANDWIDTH) {
            network.metric.recordRawMsgData(outFn);
            network.metric.printAllMessageSummary();
        } else if (testType == TestType.BANDWIDTH_BG) {
            network.metric.recordRawMsgData(outFn);
        } else if (testType == TestType.BALANCING) {
            try {
                File fout = new File(outFn);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("id,leader,member,ld_and_mem,sing_leader,sing_member,sing_idle\n");
                writer.close();
                writer = new FileWriter(fout, true);
                // Align ending time across nodes for role time collection.
                int max_ts = 0;
                for (Device node : nodes.values())
                    if (node instanceof KGroupManager)
                        if (node.getCurTS() > max_ts) {
                            max_ts = node.getCurTS();
                        }

                for (Device node : nodes.values()) {
                    if (node instanceof KGroupManager) {
                        KGroupManager mngr = (KGroupManager) node;
                        mngr.finalizeRole();
                        mngr.finalizeRole(max_ts);
                        writer.write(mngr.getRoleTimeInString() + "\n");
                    }
                }
                writer.close();
            } catch (IOException e) {
                System.out.println("An error occurred while creating file " + outFn + ".");
                e.printStackTrace();
            }

            // Record Role count over time.
            for (NodeRole role : List.of(NodeRole.LEADER, NodeRole.MEMBER, NodeRole.NON_IDLE)) {
                String fname = outFn.substring(0, outFn.length() - 4) + "_" +
                        role.name().toLowerCase() + ".csv";
                try {
                    File fout = new File(fname);
                    if (!fout.exists()) {
                        fout.createNewFile();
                    }
                    FileWriter writer = new FileWriter(fout);
                    writer.write("num_role,time\n");
                    writer.close();
                    writer = new FileWriter(fout, true);

                    Map<Integer, Integer> sum_count = new HashMap<>();
                    for (Device node : nodes.values()) {
                        if (!(node instanceof KGroupManager))
                            continue;
                        KGroupManager mngr = (KGroupManager) node;
                        Map<Integer, Integer> role_count = mngr.getRoleCountOverTime(role);
                        role_count.forEach((time, count) -> sum_count.merge(time, count, Integer::sum));
                    }
                    for (Map.Entry<Integer, Integer> entry : sum_count.entrySet()) {
                        if (entry.getValue() > 0) {
                            writer.write(entry.getKey() + "," + entry.getValue() + "\n");
                        }
                    }
                    writer.close();
                } catch (IOException e) {
                    System.out.println("An error occurred while creating file " + fname + ".");
                    e.printStackTrace();
                }
            }
        }
        // Get delay breakdown metrics and write/append to file.
        else if (testType == TestType.DELAY_BREAKDOWN) {
            RoutineMetricSingleton routineMetrics = RoutineMetricSingleton.getInstance();
            List<String> delayStrings = routineMetrics.getLockRequestAckTimeInString();
            routineMetrics.clearRecords();
            try {
                File fout = new File(outFn);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("RoutineID,SeqNo,MinLockReqTime,AvgLockReqTime,MaxLockReqTime,"
                        + "ClientDelayUsr,ClientDelaySys,ClientDelayAck\n");
                writer.close();
                writer = new FileWriter(fout, true);
                for (String delay : delayStrings) {
                    writer.write(delay + "\n");
                }
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void simulateCentralized(
            int end, TestType testType, String outFn, boolean debug) {
        int curTS;
        for (curTS = 0; curTS <= end || !events.isEmpty(); curTS++) {
            // check for new event
            getCentralizedMngr().incrementTS();
            getCentralizedMngr().processEvents();
        }

        try {
            File fout = new File(outFn);
            if (!fout.exists()) {
                fout.createNewFile();
            }
            FileWriter writer = new FileWriter(fout);
            writer.write("active,idle\n");
            writer.close();
            writer = new FileWriter(fout, true);
            writer.close();
        } catch (IOException e) {
            System.out.println("An error occurred while creating file " + outFn + ".");
            e.printStackTrace();
        }

        // Record Role count over time.
        for (NodeRole role : List.of(NodeRole.LEADER, NodeRole.MEMBER, NodeRole.NON_IDLE)) {
            String fname = outFn.substring(0, outFn.length() - 4) + "_" +
                    role.name().toLowerCase() + ".csv";
            try {
                File fout = new File(fname);
                if (!fout.exists()) {
                    fout.createNewFile();
                }
                FileWriter writer = new FileWriter(fout);
                writer.write("num_role,time\n");
                writer.close();
                writer = new FileWriter(fout, true);

                HashMap<Integer, Integer> sum_count = new HashMap<>();
                for (Map.Entry<Integer, Integer> entry : sum_count.entrySet()) {
                    if (entry.getValue() > 0) {
                        writer.write(entry.getKey() + "," + entry.getValue() + "\n");
                    }
                }
                writer.close();
            } catch (IOException e) {
                System.out.println("An error occurred while creating file " + fname + ".");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(helpText);
            System.exit(0);
        }

        String arg;
        // default parameter values
        boolean debug = false;
        TestType testType = TestType.INKGROUP_BENCHMARK_WO_FAILURE;
        LeaderElectionPolicy electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
        LockStrategy lockStrategy = LockStrategy.SERIAL;
        KGroupSelectionPolicy kGroupPolicy = KGroupSelectionPolicy.RANDOM;
        DevClusterPolicy devClusterPolicy = DevClusterPolicy.RANDOM;
        LSHParams hashParams = new LSHParams();
        int seed = 0;
        int routineNo = 10, deviceNo = 1000;
        String deviceTopologyShape = "grid", deviceTopologyDims = "10,10,10";
        String nodePercentage = "0.05";
        String nodeChurnProbability = "0.0", nodeScheduleDistribution = "uniform",
                nodeScheduleClusters = "0";
        String routineDeviceMapDistribution = "", avgDevicesPerRoutine = "", deviceClusters = "";
        String routineLengthDistribution = "", maxRoutineLength = "";
        String routineScheduleDistribution = "", routineScheduleClusters = "";

        String devicesLocationFilename = "workloads/sample_device_location_d"
                + String.valueOf(deviceNo)
                + "_" + deviceTopologyShape + deviceTopologyDims + ".txt";
        String devicesTopologyFilename = "workloads/sample_device_topology.txt";
        String devClustersFilename = "worklads/sample_device_clusters";
        String nodesListFilename = "workloads/sample_node_list.txt";
        String nodesScheduleFilename = "workloads/sample_node_schedule.txt";
        String routinesDevicesMapFilename = "workloads/sample_routine_map.txt";
        String routinesLengthsFilename = "workloads/sample_routine_lengths.txt";
        int end = 30, F = 1, K = 5, epochLength = 100 /* time units */,
                minTriggerOffset = 60, devMntrPeriod = 60;
        int hopOWD = 1, bwCap = 10000;
        int lowerNodeLimit = 10, upperNodeLimit = 50, deviceKGroupRange = 1, routineKGroupRange = 1;

        float waitCoef = 0; // Ask anna about this

        String outFn;

        try {
            for (int i = 0; i < args.length; i++) {
                arg = args[i];
                switch (arg) {
                    case "-e":
                    case "-E":
                    case "-end":
                        end = Integer.parseInt(args[++i]);
                        break;
                    case "-bw":
                    case "-BW":
                    case "-bwCap":
                        bwCap = Integer.parseInt(args[++i]);
                        break;
                    case "-rn":
                    case "-RN":
                    case "-routineNo":
                        routineNo = Integer.parseInt(args[++i]);
                        break;
                    case "-dn":
                    case "-DN":
                    case "-deviceNo":
                        deviceNo = Integer.parseInt(args[++i]);
                        break;
                    case "-dts":
                    case "-DTS":
                    case "-deviceTopologyShape":
                        // arg: -dts grid
                        deviceTopologyShape = args[++i];
                        break;
                    case "-dtd":
                    case "-DTD":
                    case "-deviceTopologyDims":
                        // arg: -dtd 3,3
                        deviceTopologyDims = args[++i];
                        break;
                    case "-np":
                    case "-NP":
                    case "-nodePercentage":
                        // arg: -np 0.1
                        nodePercentage = args[++i];
                        break;
                    case "-cp":
                    case "-CP":
                    case "-churnProbability":
                        // arg: -cp 0.1
                        nodeChurnProbability = args[++i];
                        break;
                    case "-nsd":
                    case "-NSD":
                    case "-nodeScheduleDistribution":
                        // arg: -nsd uniform
                        nodeScheduleDistribution = args[++i];
                        break;
                    case "-nsc":
                    case "-NSC":
                    case "-nodeScheduleClusters":
                        // arg: -nsc 2
                        nodeScheduleClusters = args[++i];
                        break;
                    case "-rdmd":
                    case "-RDMD":
                    case "-routineDeviceMapDistribution":
                        // arg: -rdmd uniform
                        routineDeviceMapDistribution = args[++i];
                        break;
                    case "-adpr":
                    case "-ADPR":
                    case "-avgDevicesPerRoutine":
                        // arg: -mdpr 5
                        avgDevicesPerRoutine = args[++i];
                        break;
                    case "-dc":
                    case "-DC":
                    case "-deviceClusters":
                        // arg: -nsc 2
                        deviceClusters = args[++i];
                        break;
                    case "-rld":
                    case "-RLD":
                    case "-routineLengthDistribution":
                        // arg: -rld uniform
                        routineLengthDistribution = args[++i];
                        break;
                    case "-mrl":
                    case "-MRL":
                    case "-maxRoutineLength":
                        // arg: -mrl 6
                        maxRoutineLength = args[++i];
                        break;
                    case "-rsd":
                    case "-RSD":
                    case "-routineScheduleDistribution":
                        // arg: -rsd uniform
                        routineScheduleDistribution = args[++i];
                        break;
                    case "-rsc":
                    case "-RSC":
                    case "-routineScheduleClusters":
                        // arg: -rsc 2
                        routineScheduleClusters = args[++i];
                        break;
                    case "-dm":
                    case "-DM":
                    case "-deviceMonitor":
                        // arg: -dm 10
                        devMntrPeriod = Integer.parseInt(args[++i]);
                        break;
                    case "-mto":
                    case "-MTO":
                    case "-minTriggerOffset":
                        // arg: -mto 10
                        minTriggerOffset = Integer.parseInt(args[++i]);
                        break;
                    case "-f":
                    case "-F":
                    case "-faults":
                        // arg: -f 1
                        F = Integer.parseInt(args[++i]);
                        break;
                    case "-k":
                    case "-K":
                    case "-kgroupSize":
                        // arg: -k 5
                        K = Integer.parseInt(args[++i]);
                        break;
                    case "-el":
                    case "-EL":
                    case "-epochLength":
                        // arg: -el 100
                        epochLength = Integer.parseInt(args[++i]);
                        break;
                    case "-owd":
                    case "-OWD":
                    case "-oneWayDelay":
                        hopOWD = Integer.parseInt(args[++i]);
                        break;
                    case "-RD":
                    case "-rd":
                    case "-rangeDeviceKGroup":
                        deviceKGroupRange = Integer.parseInt(args[++i]);
                        break;
                    case "-RR":
                    case "-rr":
                    case "-rangeRoutineKGroup":
                        routineKGroupRange = Integer.parseInt(args[++i]);
                        break;
                    case "-d":
                    case "-D":
                    case "-debug":
                        debug = true;
                        System.out.println("Debug mode is on");
                        break;
                    case "-lep":
                    case "-LEP":
                    case "-electionPolicy":
                        String lepStr = args[++i];
                        if (lepStr.equals("sh") || lepStr.equals("SH")) {
                            electionPolicy = LeaderElectionPolicy.SMALLEST_HASH;
                        } else if (lepStr.equals("sID") || lepStr.equals("SID")) {
                            electionPolicy = LeaderElectionPolicy.SMALLEST_ID;
                        } else if (lepStr.equals("cn") || lepStr.equals("CN")) {
                            electionPolicy = LeaderElectionPolicy.CENTRAL_NODE;
                        } else {
                            electionPolicy = null;
                        }
                        break;
                    case "-dcp":
                    case "-DCP":
                    case "-devClusterPolicy":
                        String dcpStr = args[++i];
                        if (dcpStr.equals("r") || dcpStr.equals("R")) {
                            devClusterPolicy = DevClusterPolicy.RANDOM;
                        } else if (dcpStr.equals("sID") || dcpStr.equals("SID")) {
                            devClusterPolicy = DevClusterPolicy.LOCALITY;
                        } else {
                            devClusterPolicy = null;
                        }
                        break;
                    case "-lnl":
                    case "-LNL":
                    case "-lowerNodeLimit":
                        lowerNodeLimit = Integer.parseInt(args[++i]);
                        break;
                    case "-unl":
                    case "-UNL":
                    case "-upperNodeLimit":
                        upperNodeLimit = Integer.parseInt(args[++i]);
                        break;
                    case "-h":
                    case "-H":
                    case "-help":
                        System.out.println(helpText);
                        if (args.length == 1) {
                            System.exit(0);
                        }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(helpText);
            System.exit(-1);
        }

        Simulator simulator = new Simulator();

        createWorkloads(
                seed,
                deviceTopologyShape, deviceTopologyDims, deviceKGroupRange,
                deviceNo, nodePercentage, lowerNodeLimit, upperNodeLimit,
                nodeChurnProbability, nodeScheduleDistribution, nodeScheduleClusters, end,
                routineNo, routineDeviceMapDistribution, avgDevicesPerRoutine, deviceClusters,
                routineLengthDistribution, maxRoutineLength,
                routineScheduleDistribution, routineScheduleClusters);

        devicesTopologyFilename = "workloads/device_topology_d" + String.valueOf(deviceNo)
                + "_" + deviceTopologyShape + deviceTopologyDims + ".txt";
        devClustersFilename = "workloads/device_clusters_d" + String.valueOf(deviceNo)
                + "_" + deviceTopologyShape + deviceTopologyDims + ".txt";
        nodesListFilename = "workloads/node_list_d" + String.valueOf(deviceNo)
                + "_np" + nodePercentage + ".txt";
        nodesScheduleFilename = "workloads/node_schedule_d" + String.valueOf(deviceNo)
                + "_np" + nodePercentage + "_cp" + nodeChurnProbability
                + "_" + nodeScheduleDistribution
                + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + "_e"
                + String.valueOf(end) + ".txt";

        routinesDevicesMapFilename = "workloads/routine_device_map_r" + String.valueOf(routineNo)
                + "_d" + String.valueOf(deviceNo) + "_a" + avgDevicesPerRoutine
                + "_" + routineDeviceMapDistribution
                + (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "") + ".txt";
        routinesLengthsFilename = "workloads/routine_lengths_r" + String.valueOf(routineNo)
                + "_m" + maxRoutineLength + "_" + routineLengthDistribution
                + ".txt";
        // routinesScheduleFilename = "workloads/routine_schedule_r" +
        // String.valueOf(routineNo)
        // + "_e" + String.valueOf(end) + (
        // routineScheduleDistribution.equals("clusters")?
        // routineScheduleClusters: ""
        // ) + ".txt";

        outFn = "outputs/d" + String.valueOf(deviceNo)
                + "_" + deviceTopologyShape + deviceTopologyDims
                + "_np" + nodePercentage + "_cp" + nodeChurnProbability
                + "_" + nodeScheduleDistribution
                + (nodeScheduleDistribution.equals("clusters") ? " -c " + nodeScheduleClusters : "") + "_e"
                + String.valueOf(end) + "_r" + String.valueOf(routineNo)
                + "_a" + avgDevicesPerRoutine + "_" + routineDeviceMapDistribution
                + (routineDeviceMapDistribution.equals("clusters") ? deviceClusters : "") + "_m" + maxRoutineLength
                + "_" + routineLengthDistribution
                + "_e" + String.valueOf(end)
                + (routineScheduleDistribution.equals("clusters") ? routineScheduleClusters : "") + ".csv";

        simulator.initializeTopology(
                deviceTopologyDims, devicesLocationFilename, devicesTopologyFilename,
                nodesListFilename, nodesScheduleFilename, hopOWD, bwCap, waitCoef, debug);
        simulator.initializeRoutines(routineNo, routinesDevicesMapFilename, routinesLengthsFilename);

        simulator.initializeEvents(nodesScheduleFilename, testType);

        simulator.initializeKGroupManagers(
                F, K, epochLength, minTriggerOffset, devMntrPeriod, devClustersFilename, devClusterPolicy,
                deviceKGroupRange, routineKGroupRange, end, electionPolicy, lockStrategy,
                kGroupPolicy, hashParams, debug);

        simulator.simulateCoMesh(
                epochLength, kGroupPolicy, end, testType, outFn, debug);
    }
}
