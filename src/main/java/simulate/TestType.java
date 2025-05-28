package simulate;

public enum TestType {
    INKGROUP_BENCHMARK_WO_FAILURE, // Quorum time, leader election time, state
                                   // transfer time, bandwidth - 1 device k-group
    INKGROUP_BENCHMARK_W_FAILURE, // Quorum time after (leader) failure - 1 k-group
    INKGROUP_FAILURE_SCHEDULE, // timeline after a failure happens
    CLIENT_DELAY, // time units till entry since trigger
                  // - 1 routine + 1 device k-group
    SYNC_DELAY, // time units till entry since another routine starts to exit
                // - 1 device + 1 routine k-group with 2 triggerings of the same routine
    DELAY_BREAKDOWN, // time units till entry since trigger
                     // - 1 routine + 1 device k-group
    BANDWIDTH, // messages for entry and exit - 1 device and 1 routine k-group
    BANDWIDTH_BG, // messages for entry and exit - 1 device and 1 routine k-group
    BALANCING, // distribution of k-group membership and message sending
               // - several (10?) device and several (10?) routine k-groups
    LOCKING_TIME, // time until routine executes since triggered vs number of
                  // devices / routines triggered but not executing yet
                  // - 1 device + 1 routine (several triggerings of the same
                  // routine) k-groups
    CENTRALIZED,
    CHURN,
    ;
}
