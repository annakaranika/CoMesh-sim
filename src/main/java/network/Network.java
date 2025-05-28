package network;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;

import javafx.geometry.Point3D;
import javafx.util.Pair;

import dijkstra.*;
import metric.NetworkMetric;
import network.message.*;

public class Network {
    private static boolean debug;
    private static FileWriter logger;
    private static int _curTS;
    private int hopOWD, bwCap;
    private float waitCoef;
    private static final float fitCoeff = 1.4054f, fitExp = 0.0301f;
    // for every node
    private Map<String, Node> neighbors = new HashMap<>();
    private Map<String, Map<String, Pair<String, Float>>> routingTable = new HashMap<>();
    public Map<String, Map<Integer, Set<Message>>> msgBuffers = new HashMap<>(); // {rcver: {ts: [msg]}}
    private Map<String, Map<Integer, Integer>> devMsgCnt = new HashMap<>();
    private int msgNo;
    private long totalBytes;

    public NetworkMetric metric;

    public Network(String locationsFileName, String topologyFilename, int bwCap, float waitCoef, boolean debug) {
        Network.debug = debug;
        Network._curTS = 0;

        String logDir = "logs/" + new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss").format(
                new java.util.Date());
        String logFn = logDir + "/log.txt";

        File logFile = new File(logFn);
        try {
            if (!logFile.exists()) {
                Files.createDirectories(Paths.get(logDir));
                logFile.createNewFile();
                System.out.println("Log file " + logFn + " created: " + logFile.exists());
            }
            logger = new FileWriter(logFile);
        } catch (IOException e) {
            System.out.println("Error creating logger at " + logDir);
        }
        this.bwCap = bwCap;
        this.waitCoef = waitCoef;

        File locationFile = new File(locationsFileName);
        Map<String, Point3D> locations = new HashMap<>();
        try (Scanner sc = new Scanner(locationFile)) {
            while (sc.hasNextLine()) {
                String currLine = sc.nextLine();
                String[] currLines = currLine.split(" ");
                String nodeName = currLine.split(" ")[0];
                double z = 0;
                if (currLines.length >= 5) {
                    try {
                        z = Double.parseDouble(currLines[3]);
                    } catch (NumberFormatException e) {
                        z = 0.0;
                    }
                }
                Point3D pointLoc = new Point3D(
                        Double.parseDouble(currLines[1]), Double.parseDouble(currLines[2]), z);
                locations.put(nodeName, pointLoc);
            }
        } catch (FileNotFoundException ex) {
            log("Location file " + locationsFileName + " does not exist", true);
            System.exit(-1);
        }

        // create membership list, neighbors and msg listener for each node in the
        // network
        // based on topology-specifying file
        File topologyFile = new File(topologyFilename);
        Graph topology = new Graph();
        String curNode;
        try (Scanner sc = new Scanner(topologyFile)) {
            // create every node in the network
            while (sc.hasNextLine()) {
                curNode = sc.nextLine().split(" ")[0];
                neighbors.put(curNode, new Node(curNode));
            }
        } catch (FileNotFoundException ex) {
            log("Topology file " + topologyFilename + " does not exist", true);
            System.exit(-1);
        }

        try (Scanner sc = new Scanner(topologyFile)) {
            // add every node's neighbors
            String[] line = {};
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                curNode = line[0];

                for (int i = 1; i < line.length; i++) {
                    String nextNode = line[i];
                    Point3D point1 = locations.get(curNode);
                    Point3D point2 = locations.get(nextNode);
                    float distance = (float) point1.distance(point2);
                    float hopOWDF = fitCoeff * (float) Math.exp(fitExp * distance);
                    neighbors.get(curNode).addDestination(neighbors.get(line[i]), hopOWDF);
                }
                msgBuffers.put(curNode, new HashMap<Integer, Set<Message>>());
                devMsgCnt.put(curNode, new HashMap<Integer, Integer>());
            }
        } catch (FileNotFoundException ex) {
            log("Topology file " + topologyFilename + " does not exist", true);
            System.exit(-1);
        }

        // add every node to the graph
        topology.setNodes(neighbors.values());

        createRoutingTable(topology);

        msgNo = 0;
        totalBytes = 0;

        metric = new NetworkMetric();
    }

    public Network(String topologyFilename, int hopOWD, int bwCap, float waitCoef, boolean debug) {
        Network.debug = debug;
        Network._curTS = 0;

        String logDir = "logs/" + new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss").format(
                new java.util.Date());
        String logFn = logDir + "/log.txt";

        File logFile = new File(logFn);
        try {
            if (!logFile.exists()) {
                Files.createDirectories(Paths.get(logDir));
                logFile.createNewFile();
                System.out.println("Log file " + logFn + " created: " + logFile.exists());
            }
            logger = new FileWriter(logFile);
        } catch (IOException e) {
            System.out.println("Error creating logger at " + logDir);
        }
        this.hopOWD = hopOWD;
        this.bwCap = bwCap;
        this.waitCoef = waitCoef;

        // create membership list, neighbors and msg listener for each node in the
        // network
        // based on topology-specifying file
        File topologyFile = new File(topologyFilename);
        Graph topology = new Graph();
        String curNode;
        try (Scanner sc = new Scanner(topologyFile)) {
            // create every node in the network
            while (sc.hasNextLine()) {
                curNode = sc.nextLine().split(" ")[0];
                neighbors.put(curNode, new Node(curNode));
            }
        } catch (FileNotFoundException ex) {
            log("Topology file " + topologyFilename + " does not exist", true);
            System.exit(-1);
        }

        try (Scanner sc = new Scanner(topologyFile)) {
            // add every node's neighbors
            String[] line = {};
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                curNode = line[0];

                for (int i = 1; i < line.length; i++) {
                    neighbors.get(curNode).addDestination(neighbors.get(line[i]), 1);
                }
                msgBuffers.put(curNode, new HashMap<Integer, Set<Message>>());
                devMsgCnt.put(curNode, new HashMap<Integer, Integer>());
            }
        } catch (FileNotFoundException ex) {
            log("Topology file " + topologyFilename + " does not exist", true);
            System.exit(-1);
        }

        // add every node to the graph
        topology.setNodes(neighbors.values());

        createRoutingTable(topology);

        msgNo = 0;
        totalBytes = 0;

        metric = new NetworkMetric();
    }

    public static int resetTS() {
        _curTS = 0;
        return _curTS;
    }

    public static int getCurTS() {
        return _curTS;
    }

    public static int incrTS() {
        return ++_curTS;
    }

    public static PrintWriter getLogger() {
        return new PrintWriter(logger);
    }

    public static void log(String myID, Object text, boolean debug) {
        if (myID != null)
            text = myID + ": " + text;
        text += " at time " + getCurTS();
        if (debug) {
            System.out.println(text);
            try {
                logger.write(text + "\n");
                logger.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void log(String myID, Object text) {
        log(myID, text, debug);
    }

    public static void log(Object text, boolean debug) {
        log(null, text, debug);
    }

    public static void log(Object text) {
        log(null, text, debug);
    }

    public static void flushLog() {
        try {
            logger.flush();
            logger.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printTopology() {
        log("printTopology:");
        for (String node : neighbors.keySet()) {
            Node value = neighbors.get(node);
            if (value != null) {
                log(node.toString() + ": " + value.toString());
            }
        }
    }

    public Set<String> getMembershipList() {
        return neighbors.keySet();
    }

    public Set<String> getNeighbors(String curNode) {
        Node node = neighbors.get(curNode);
        if (node != null) {
            return node.getAdjacentNodesIDs();
        } else {
            return null;
        }
    }

    public void createRoutingTable(Graph topology) {
        Graph tempTopology, spanningTree;
        synchronized (routingTable) {
            routingTable.clear();
            for (String srcNodeID : neighbors.keySet()) {
                tempTopology = new Graph(topology);
                if (debug) {
                    tempTopology.print();
                }
                spanningTree = Dijkstra.calculateShortestPathFromSource(tempTopology,
                        tempTopology.getNodeByID(srcNodeID));
                if (debug) {
                    log(srcNodeID + ": " + spanningTree);
                }

                routingTable.put(srcNodeID, new HashMap<String, Pair<String, Float>>());
                Map<String, Pair<String, Float>> singleNodeRoutingTable = routingTable.get(srcNodeID);
                for (Node destNode : spanningTree.getNodes()) {
                    if (destNode.getShortestPath().size() > 1) {
                        singleNodeRoutingTable.put(destNode.getID(), new Pair<String, Float>(
                                destNode.getShortestPath().get(1).getID(), destNode.getDistance()));
                    } else if (destNode.getShortestPath().size() == 1) {
                        singleNodeRoutingTable.put(destNode.getID(),
                                new Pair<String, Float>(destNode.getID(), destNode.getDistance()));
                    } else {
                        singleNodeRoutingTable.put(destNode.getID(), null);
                    }
                }
            }
        }
        if (debug) {
            printRoutingTable();
        }
    }

    public void printRoutingTable(String nodeID) {
        log("Routing table\nsource\tdestinations");
        System.out.print("\t");
        for (String node : neighbors.keySet()) {
            System.out.print(node + "\t");
        }

        synchronized (routingTable) {
            for (String src : neighbors.keySet()) {
                if (src.equals(nodeID)) {
                    System.out.print("\n" + src);
                    Map<String, Pair<String, Float>> singleNodeRoutingTable = routingTable.get(src);
                    if (singleNodeRoutingTable != null) {
                        for (String dst : singleNodeRoutingTable.keySet()) {
                            System.out.print("\t");
                            Pair<String, Float> neighbor = singleNodeRoutingTable.get(dst);
                            if (!src.equals(dst) && neighbor != null)
                                System.out.print(neighbor.getKey());
                        }
                        break;
                    }
                }
            }
        }
        System.out.println();
    }

    public void printRoutingTable() {
        log("Routing table\nsource\tdestinations");
        System.out.print("\t");
        for (String node : neighbors.keySet()) {
            System.out.print(node + "\t");
        }

        synchronized (routingTable) {
            for (String src : neighbors.keySet()) {
                System.out.print("\n" + src);
                Map<String, Pair<String, Float>> singleNodeRoutingTable = routingTable.get(src);
                if (singleNodeRoutingTable != null) {
                    for (String dst : singleNodeRoutingTable.keySet()) {
                        System.out.print("\t");
                        Pair<String, Float> neighbor = singleNodeRoutingTable.get(dst);
                        if (!src.equals(dst) && neighbor != null)
                            System.out.print(neighbor.getKey());
                    }
                }
            }
        }
        System.out.println();
    }

    public Map<String, Map<String, Pair<String, Float>>> getRoutingTable() {
        return routingTable;
    }

    public Map<String, Pair<String, Float>> getRoutingTable(String nodeID) {
        return routingTable.get(nodeID);
    }

    public int getRouteOWD(String src, String dst) {

        int routeOWD;
        if (src.equals(dst)) {
            routeOWD = 0;
        } else if (getRoutingTable(src).get(dst) == null) {
            log("from " + src + " to " + dst + ": no routing entry?");
            routeOWD = 0;
        } else {
            routeOWD = Integer.valueOf(String.valueOf(getRoutingTable(src).get(dst).getValue()).split("\\.")[0]);
        }
        return routeOWD;
    }

    public int getRouteRTT(String src, String dst) {
        return getRouteOWD(src, dst) * 2;
    }

    public int getResendTO(String src, String dst) {
        return getResendTO(getRouteRTT(src, dst));
    }

    public int getResendTO(int routeRTT) {
        return (int) (routeRTT * waitCoef);
    }

    public int getResendBound(String src, String dst) {
        return getResendTO(src, dst) * 2;
    }

    public int getResendBound(int routeRTT) {
        return getResendTO(routeRTT) * 2;
    }

    public void nodeJoined(String newNodeID, List<String> newNodeNeighbors) {
        msgBuffers.put(newNodeID, new HashMap<Integer, Set<Message>>());
        devMsgCnt.put(newNodeID, new HashMap<Integer, Integer>());

        neighbors.put(newNodeID, new Node(newNodeID));
        for (String nodeID : neighbors.keySet()) {
            if (newNodeNeighbors.contains(nodeID)) {
                neighbors.get(newNodeID).addDestination(neighbors.get(nodeID), 1);
                neighbors.get(nodeID).addDestination(neighbors.get(newNodeID), 1);
            }
        }

        Graph topology = new Graph();
        topology.setNodes(neighbors.values());
        createRoutingTable(topology);
    }

    public void nodeFailed(String failedNodeID) {
        msgBuffers.remove(failedNodeID);
        devMsgCnt.remove(failedNodeID);
        Node failedNode = neighbors.get(failedNodeID);
        for (String nodeID : neighbors.keySet()) {
            if (neighbors.get(nodeID).getAdjacentNodesIDs().contains(failedNodeID)) {
                neighbors.get(nodeID).removeDestination(failedNode);
            }
        }
        neighbors.remove(failedNodeID);

        Graph topology = new Graph();
        topology.setNodes(neighbors.values());
        createRoutingTable(topology);
    }

    public void sendMsg(Message msg) {
        // log("Sending " + msg);
        fwdMsg(msg, msg.src, msg.srcTS);
    }

    private void fwdMsg(final Message msg, final String curr, final int ts) {
        int delay = 0;
        while (devMsgCnt.get(curr).getOrDefault(ts + delay, 0) >= bwCap / getNeighbors(curr).size()) {
            delay++;
        }
        int curMsgCnt = devMsgCnt.get(curr).getOrDefault(ts + delay, 0);
        devMsgCnt.get(curr).replace(ts + delay, curMsgCnt + msg.getByteSize());
        msg.dstTS += delay;

        if (curr.equals(msg.dst)) {
            // if (curr.equals(msg.src)) msg.dstTS += 1;
            if (msgBuffers.get(msg.dst) == null) {
                msgBuffers.put(msg.dst, new HashMap<Integer, Set<Message>>());
            }
            if (msgBuffers.get(msg.dst).get(msg.dstTS) == null) {
                msgBuffers.get(msg.dst).put(msg.dstTS, new HashSet<Message>());
            }
            msgBuffers.get(msg.dst).get(msg.dstTS).add(msg);
            metric.recordE2EMsg(msg.src, msg.dst, msg.type);
        } else {
            msgNo++;
            // turn msg to byte array and get its length
            totalBytes += msg.getByteSize();
            Map<String, Pair<String, Float>> singleNodeRoutingTable = routingTable.get(curr);
            if (singleNodeRoutingTable != null) {
                String nodeID = singleNodeRoutingTable.get(msg.dst).getKey();
                if (nodeID != null) {
                    fwdMsg(msg, nodeID, ts + 1 + delay);
                    metric.recordH2HMsg(curr, nodeID, msg.type);
                } else {
                    log(msg + " failed at node " + curr);
                }
            } else {
                log(msg + " failed at node " + curr);
            }
        }
    }

    public boolean existUnreadMsgs() {
        for (String recipient : msgBuffers.keySet()) {
            for (Entry<Integer, Set<Message>> e : msgBuffers.get(recipient).entrySet()) {
                if (e.getValue() != null) {
                    if (!e.getValue().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Set<Message> recvCurMsgs(String recipient, int ts) {
        if (msgBuffers.get(recipient) != null) {
            Set<Message> msgSet = (Set<Message>) ((HashSet<Message>) msgBuffers.get(recipient).get(ts)).clone();
            msgBuffers.get(recipient).get(ts).clear();
            return msgSet;
        } else {
            return null;
        }
    }

    public Set<Message> recvPastMsgs(String recipient, int ts) {
        Set<Message> msgSet = new HashSet<>();
        if (!msgBuffers.containsKey(recipient)) {
            return msgSet;
        }
        for (Entry<Integer, Set<Message>> e : msgBuffers.get(recipient).entrySet()) {
            if (e.getKey() <= ts && e.getValue() != null) {
                msgSet.addAll(recvCurMsgs(recipient, e.getKey()));
            }
        }
        return msgSet;
    }

    public Set<Message> recvAllMsgs(String recipient) {
        Set<Message> msgSet = new HashSet<>();
        for (Entry<Integer, Set<Message>> e : msgBuffers.get(recipient).entrySet()) {
            if (e.getValue() != null) {
                msgSet.addAll(recvCurMsgs(recipient, e.getKey()));
            }
        }
        return msgSet;
    }

    public void clearMsgs(String recipient) {
        msgBuffers.remove(recipient);
    }

    public int totalMessages() {
        return msgNo;
    }

    public double averageBandwidth(int timeUnitsPassed) {
        return totalBytes / (double) timeUnitsPassed;
    }

    public int getNumberOfDevices() {
        return neighbors.size();
    }

}