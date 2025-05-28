package kgroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Logger;

import network.Network;

import java.util.logging.Level;

//return a set of K members: F + 1 from LSH, the other F chosen randomly
public class KMemberMix {

    class DeviceNodeDis {
        private String nodeID;
        private double dis;

        public DeviceNodeDis(String nodeID, double dis) {
            this.dis = dis;
            this.nodeID = nodeID;
        }

        public String getNodeID() {
            return nodeID;
        }

        public double getDis() {
            return dis;
        }
    }

    class SmartNode {
        String id;
        int limit;
        double[] location;
        double[] DVector;
        Set<String> monitoredDevicesIds;

        public SmartNode(String id, int limit) {
            this.id = id;
            this.limit = limit;
            // initially monitor itself
            monitoredDevicesIds = new HashSet<>();
            monitoredDevicesIds.add(id);
        }

        public void updateDVector(double randomness, int epoch) {
            getAveCoordinatesOfPreEpoc();
            getRandomize2dVectors(randomness, epoch);
        }

        private void getAveCoordinatesOfPreEpoc() {
            int cnt = monitoredDevicesIds.size();
            // it doesn't monitor any device in the previous epoch, reset it monitor itself
            if (cnt == 0) {
                monitoredDevicesIds.add(id);
                cnt = 1;
            }
            double[] coordinates = new double[d];
            for (String deviceId : monitoredDevicesIds) {
                double[] deviceLocation = deviceLocations.get(deviceId);
                for (int i = 0; i < d; i++) {
                    coordinates[i] += deviceLocation[i];
                }
            }
            for (int i = 0; i < d; i++) {
                coordinates[i] = coordinates[i] / cnt;
                DVector[i + d] = coordinates[i];
            }
        }

        private void getRandomize2dVectors(double randomness, int epoch) {
            // return a 2d random vector based on current value and randomness level
            for (int j = 0; j < DVector.length; j++) {
                double rangeHi = ranges[j % d];
                double rangeLow = 0.;
                double delta = 0.5 * randomness * (rangeHi - rangeLow);
                DVector[j] = randomizeValue(rangeHi, rangeLow, delta, DVector[j], id, epoch, j);
            }
        }

        private double randomizeValue(double rangeHi, double rangeLow, double delta,
                double value, String nodeId, int epoch, int dimensionId) {
            // this will return a random value within a small +-delta range centered at the
            // current value
            // also need to be within the topology range
            // the randomness is implemented by using sha1 hash function based on
            // deterministic info, so it is same
            // for all nodes
            double[] interval = getRandomInterval(rangeHi, rangeLow, delta, value);
            double newValue = getDoubleRandFromHash(nodeId, epoch, interval[0], interval[1], dimensionId);
            return newValue;
        }

        private double[] getRandomInterval(double rangeHi, double rangeLow, double delta, double value) {
            // return the true range for the random number. can be put into the
            // randomizeValue if wanted
            double start = Math.max(rangeLow, value - delta);
            double end = Math.min(rangeHi, value + delta);
            return new double[] { start, end };
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public double[] getDVector() {
            return DVector;
        }

        public double[] getLocation() {
            return location;
        }

        public String getId() {
            return id;
        }

        public void setDVector(double[] DVector) {
            this.DVector = DVector;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setLocation(double[] location) {
            this.location = location;
        }

        public Set<String> getMonitoredDevicesIds() {
            return monitoredDevicesIds;
        }

        public void setMonitoredDevicesIds(Set<String> monitoredDevicesIds) {
            this.monitoredDevicesIds = monitoredDevicesIds;
        }
    }

    class Center {
        private String type;
        private int centerIndex;
        private double[] location;
        private String centerDeviceId;
        private double[] center2dVector;
        private List<String> centerHashValsList;
        private List<String> involvedDevices;
        private Set<String> kGroup;
        private List<String> previousKGroup;
        private List<String> lshNodes = new ArrayList<>(); // the majority based on LSH: may contain randomly selected
                                                           // nodes if LSH can not give enough
        private List<String> randomNodes = new ArrayList<>();
        private List<String> orderedKGroup = new ArrayList<>();

        public Center(int centerIndex, String type) {
            this.centerIndex = centerIndex;
            this.type = type;
            centerHashValsList = new ArrayList<>();
            involvedDevices = new ArrayList<>();
            kGroup = new HashSet<>();
            previousKGroup = new ArrayList<>();
        }

        public void setPreviousKGroup(List<String> kGroup) {
            previousKGroup.clear();
            for (String id : kGroup) {
                previousKGroup.add(id);
            }
        }

        public List<String> getPreviousKGroup() {
            return previousKGroup;
        }

        public List<String> getOrderedKGroup() {
            orderedKGroup = sortLSHNodes();
            orderedKGroup.addAll(randomNodes);
            return orderedKGroup;
        }

        public List<String> sortLSHNodes() {
            List<String> temp = new ArrayList<>();
            Map<String, String> tempMap = new HashMap<>();
            for (String nodeId : lshNodes) {
                String hashVal = hash(nodeId + this.centerIndex + this.centerDeviceId);
                tempMap.put(hashVal, nodeId);
                temp.add(hashVal);
            }
            Collections.sort(temp);
            List<String> orderedNodes = new ArrayList<>();
            for (String hashVal : temp) {
                orderedNodes.add(tempMap.get(hashVal));
            }
            return orderedNodes;
        }

        public String hash(String value) {
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

        public List<String> getLshNodes() {
            return lshNodes;
        }

        public List<String> getRandomNodes() {
            return randomNodes;
        }

        public void setLshNodes(List<String> lshNodes) {
            this.lshNodes = lshNodes;
        }

        public void setRandomNodes(List<String> randomNodes) {
            this.randomNodes = randomNodes;
        }

        public void setkGroup(Set<String> kGroup) {
            this.kGroup = kGroup;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setCenter2dVector(double[] center2dVector) {
            this.center2dVector = center2dVector;
        }

        public double[] getCenter2dVector() {
            return center2dVector;
        }

        public void setCenterDeviceId(String centerDeviceId) {
            this.centerDeviceId = centerDeviceId;
        }

        public void setLocation(double[] location) {
            this.location = location;
        }

        public double[] getLocation() {
            return location;
        }

        public void setCenterHashValsList(List<String> centerHashValsList) {
            this.centerHashValsList = centerHashValsList;
        }

        public void setInvolvedDevices(List<String> involvedDevices) {
            this.involvedDevices = involvedDevices;
        }

        public List<String> getInvolvedDevices() {
            return involvedDevices;
        }

        public String getCenterDeviceId() {
            return centerDeviceId;
        }

        public Set<String> getkGroup() {
            return kGroup;
        }

        public List<String> getCenterHashValsList() {
            return centerHashValsList;
        }

        public void setCenterIndex(int centerIndex) {
            this.centerIndex = centerIndex;
        }

        public int getCenterIndex() {
            return centerIndex;
        }

        public String getType() {
            return type;
        }
    }

    public static Logger logger = Logger.getGlobal();

    private int d, F, K, nNode;
    private int seed = 0;

    private String[] dimensions;
    private double[] ranges;
    private Map<String, double[]> deviceLocations; // needed by for the 2dVector update
    public Map<String, SmartNode> nodeListMap; // id to Node
    private List<String> nodeList; // List of smart nodes
    private Map<Integer, Center> allCenters;
    private Map<String, List<String>> deviceNeighbors;
    private double[][][] aVectors;
    private double[][] bValues;
    private List<Map<String, Set<String>>> nodeBuckets;
    private LSHParams hashParams;
    private Random random;

    public Map<String, Integer> pickedTimes = new HashMap<>();
    Set<String> fullLoadNodes = new HashSet<>();

    public KMemberMix(
            String deviceTopologyDims, int F, int K, LSHParams hashParams, String devicesLocationFilename,
            String devicesTopologyFilename, List<List<String>> devClusters, List<List<String>> rtnTriggerDevs,
            List<String> nodesIDs, Map<String, Integer> nodesLimits) {

        logger.setLevel(Level.WARNING);

        seed = hashParams.getSeed();
        dimensions = deviceTopologyDims.split(",");
        ranges = initRanges(dimensions);
        d = dimensions.length;
        this.F = F;
        this.K = K;
        this.hashParams = hashParams;
        deviceLocations = initDeviceLocations(devicesLocationFilename);

        nodeListMap = new HashMap<>();
        nodeList = new ArrayList<>();

        initNodesList(nodesIDs, nodesLimits);

        nNode = this.nodeListMap.size();
        nodeBuckets = new ArrayList<>();

        random = new Random();
        random.setSeed(seed);

        aVectors = new double[this.hashParams.getL()][this.hashParams.getK()][2 * d];
        bValues = new double[this.hashParams.getL()][getHashParams().getK()];
        deviceNeighbors = new HashMap<>();
        initDeviceNeighbors(devicesTopologyFilename);
        initAVectors();
        initBValues();
        // init device k group here, and later add routine k group
        allCenters = initCenters(devClusters, 0, "device");
        // add routine k-groups: centerCount is not reset, increases from the device
        // centerCounter
        allCenters.putAll(initCenters(rtnTriggerDevs, devClusters.size(), "routine"));
    }

    public void getKMembersNewEpoch(int epoch, Map<String, Membership> membershipList) {
        // for each new epoch, clear the KMember map and the node buckets.
        nodeBuckets.clear();
        for (int centerIndex : allCenters.keySet()) {
            allCenters.get(centerIndex).setPreviousKGroup(allCenters.get(centerIndex).getOrderedKGroup());
        }

        for (int index : allCenters.keySet()) {
            allCenters.get(index).getkGroup().clear();
            allCenters.get(index).orderedKGroup.clear();
            allCenters.get(index).lshNodes.clear();
            allCenters.get(index).randomNodes.clear();
        }

        // update node's 2d Vector
        for (String nodeId : nodeListMap.keySet()) {
            nodeListMap.get(nodeId).updateDVector(hashParams.getRandomness(), epoch);
        }
        nodeBuckets = updateBuckets(membershipList, nodeListMap, nodeBuckets);
        updateDeviceCentersNewEpoch(epoch);
        getKGroups(epoch, membershipList);
        updateInvolvedDevices();
    }

    private void updateDeviceCentersNewEpoch(int epoch) {
        for (Center center : allCenters.values()) {
            setCenter(center, center.getInvolvedDevices(), center.getInvolvedDevices().size(), epoch);
        }
    }

    public Map<Integer, Center> initCenters(List<List<String>> deviceClusters, int centerIndex, String type) {
        // log("groups: " + deviceClusters.size());
        Map<Integer, Center> centers = new HashMap<>();
        for (List<String> devicesList : deviceClusters) {
            Center center = new Center(centerIndex, type);
            setCenter(center, devicesList, devicesList.size(), -1);
            centers.put(centerIndex, center);
            centerIndex++;
        }
        return centers;
    }

    // currently OK, may need change if range can not be inferred from dimensions
    private double[] initRanges(String[] dimensions) {
        double[] ranges = new double[dimensions.length];
        for (int i = 0; i < dimensions.length; i++) {
            ranges[i] = Double.parseDouble(dimensions[i]);
        }
        return ranges;
    }

    // initiate deviceLocation: need devicesLocationFilename
    private Map<String, double[]> initDeviceLocations(String devicesLocationFilename) {
        Map<String, double[]> locations = new HashMap<>();
        try {
            File myObj = new File(devicesLocationFilename);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String[] data = myReader.nextLine().split(" ");
                double[] temp = new double[3];

                for (int j = 1; j < 4; j++) {
                    temp[j - 1] = Double.parseDouble(data[j]);
                }

                locations.put(data[0], temp);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            Network.log("An error occurred when reading devices location file.");
            e.printStackTrace();
        }
        return locations;
    }

    private void initDeviceNeighbors(String topologyFile) {
        try {
            File myObj = new File(topologyFile);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String[] data = myReader.nextLine().split(" ");
                List<String> neighbors = new ArrayList<>();
                for (int i = 1; i < data.length; i++) {
                    neighbors.add(data[i]);
                }
                deviceNeighbors.put(data[0], neighbors);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            Network.log("An error occurred when reading topology file.");
            e.printStackTrace();
        }
    }

    private void initNodesList(List<String> nodesIDs, Map<String, Integer> nodesLimits) {
        for (String id : nodesIDs) {
            SmartNode node = new SmartNode(id, nodesLimits.get(id));
            node.setLocation(deviceLocations.get(id));
            node.setDVector(generateDVector(deviceLocations.get(id)));
            nodeListMap.put(id, node);
            nodeList.add(id);
        }
    }

    private double[] generateDVector(double[] dVector) {
        double[] _2dVector = new double[d * 2];
        System.arraycopy(dVector, 0, _2dVector, 0, d);
        System.arraycopy(dVector, 0, _2dVector, d, d);
        return _2dVector;
    }

    private void initAVectors() {
        for (int i = 0; i < hashParams.getL(); i++) {
            for (int j = 0; j < hashParams.getK(); j++) {
                for (int t = 0; t < 2 * d; t++) {
                    // get next random number from N(mean, std)
                    aVectors[i][j][t] = Math.sqrt(hashParams.getStd()) * random.nextGaussian() + hashParams.getMean();
                }
            }
        }
    }

    private void initBValues() {
        Random random = new Random();
        random.setSeed(hashParams.getSeed());
        for (int i = 0; i < hashParams.getL(); i++) {
            for (int j = 0; j < hashParams.getK(); j++) {
                bValues[i][j] = random.nextDouble() * hashParams.getR();
            }
        }
    }

    private List<Map<String, Set<String>>> updateBuckets(Map<String, Membership> membershipList,
            Map<String, SmartNode> nodeListMap,
            List<Map<String, Set<String>>> buckets) {
        for (int l = 0; l < hashParams.getL(); l++) {
            Map<String, Set<String>> oneBucketMaps = new HashMap<>();
            double[] _2dVector = null;
            for (String id : nodeListMap.keySet()) {
                // skip the node if it offline
                if (membershipList.get(id).equals(Membership.OFFLINE))
                    continue;
                _2dVector = nodeListMap.get(id).getDVector();
                List<Integer> hashVals = new ArrayList<>();
                for (int i = 0; i < hashParams.getK(); i++) {
                    hashVals.add(getLSHVal(aVectors[l][i], bValues[l][i], hashParams.getR(), _2dVector));
                }
                String bucket = hashVals.toString();
                Set<String> indexSet = oneBucketMaps.getOrDefault(bucket, new HashSet<String>());
                indexSet.add(id);
                oneBucketMaps.put(bucket, indexSet);
            }
            buckets.add(oneBucketMaps);
        }
        return buckets;
    }

    private void getKGroups(int epoch, Map<String, Membership> membershipList) {
        // 1. hash the center;
        // 2 find the nodes that in the same buckets with the center;
        // 3. get exact K
        logger.info("--------------------------epoch---------------------------");
        pickedTimes.clear();
        fullLoadNodes.clear();
        for (int centerIndex : allCenters.keySet()) {
            Center center = allCenters.get(centerIndex);
            Set<String> nodesInSameBuckets = getNodeIdsInSameBuckets(center.getCenterHashValsList());
            center.setkGroup(getExactKMember(center.getCenterDeviceId(), center.getLocation(),
                    nodesInSameBuckets, epoch, centerIndex, membershipList, fullLoadNodes));
            for (String node : center.getkGroup()) {
                pickedTimes.put(node, pickedTimes.getOrDefault(node, 0) + 1);
                if (pickedTimes.get(node) >= nodeListMap.get(node).getLimit()) {
                    fullLoadNodes.add(node);
                }
            }
        }
    }

    private void updateInvolvedDevices() {
        for (String node : nodeListMap.keySet()) {
            nodeListMap.get(node).getMonitoredDevicesIds().clear();
        }
        for (int centerIndex : allCenters.keySet()) {
            for (String nodeId : allCenters.get(centerIndex).getkGroup()) {
                nodeListMap.get(nodeId).getMonitoredDevicesIds()
                        .addAll(allCenters.get(centerIndex).getInvolvedDevices());
            }
        }
    }

    private Set<String> getNodeIdsInSameBuckets(List<String> myBuckets) {
        Set<String> smartNodes = new HashSet<>();
        for (int l = 0; l < nodeBuckets.size(); l++) {
            if (nodeBuckets.get(l).containsKey(myBuckets.get(l))) {
                smartNodes.addAll(nodeBuckets.get(l).get(myBuckets.get(l)));
            }
        }
        return smartNodes;
    }

    private Set<String> getExactKMember(String centerDeviceId, double[] targetLocation, Set<String> nodesInSameBuckets,
            int epoch,
            int centerIndex, Map<String, Membership> membershipList, Set<String> fullLoadNodes) {
        /* this method will return exact K group members */
        Iterator<String> it = nodesInSameBuckets.iterator();
        while (it.hasNext()) {
            String node = it.next();
            if (fullLoadNodes.contains(node)) {
                it.remove();
            }
        }
        Set<String> kMembers = getMajority(centerDeviceId, targetLocation, nodesInSameBuckets, epoch, centerIndex,
                membershipList, fullLoadNodes);
        if (kMembers.size() != F + 1) {
            Network.log(
                    "majority not exact F + 1, epoch " + epoch + ", near members so far: " + kMembers);
        }
        List<String> lshNodes = new ArrayList<>();
        for (String id : kMembers) {
            lshNodes.add(id);
        }
        allCenters.get(centerIndex).setLshNodes(lshNodes);
        Set<String> curMembers = new HashSet<>();
        for (String id : kMembers) {
            curMembers.add(id);
        }
        kMembers = getNodesRandomly(curMembers, epoch, centerIndex, K,
                "minority", membershipList, fullLoadNodes);
        return kMembers;
    }

    private Set<String> getMajority(String centerDeviceId, double[] targetLocation,
            Set<String> nodesInSameBuckets, int epoch, int centerIndex,
            Map<String, Membership> membershipList, Set<String> fullLoadNodes) {
        /*
         * this method returns the majority of the k-group based on lsh. May contain
         * some nodes borrowed from its
         * neighbors or even random ones if its own is not enough ( < F + 1 )
         */
        Set<String> kMembers = new HashSet<>();
        if (nodesInSameBuckets.size() >= F + 1) {
            kMembers = getNearestMajority(centerDeviceId, targetLocation, nodesInSameBuckets, fullLoadNodes);
        } else {
            kMembers = borrowerFromNeighbors(centerDeviceId, targetLocation, nodesInSameBuckets, epoch, centerIndex,
                    membershipList, fullLoadNodes);
        }
        return kMembers;
    }

    private Set<String> borrowerFromNeighbors(String centerDeviceId, double[] targetLocation,
            Set<String> nodesInSameBuckets, int epoch,
            int centerIndex, Map<String, Membership> membershipList, Set<String> fullLoadNodes) {
        /*
         * this method is for borrowing k-group members from one's neighbor's bucket. If
         * still not enough (< F + 1) after
         * check its all neighors, get some random ones
         */
        String deviceId = allCenters.get(centerIndex).getCenterDeviceId();
        Set<String> curNodes = new HashSet<>();
        curNodes.addAll(nodesInSameBuckets);
        Set<String> kMembers = new HashSet<>();
        for (String neighbor : deviceNeighbors.get(deviceId)) {
            List<String> neighborBuckets = getHashForOneVector(generateDVector(deviceLocations.get(neighbor)));
            curNodes.addAll(getNodeIdsInSameBuckets(neighborBuckets));
            Iterator<String> it = curNodes.iterator();
            while (it.hasNext()) {
                String node = it.next();
                if (fullLoadNodes.contains(node)) { // double check if need check offline
                    it.remove();
                }
            }

            if (curNodes.size() >= F + 1) {
                kMembers = getNearestMajority(centerDeviceId, targetLocation, curNodes, fullLoadNodes);
                return kMembers;
            }
        }
        if (curNodes.size() < F + 1) {
            String input = "neighbors not enough ";
            kMembers = getNodesRandomly(curNodes, epoch, centerIndex, F + 1, input, membershipList, fullLoadNodes);
        }
        return kMembers;
    }

    private Set<String> getNearestMajority(String centerDeviceId, double[] target, Set<String> nodesInSameBuckets,
            Set<String> fullLoadNodes) {
        /*
         * this method will choose the nearest F + 1 from the bucket if it contains more
         * than F + 1 nodes.
         * The input bucket size needs to be >= F + 1
         */
        if (nodesInSameBuckets.size() == F + 1)
            return nodesInSameBuckets;
        List<DeviceNodeDis> deviceNodeDis = new ArrayList<>();
        for (String nodeID : nodesInSameBuckets) {
            if (nodeID.equals(centerDeviceId)) {
                continue;
            }
            double dis = getDistance(target, nodeListMap.get(nodeID).getLocation());
            deviceNodeDis.add(new DeviceNodeDis(nodeID, dis));
        }
        Collections.sort(deviceNodeDis, (a, b) -> Double.compare(a.getDis(), b.getDis()));
        Set<String> majorityKMembers = new HashSet<>();
        for (int i = 0; i < F + 1; i++) {
            majorityKMembers.add(deviceNodeDis.get(i).getNodeID());
        }
        return majorityKMembers;
    }

    // todo: Now is here during cleaning and adding comments
    private Set<String> getNodesRandomly(Set<String> curMembers, int epoch, int centerIndex,
            int upto, String type, Map<String, Membership> membershipList, Set<String> fullLoadNodes) {
        int cnt = 0;
        List<String> randNodes = new ArrayList<>();
        Set<String> result = new HashSet<>();
        Set<String> tried = new HashSet<>();
        while (curMembers.size() < upto) {
            if (tried.containsAll(nodeList))
                return curMembers;
            type = type + cnt;
            String value = centerIndex + " " + epoch + " " + cnt + " " + type;
            String newNode = nodeList.get(getARandomIndex(value, nNode));
            cnt++;
            tried.add(newNode);
            if (membershipList.get(newNode).equals(Membership.OFFLINE)
                    || fullLoadNodes.contains(newNode) || curMembers.contains(newNode)) {
                continue;
            } else {
                curMembers.add(newNode);
                randNodes.add(newNode);
                result.add(newNode);
            }
        }
        // todo : double check
        allCenters.get(centerIndex).setRandomNodes(randNodes);
        return curMembers;
    }

    // this is the same for both device centers and routine centers. if need
    // different, change
    private void setCenter(Center center, List<String> monitoredDevices, int N, int epoch) {
        if (center.getInvolvedDevices() == null || center.getInvolvedDevices().size() == 0) {
            center.setInvolvedDevices(monitoredDevices);
        }
        // randomly select a device in this group as a center: return a index [0, N-1]
        // log("should increase centerindex: " + center.getCenterIndex());
        int centerDeviceId = getARandomIndex(center.getCenterIndex() + center.getType() + epoch, N);
        // int index = (N == nDevice) ? centerDeviceId :
        // Integer.parseInt(monitoredDevices.get(centerDeviceId));
        String index = monitoredDevices.get(centerDeviceId);
        center.setCenterDeviceId(index);
        center.setLocation(deviceLocations.get(index));
        if (center.getLocation() == null)
            Network.log(index + " " + deviceLocations.get(index), true);
        center.setCenter2dVector(generateDVector(center.getLocation()));
        center.setCenterHashValsList(getHashForOneVector(center.getCenter2dVector()));
    }

    private int getARandomIndex(String value, int N) {
        // since we do not hash devices anymore, we randomly choose a smart node as a
        // center
        int nodeID = -1;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            digest.update(value.getBytes("utf8"));
            nodeID = new BigInteger(1, digest.digest()).mod(BigInteger.valueOf(N)).intValue();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return nodeID;
    }

    private List<String> getHashForOneVector(double[] vector) {
        /* given a vector, return the hash value list using a, b, r */
        List<String> hashValsList = new ArrayList<>();
        for (int l = 0; l < aVectors.length; l++) {
            List<Integer> hashVals = new ArrayList<>();
            for (int k = 0; k < aVectors[0].length; k++) {
                hashVals.add(getLSHVal(aVectors[l][k], bValues[l][k], hashParams.getR(), vector));
            }
            String bucket = hashVals.toString();
            hashValsList.add(bucket);
        }
        return hashValsList;
    }

    private double getDistance(double[] v1, double[] v2) {
        /* return the distance between tow same dimensional vectors */
        double dis = 0.;
        assert v1.length == v2.length;
        for (int i = 0; i < v1.length; i++) {
            dis += Math.pow((v1[i] - v2[i]), 2);
        }
        dis = Math.sqrt(dis);
        return dis;
    }

    private double getDoubleRandFromHash(String nodeIndex, int epoch, double intervalLow, double intervalHi,
            int dimensionId) {
        String value = "Randomize 2dVector " + nodeIndex + epoch + dimensionId;
        double result = 0.;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            digest.update(value.getBytes("utf8"));
            result = new BigInteger(1, digest.digest()).doubleValue() / Math.pow(2, 160);
            result = intervalLow + (intervalHi - intervalLow) * result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private int getLSHVal(double[] aVector, double bValue, int r, double[] v) {
        assert aVector.length == v.length;
        int hashVal = 0;
        for (int i = 0; i < v.length; i++) {
            hashVal += aVector[i] * v[i];
        }
        hashVal = (int) ((hashVal + bValue) / r);
        return hashVal;
    }

    public Map<Integer, Center> getAllCenters() {
        return allCenters;
    }

    public int getD() {
        return d;
    }

    public LSHParams getHashParams() {
        return hashParams;
    }

    public void setD(int d) {
        this.d = d;
    }
}