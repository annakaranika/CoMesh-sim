package kgroup.state;

public enum RoutineStageType {
    NOT_TRIGGERED,
    ACQUIRING_LOCKS,
    ACQUIRED_LOCKS,
    EXECUTING,
    RELEASING_LOCKS,
    AC_LOCK_FAILED
}
