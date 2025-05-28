package kgroup;

public enum LeaderElectionPolicy {
    SMALLEST_ID("sID"),
    SMALLEST_HASH("sh"),
    LSH_SMALLEST_HASH("lsh"),
    CENTRAL_NODE("cn"),
    STATIC("st");

    public String name;

    private LeaderElectionPolicy(String name) {
        this.name = name;
    }
}
