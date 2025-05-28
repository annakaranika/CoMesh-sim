package network.message.payload.lock;

import java.io.Serial;
import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class RoutineKGroupLeaderInfoMessagePayload extends ReplyMessagePayload {
    @Serial
    private static final long serialVersionUID = -1694369111580051543L;

    public String leaderNodeID;

    public RoutineKGroupLeaderInfoMessagePayload(
            int epochNo, List<String> devID, int srcSeqNo, String leaderNodeID) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
        this.leaderNodeID = leaderNodeID;
    }

    public RoutineKGroupLeaderInfoMessagePayload(
            int epochNo, String devID, int srcSeqNo, String leaderNodeID) {
        super(epochNo, KGroupType.DEVICE, devID, srcSeqNo);
        this.leaderNodeID = leaderNodeID;
    }
}
