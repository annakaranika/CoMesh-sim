package metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import kgroup.KGroup;
import network.Network;

public class KGroupMetric {
    private enum OperationKind {
        QUORUM,
        LEADER_ELECTION,
        STATE_TRANSFER;
    }

    private static KGroupMetric kGroupMetricSingleton = new KGroupMetric();

    private class OperationMetric {
        private int startTS, endTS = -1;
        private List<String> members = new ArrayList<>();
        private String leaderID;
        private final List<Integer> seqNos = new ArrayList<>();

        public OperationMetric(KGroup kgrp, int startTS, int seqNo) {
            members.addAll(kgrp.getCurMemberIDs());
            this.startTS = startTS;
            seqNos.add(seqNo);
        }

        public boolean containsSeqNo(int seqNo) {
            return seqNos.contains(seqNo);
        }

        public void addSeqNo(int seqNo) {
            seqNos.add(seqNo);
        }

        public int recordEndTS(int endTS, String leaderID) {
            this.endTS = endTS;
            this.leaderID = String.valueOf(leaderID);
            return getDelay();
        }

        public boolean existsEnd() {
            return endTS != -1;
        }

        public int getDelay() {
            return endTS - startTS;
        }

        public String toString() {
            return String.join(" ", members) + "," + leaderID + "," + getDelay();
        }
    }

    private class KGroupMetricNode {
        private final Map<Integer, List<OperationMetric>> _quorumDelays = new HashMap<>();
        private final Map<Integer, List<OperationMetric>> _ldrElctnDelays = new HashMap<>();
        private final Map<Integer, List<OperationMetric>> _stateTrnsfrDelays = new HashMap<>();

        public KGroupMetricNode() {
        }

        public Map<Integer, List<OperationMetric>> getDelays(OperationKind kind) {
            switch (kind) {
                case QUORUM:
                    return _quorumDelays;
                case LEADER_ELECTION:
                    return _ldrElctnDelays;
                case STATE_TRANSFER:
                    return _stateTrnsfrDelays;
                default:
                    return null;
            }
        }

        public OperationMetric getMetric(
                int epochNo, int seqNo, Map<Integer, List<OperationMetric>> metrics) {
            for (OperationMetric curr : metrics.get(epochNo)) {
                if (curr.containsSeqNo(seqNo))
                    return curr;
            }
            return null;
        }

        public List<String> stringifyDelays(OperationKind kind) {
            int epochNo;
            List<OperationMetric> opList;
            List<String> lines = new ArrayList<>();
            Map<Integer, List<OperationMetric>> delays = getDelays(kind);
            for (Entry<Integer, List<OperationMetric>> entry : delays.entrySet()) {
                epochNo = entry.getKey();
                opList = entry.getValue();
                for (OperationMetric opMetric : opList) {
                    if (opMetric.existsEnd())
                        lines.add(epochNo + "," + opMetric + "\n");
                }
            }
            return lines;
        }
    }

    private static final Map<KGroup, KGroupMetricNode> _data = new HashMap<>();

    public static void clear() {
        _data.clear();
    }

    private static Map<Integer, List<OperationMetric>> opChecks(OperationKind kind, KGroup kgrp) {
        if (kgrp == null) {
            Network.log("kgroupmetric mngr is null!!!!!");
        }
        if (!_data.containsKey(kgrp)) {
            _data.put(kgrp, kGroupMetricSingleton.new KGroupMetricNode());
        }

        Map<Integer, List<OperationMetric>> ops = _data.get(kgrp).getDelays(kind);

        if (!ops.containsKey(kgrp.getEpochNo())) {
            ops.put(kgrp.getEpochNo(), new ArrayList<>());
        }

        return ops;
    }

    private static OperationMetric getOpMetric(
            KGroup kgrp, int seqNo, Map<Integer, List<OperationMetric>> ops) {
        return _data.get(kgrp).getMetric(kgrp.getEpochNo(), seqNo, ops);
    }

    private static void startOp(OperationKind kind, KGroup kgrp, int seqNo, int startTS) {
        Map<Integer, List<OperationMetric>> ops = opChecks(kind, kgrp);

        OperationMetric opMetric = kGroupMetricSingleton.new OperationMetric(kgrp, startTS, seqNo);
        ops.get(kgrp.getEpochNo()).add(opMetric);
    }

    private static void addOpSeqNo(OperationKind kind, KGroup kgrp, int oldSeqNo, int newSeqNo) {
        Map<Integer, List<OperationMetric>> ops = opChecks(kind, kgrp);

        OperationMetric opMetric = getOpMetric(kgrp, oldSeqNo, ops);
        opMetric.addSeqNo(newSeqNo);
    }

    private static int completeOp(OperationKind kind, KGroup kgrp, int seqNo, int endTS) {
        if (kgrp == null) {
            return -1;
        }
        Map<Integer, List<OperationMetric>> ops = opChecks(kind, kgrp);

        OperationMetric opMetric = getOpMetric(kgrp, seqNo, ops);
        return opMetric.recordEndTS(endTS, kgrp.getLeader());
    }

    private static String stringifyDelays(OperationKind kind) {
        String text = "monitored,epochNo,members,leader,delay\n";
        List<String> monitored, stringDelays;
        for (Entry<KGroup, KGroupMetricNode> kgrpEntry : _data.entrySet()) {
            monitored = kgrpEntry.getKey().getMonitored();
            stringDelays = kgrpEntry.getValue().stringifyDelays(kind);
            for (String delay : stringDelays) {
                text += String.join(" ", monitored) + "," + delay;
            }
        }

        return text;
    }

    public static void startQuorum(KGroup kgrp, int seqNo, int startTS) {
        startOp(OperationKind.QUORUM, kgrp, seqNo, startTS);
    }

    public static void completeQuorum(KGroup kgrp, int seqNo, int endTS) {
        completeOp(OperationKind.QUORUM, kgrp, seqNo, endTS);
    }

    public static String stringifyQuorumDelays() {
        return stringifyDelays(OperationKind.QUORUM);
    }

    public static void startLdrElctn(KGroup kgrp, int seqNo, int startTS) {
        startOp(OperationKind.LEADER_ELECTION, kgrp, seqNo, startTS);
    }

    public static void addLdrElctnSeqNo(KGroup kgrp, int oldSeqNo, int newSeqNo) {
        addOpSeqNo(OperationKind.LEADER_ELECTION, kgrp, oldSeqNo, newSeqNo);
    }

    public static int completeLdrElctn(KGroup kgrp, int seqNo, int endTS) {
        return completeOp(OperationKind.LEADER_ELECTION, kgrp, seqNo, endTS);
    }

    public static String stringifyLdrElctnDelays() {
        return stringifyDelays(OperationKind.LEADER_ELECTION);
    }

    public static void startStateTrnsfr(KGroup kgrp, int seqNo, int startTS) {
        if (kgrp.getEpochNo() == 0)
            return;
        startOp(OperationKind.STATE_TRANSFER, kgrp, seqNo, startTS);
    }

    public static void addStateTrnsfrSeqNo(KGroup kgrp, int oldSeqNo, int newSeqNo) {
        if (kgrp.getEpochNo() == 0)
            return;
        addOpSeqNo(OperationKind.STATE_TRANSFER, kgrp, oldSeqNo, newSeqNo);
    }

    public static int completeStateTrnsfr(KGroup kgrp, int seqNo, int endTS) {
        if (kgrp.getEpochNo() == 0)
            return -1;
        return completeOp(OperationKind.STATE_TRANSFER, kgrp, seqNo, endTS);
    }

    public static String stringifyStateTrnsfrDelays() {
        return stringifyDelays(OperationKind.STATE_TRANSFER);
    }
}
