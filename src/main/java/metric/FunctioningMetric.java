package metric;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class FunctioningMetric {
    private static FunctioningMetric functioningMetricSingleton = new FunctioningMetric();

    private class NodeKGroupInfo {
        private int failedNodes, activeKGrps;

        private NodeKGroupInfo(int failedNodes, int activeKGrps) {
            this.failedNodes = failedNodes;
            this.activeKGrps = activeKGrps;
        }

        @Override
        public String toString() {
            return failedNodes + "," + activeKGrps;
        }
    }
    
    private static final Map<Integer, NodeKGroupInfo> _timeseries = new HashMap<>();

    public static void clear() { _timeseries.clear(); }

    public static void record(int ts, int failedNodes, int activeKGrps) {
        _timeseries.put(
            ts, functioningMetricSingleton.new NodeKGroupInfo(failedNodes, activeKGrps)
        );
    }

    private static String stringifyEntry(Entry<Integer, NodeKGroupInfo> e) {
        return e.getKey() + "," + e.getValue().toString();
    }

    public static String stringifyTimeseries() {
        return "ts,failed nodes,active k-groups\n" +
               _timeseries.entrySet().stream()
                          .map(FunctioningMetric::stringifyEntry)
                          .collect(Collectors.joining("\n"));
    }
}
