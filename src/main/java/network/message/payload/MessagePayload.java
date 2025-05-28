package network.message.payload;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import device.Device;
import javafx.util.Pair;
import kgroup.KGroup;
import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import network.Network;
import network.message.MessageType;

public class MessagePayload implements Cloneable, Serializable {
    private static final long serialVersionUID = 8135931604175469785L;
    public int epochNo;
    // the following two fields specify the k-group this message is addressed to
    public KGroupType kGroupType;
    public List<String> entitiesIDs;
    public String rtnID, devID;

    public MessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs) {
        this.epochNo = epochNo;
        this.kGroupType = kGroupType;
        this.entitiesIDs = new ArrayList<String>(entitiesIDs);
    }

    public MessagePayload(int epochNo, KGroupType kGroupType, String entityID) {
        this.epochNo = epochNo;
        this.kGroupType = kGroupType;
        this.entitiesIDs = new ArrayList<String>();
        this.entitiesIDs.add(entityID);
    }

    public MessagePayload(KGroupType kGroupType, String entityID) {
        this.kGroupType = kGroupType;
        this.entitiesIDs = List.of(entityID);
    }

    public MessagePayload(KGroup kgrp) {
        this.epochNo = kgrp.getEpochNo();
        this.kGroupType = kgrp.getType();
        this.entitiesIDs = new ArrayList<String>(kgrp.getMonitored());
    }

    public MessagePayload() {
    }

    public void changeEpochNo(int newEpoch) {
        this.epochNo = newEpoch;
    }

    public int getByteSize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this.clone());
            oos.flush();
            return bos.toByteArray().length;
        } catch (Exception ex) {
            Network.log("Exception during payload byte size calculation");
            ex.printStackTrace(System.out);
            return -1;
        }
    }

    public LeaderElectionCause getLdrElctnCause() {
        return null;
    }

    public String getFailedNodeID() {
        return null;
    }

    public String getRtnID() {
        return null;
    }

    public int getRtnSeqNo() {
        return -1;
    }

    public int getReqSeqNo() {
        return -1;
    }

    public String getDevID() {
        return null;
    }

    public KGroupType getKGroupType() {
        return kGroupType;
    }

    public List<String> getMonitored() {
        return entitiesIDs;
    }

    public String getMonitored1() {
        return entitiesIDs.get(0);
    }

    // this srcSeqNo specifies the msg that will be the returned reply
    public Pair<MessageType, ReplyMessagePayload> process(
            Device node, KGroup kgrp, String src, int replySeqNo) {
        return null;
    }

    public String toString() {
        return "for " + kGroupType + " k-group monitoring " + entitiesIDs;
    }
}
