package kgroup;

public enum DevClusterPolicy {
    RANDOM("rc"),
    LOCALITY("lc");

    public String name;

    DevClusterPolicy(String name) { this.name = name; }
}
