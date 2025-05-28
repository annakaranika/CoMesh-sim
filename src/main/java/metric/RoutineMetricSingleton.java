package metric;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import network.Network;

public class RoutineMetricSingleton {
    private static RoutineMetricSingleton singleton;

    private RoutineMetricSingleton() {
    }

    public static synchronized RoutineMetricSingleton getInstance() {
        if (RoutineMetricSingleton.singleton == null) {
            RoutineMetricSingleton.singleton = new RoutineMetricSingleton();
        } else {
            singleton._dev_records.clear();
        }
        return RoutineMetricSingleton.singleton;
    }

    class RtnTimeRecord {
        private int _pre_rtn_lock_release = -1;
        // trigger related time
        private int _trigger_usr_time = -1;
        private int _trigger_sys_time = -1;
        private int _trigger_ack_time = -1;
        // execution start related time
        private int _exe_start_time = -1;
        // rtn finish related time
        private int _lock_release_time = -1;
        // The time that locks has been requested
        // private boolean _all_lock_req_sent = false;
        private Map<String, Integer> _lock_req_sent_time = new HashMap<>();

        public RtnTimeRecord() {
        }

        public void clearAll() {
            _pre_rtn_lock_release = -1;
            _trigger_usr_time = -1;
            _trigger_sys_time = -1;
            _trigger_ack_time = -1;
            _exe_start_time = -1;
            _lock_release_time = -1;
            // _all_lock_req_sent = false;
            _lock_req_sent_time.clear();
        }

        public void setPreRtnLockRelease(int time) {
            _pre_rtn_lock_release = time;
        }

        public void setTriggerUsrTime(int time) {
            _trigger_usr_time = time;
        }

        public void setTriggerSysTime(int time) {
            _trigger_sys_time = time;
        }

        public void setTriggerAckTime(int time) {
            _trigger_ack_time = time;
        }

        public void setStartExecutionTime(int time) {
            _exe_start_time = time;
        }

        public void setLockReleaseTime(int time) {
            _lock_release_time = time;
        }

        public void setLockRequestAckTime(String dev_id, int time) {
            _lock_req_sent_time.put(dev_id, time);
        }
        // public void allLockRequestAckReceived() { _all_lock_req_sent = true; }

        public int getTriggerUsrTime() {
            return _trigger_usr_time;
        }

        public int getTriggerSysTime() {
            return _trigger_sys_time;
        }

        public int getTriggerAckTime() {
            return _trigger_ack_time;
        }

        public int getStartExecutionTime() {
            return _exe_start_time;
        }

        public int getLockReleaseTime() {
            return _lock_release_time;
        }

        public int getClientDelay(String type) {
            // Get client delay with different trigger time.
            int target_trigger_time;
            if (type.equals("usr")) {
                target_trigger_time = _trigger_usr_time;
            } else if (type.equals("ack")) {
                target_trigger_time = _trigger_ack_time;
            } else {
                target_trigger_time = _trigger_sys_time;
            }

            if (target_trigger_time < 0 || _exe_start_time < 0) {
                return -1;
            } else if (target_trigger_time > _exe_start_time) {
                Network.log("[ERROR] #TRCD trigger time later than execution start time!");
                return -2;
            } else {
                return _exe_start_time - target_trigger_time;
            }
        }

        public int getSyncDelay() {
            if (_pre_rtn_lock_release == -1) {
                return -1;
            }
            return _exe_start_time - _pre_rtn_lock_release;
        }

        public String stringForClientDelays() {
            return String.format("%d,%d,%d",
                    getClientDelay("usr"),
                    getClientDelay("sys"),
                    getClientDelay("ack"));
        }

        public String stringForSyncDelay() {
            return Integer.toString(getSyncDelay());
        }

        public String StringMinAvgMaxLockRequestAckTime() {
            Collection<Integer> times = _lock_req_sent_time.values();
            if (times.size() == 0) {
                return "-1,-1.0,-1";
            }
            double avg_time = times.stream()
                    .mapToDouble(Integer::doubleValue).average().orElse(0);
            return String.format("%d,%f,%d",
                    Collections.min(times) - _trigger_usr_time,
                    avg_time - _trigger_usr_time,
                    Collections.max(times) - _trigger_usr_time);
        }
    }

    class DevTimeRecord {
        private int _release_req_time = -1;

        public DevTimeRecord() {
        }

        public void setReleaseReqTime(int time) {
            _release_req_time = time;
        }

        public int getReleaseReqTime() {
            return _release_req_time;
        }
    }

    private final HashMap<String, HashMap<Integer, RtnTimeRecord>> _all_records = new HashMap<>();
    private final HashMap<String, DevTimeRecord> _dev_records = new HashMap<>();

    public void recordTriggerUsrTime(String routine_id, int seq_no, int time) {
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setTriggerUsrTime(time);
    }

    public void recordTriggerSysTime(String routine_id, int seq_no, int time) {
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setTriggerSysTime(time);
    }

    public void recordTriggerAckTime(String routine_id, int seq_no, int time) {
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setTriggerAckTime(time);
    }

    public void clearRecords() {
        for (String rtn_id : _all_records.keySet()) {
            for (int seq_no : _all_records.get(rtn_id).keySet()) {
                _all_records.get(rtn_id).get(seq_no).clearAll();
            }
            _all_records.get(rtn_id).clear();
        }
        _all_records.clear();
    }

    public void recordLockRequestAckTime(String routine_id, String dev_id, int seq_no, int time) {
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setLockRequestAckTime(dev_id, time);
    }

    public void recordAllLockRequestAckReceived(String routine_id, int seq_no) {
        // Currently is called when all devices are locked.
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }

    }

    public void recordStartExecutionTime(String routine_id, int seq_no, int time) {
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setStartExecutionTime(time);
    }

    public void recordRtnLockReleaseTime(String routine_id, int seq_no, int time) {
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setLockReleaseTime(time);
    }

    public void updateRtnPreRtnLockRelease(String routine_id, int seq_no, List<String> dev_ids) {
        // Get the last lock release time of routine_id touched devices
        int max_lock_release_ts = -1;
        for (String dev : dev_ids) {
            int release_ts = _dev_records.getOrDefault(dev, new DevTimeRecord()).getReleaseReqTime();
            max_lock_release_ts = Math.max(max_lock_release_ts, release_ts);
        }
        Network.log("Routine " + routine_id + "-" + seq_no + " recording prelock time as " + max_lock_release_ts);
        // Record the this lock release time to routine.
        if (!_all_records.containsKey(routine_id)) {
            _all_records.put(routine_id, new HashMap<>());
        }
        if (!_all_records.get(routine_id).containsKey(seq_no)) {
            _all_records.get(routine_id).put(seq_no, new RtnTimeRecord());
        }
        _all_records.get(routine_id).get(seq_no).setPreRtnLockRelease(max_lock_release_ts);
    }

    public void printDevLockReleaseMap(List<String> dev_ids) {
        Network.log("Dev lock release time after recording:   ");
        for (String dev : dev_ids) {
            Network.log("   " + dev + ":" +
                    _dev_records.getOrDefault(dev, new DevTimeRecord()).getReleaseReqTime());
        }
    }

    public void recordMultiDevLockReleaseTime(List<String> dev_ids, int time) {
        for (String dev : dev_ids) {
            recordDevLockReleaseTime(dev, time);
        }
    }

    public void recordDevLockReleaseTime(String dev_id, int time) {
        if (!_dev_records.containsKey(dev_id)) {
            _dev_records.put(dev_id, new DevTimeRecord());
        }
        _dev_records.get(dev_id).setReleaseReqTime(time);
    }

    public List<Integer> getClientDelay() {
        return getClientDelay("sys");
    }

    public List<Integer> getClientDelay(String type) {
        List<Integer> delays = new ArrayList<>();
        for (Map.Entry<String, HashMap<Integer, RtnTimeRecord>> entry : _all_records.entrySet()) {
            for (Integer seq : entry.getValue().keySet()) {
                int time = entry.getValue().get(seq).getClientDelay(type);
                if (time == -1) {
                    Network.log("[ERROR] #RMCLND Routine " + entry.getKey() + " seqNo " + seq + " lack of ts");
                } else if (time == -2) {
                    Network.log("[ERROR] #RMCLND Routine " + entry.getKey() +
                            " seqNo " + seq + " execution ts earlier than trigger ts");
                } else {
                    delays.add(time);
                }
            }
        }
        return delays;
    }

    public List<Integer> getSyncDelay() {
        List<Integer> delays = new ArrayList<>();
        for (Map.Entry<String, HashMap<Integer, RtnTimeRecord>> entry : _all_records.entrySet()) {
            for (Integer seq : entry.getValue().keySet()) {
                int time = entry.getValue().get(seq).getSyncDelay();
                if (time == -1) {
                    Network.log("[ERROR] #RMSYND Routine " + entry.getKey() + " seqNo " + seq + " lack of ts");
                } else {
                    delays.add(time);
                }
            }
        }
        return delays;
    }

    private <T> List<List<T>> transposeList(List<List<T>> list) {
        final int N = list.stream().mapToInt(l -> l.size()).max().orElse(-1);
        List<Iterator<T>> iterList = list.stream().map(it -> it.iterator()).collect(Collectors.toList());
        return IntStream.range(0, N)
                .mapToObj(n -> iterList.stream()
                        .filter(it -> it.hasNext())
                        .map(m -> m.next())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public List<List<Integer>> getClientDelayAllTypes(Boolean transpose) {
        if (transpose) {
            return transposeList(getClientDelayAllTypes());
        }
        return getClientDelayAllTypes();
    }

    public List<List<Integer>> getClientDelayAllTypes() {
        List<List<Integer>> all_delays = new ArrayList<>();
        all_delays.add(getClientDelay("usr"));
        all_delays.add(getClientDelay("sys"));
        all_delays.add(getClientDelay("ack"));
        return all_delays;
    }

    public List<String> getClientDelaysInString() {
        List<String> delays = new ArrayList<>();
        for (Map.Entry<String, HashMap<Integer, RtnTimeRecord>> entry : _all_records.entrySet()) {
            for (Integer seq : entry.getValue().keySet()) {
                String delay = entry.getValue().get(seq).stringForClientDelays();
                delay = entry.getKey() + "," + seq + "," + delay;
                delays.add(delay);
            }
        }
        return delays;
    }

    public List<String> getSyncDelayInString() {
        List<String> delays = new ArrayList<>();
        for (Map.Entry<String, HashMap<Integer, RtnTimeRecord>> entry : _all_records.entrySet()) {
            for (Integer seq : entry.getValue().keySet()) {
                String delay = entry.getValue().get(seq).stringForSyncDelay();
                delay = entry.getKey() + "," + seq + "," + delay;
                delays.add(delay);
            }
        }
        return delays;
    }

    public List<String> getLockRequestAckTimeInString() {
        List<String> delays = new ArrayList<>();
        for (Map.Entry<String, HashMap<Integer, RtnTimeRecord>> entry : _all_records.entrySet()) {
            for (Integer seq : entry.getValue().keySet()) {
                String lr_delay = entry.getValue().get(seq).StringMinAvgMaxLockRequestAckTime();
                String client_delay = entry.getValue().get(seq).stringForClientDelays();
                Network.log("Client delay string is " + client_delay);
                String delay = entry.getKey() + "," + seq + "," + lr_delay + "," + client_delay;
                delays.add(delay);
            }
        }
        return delays;
    }
}
