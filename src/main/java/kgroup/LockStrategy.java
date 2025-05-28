package kgroup;

public enum LockStrategy {
    SERIAL("sr"),
    PARALLEL("pl");

    public String name;

    LockStrategy(String name) { this.name = name; }
}
