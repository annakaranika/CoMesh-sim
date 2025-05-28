package metric;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class LoadMetric {
    private static class NodeLoad {
        private final Map<Long, Integer> _loadHistory = new HashMap<>();

        private boolean existsLoadEntry(long ts) {
            return _loadHistory.containsKey(ts);
        }

        private int getLoadEntry(long ts) {
            return _loadHistory.get(ts);
        }

        private void addLoadEntry(long ts, int count) {
            _loadHistory.put(ts, count);
        }

        private void addLoadEntry(long ts) {
            addLoadEntry(ts, 0);
        }

        private void incLoadEntry(long ts, int count) {
            _loadHistory.put(ts, getLoadEntry(ts) + count);
        }

        private void incLoadEntry(long ts) {
            incLoadEntry(ts, 1);
        }

        private Map<Long, Integer> getLoadHistory() {
            return _loadHistory;
        }
    }

    private static final Map<String, NodeLoad> _nodeLoads = new HashMap<>();

    public static void clear() {
        _nodeLoads.clear();
    }

    public LoadMetric(Map<String, Integer> nodeLimits) {
    }

    public static void recordLoad(String nodeID, long ts, int count) {
        if (!_nodeLoads.containsKey(nodeID)) {
            _nodeLoads.put(nodeID, new NodeLoad());
        }
        _nodeLoads.get(nodeID).addLoadEntry(ts, count);
    }

    public static void recordLoad(String nodeID, long ts) {
        if (!_nodeLoads.containsKey(nodeID)) {
            _nodeLoads.put(nodeID, new NodeLoad());
        }
        if (!_nodeLoads.get(nodeID).existsLoadEntry(ts)) {
            _nodeLoads.get(nodeID).addLoadEntry(ts);
        }
        _nodeLoads.get(nodeID).incLoadEntry(ts);
    }

    public static String stringifyLoadHistories() {
        String text = "nodeID,ts,load\n";
        String nodeID;
        long ts;
        int load;
        for (Entry<String, NodeLoad> nodeLoad : _nodeLoads.entrySet()) {
            nodeID = nodeLoad.getKey();
            for (Entry<Long, Integer> loadEntry : nodeLoad.getValue().getLoadHistory().entrySet()) {
                ts = loadEntry.getKey();
                load = loadEntry.getValue();
                text += nodeID + "," + ts + "," + load + "\n";
            }
        }
        return text;
    }
}
