package kgroup.state;

import java.io.Serializable;

public class LockRequest implements Serializable {
    private String rtnID;
    private int rtnSeqNo;

    public LockRequest(String rtnID, int rtnSeqNo) {
        this.rtnID = rtnID;
        this.rtnSeqNo = rtnSeqNo;
    }

    public LockRequest(LockRequest rq) {
        this.rtnID = rq.rtnID;
        this.rtnSeqNo = rq.rtnSeqNo;
    }

    public String getRtnID() {
        return rtnID;
    }

    public int getRtnSeqNo() {
        return rtnSeqNo;
    }

    public boolean contains(String rtnID, int rtnSeqNo) {
        if (this.rtnID.equals(rtnID) && this.rtnSeqNo == rtnSeqNo) {
            return true;
        }
        return false;
    }

    public boolean equals(LockRequest rq) {
        if (this.rtnID.equals(rq.rtnID) && this.rtnSeqNo == rq.rtnSeqNo) {
            return true;
        }
        return false;
    }

    public String toString() {
        return rtnID + "-" + rtnSeqNo;
    }
}
