package kgroup;

public enum KGroupSelectionPolicy {
    STATIC("ss"),
    RANDOM("rs"),
    LSHMIX("ls");

    public String name;

    KGroupSelectionPolicy(String name) { this.name = name; }
}
