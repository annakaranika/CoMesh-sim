package dijkstra;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Node {
    
    private String ID;
    
    private List<Node> shortestPath = new LinkedList<>();
    
    private Float distance = Float.MAX_VALUE;
    
    Map<Node, Float> adjacentNodes = new HashMap<>();
 
    public Node(String ID) {
        this.ID = ID;
    }
    
    public void addDestination(Node destination, float distance) {
        adjacentNodes.put(destination, distance);
    }

    public void removeDestination(Node destination) {
        adjacentNodes.remove(destination);
    }

    public String getID() {
        return ID;
    }

    public void setName(String ID) {
        this.ID = ID;
    }

    public Map<Node, Float> getAdjacentNodes() {
        return adjacentNodes;
    }

    public Set<String> getAdjacentNodesIDs() {
        Set<String> IDs = new HashSet<>();
        for (Node node: adjacentNodes.keySet()) {
            IDs.add(node.getID());
        }
        return IDs;
    }

    public void setAdjacentNodes(Map<Node, Float> adjacentNodes) {
        this.adjacentNodes = adjacentNodes;
    }

    public Float getDistance() {
        return distance;
    }

    public void setDistance(Float distance) {
        this.distance = distance;
    }

    public List<Node> getShortestPath() {
        return shortestPath;
    }

    public void setShortestPath(LinkedList<Node> shortestPath) {
        this.shortestPath = shortestPath;
    }

    @Override
    public String toString() {
        String result = "";
            for (Node current: shortestPath) {
                result += current.getID() + "-" + current.getDistance() + " -> ";
            }

            result += getID() + "-" + getDistance();
            return result;
    }
}