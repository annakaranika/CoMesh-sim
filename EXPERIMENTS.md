# Running experiments and producing plots — CoMesh-sim

Simulation experiments for *There is More Control in Egalitarian Edge IoT
Meshes*. See `README.md` for build requirements (JDK 15+, Maven 3.6.3+).

## Relationship to CoMesh-on-Pis

| Repo | What |
|---|---|
| **CoMesh-sim** (here) | CoMesh simulation — the paper's simulated results |
| CoMesh-on-Pis | CoMesh deployed on real Raspberry Pis |

This simulator also has a **`smart-building` branch** that differs from `master`
in 1,544 files: it carries 1,392 pre-generated workload files that `master` does
not, and drops `master`'s `workloads/scripts/create*.py` generators. Results from
the smart-building scenario came from that branch, not this one.

## Building

```bash
mvn compiler:compile
```

## Running a single simulation

> **This was fixed on 2026-08-27.** `README.md` and `benchmarks.sh` previously
> passed `-dt`, `-ns` and `-rtt`, none of which
> the argument parser accepts. The parser's `switch` has **no `default:` case**,
> so an unrecognised flag and its value are silently ignored — the simulation
> still runs, but with the *default* topology and schedule rather than the ones
> in the command — failing quietly and producing a different experiment than the
> command implied. **Check any older saved command line against the table below
> before trusting its results.**

The stale flags map onto current ones as follows. `Simulator.java` builds its
workload filenames as
`device_topology_d<deviceNo>_<shape><dims>.txt` and
`node_schedule_d<deviceNo>_np<np>_cp<churn>_<nodeScheduleDistribution>...`,
which is what identifies the mapping:

| Stale flag | Replace with | Why |
|---|---|---|
| `-dt grid3,3` | `-dts grid -dtd 3,3` | shape and dimensions are now separate flags |
| `-ns cp0.0_uniform` | `-cp 0.0 -nsd uniform` | that value encoded churn probability and schedule distribution together |
| `-rtt 5` | `-owd <value>` | only a one-way delay flag exists now; **confirm the intended value**, since one-way delay is not the same quantity as a round-trip time |

So the README's example becomes:

```bash
mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dts grid -dtd 3,3 -np 0.4 -cp 0.0 -nsd uniform -f 1 -el 100 -owd 5 -rd 9"
```

with `-owd 5` carried over literally from `-rtt 5` — verify whether it should be
half that before using it for anything you intend to publish.

`benchmarks.sh` runs the single stale configuration after compiling, so treat it
as a smoke test rather than a sweep, and fix its command line too. To reproduce a
figure, sweep the parameter of interest in a shell loop over `mvn exec:java`.

Workloads are generated automatically when the `Simulator` runs; the generators
live in `workloads/scripts/` (`createDeviceTopology.py`,
`createRoutineSchedule.py`, `createRoutinesToDevicesMap.py`, and others).

## Simulator arguments

The simulator accepts short, upper-case and long forms of each flag (`-dn`,
`-DN`, `-deviceNo` are the same option). `-help` prints only a two-line usage
string and does **not** list the flags, so the table below — read directly off
the `switch` in `Simulator.java` — is the reference:

| Flag | Long form | Meaning |
|---|---|---|
| `-dn` | `-deviceNo` | number of devices |
| `-dts` | `-deviceTopologyShape` | topology shape (e.g. `grid`) |
| `-dtd` | `-deviceTopologyDims` | topology dimensions (e.g. `3,3`) |
| `-rn` | `-routineNo` | number of routines |
| `-np` | `-nodePercentage` | fraction of devices acting as nodes |
| `-cp` | `-churnProbability` | churn probability |
| `-f` | `-faults` | number of faults injected |
| `-el` | `-epochLength` | epoch length |
| `-owd` | `-oneWayDelay` | one-way network delay |
| `-k` | `-kgroupSize` | k-group size |
| `-lep` | `-electionPolicy` | leader-election policy |
| `-mrl` | `-maxRoutineLength` | maximum routine length |
| `-rld` | `-routineLengthDistribution` | routine-length distribution |
| `-rsd` / `-rsc` | `-routineScheduleDistribution` / `-Clusters` | routine schedule |
| `-nsd` / `-nsc` | `-nodeScheduleDistribution` / `-Clusters` | node schedule |
| `-rdmd` | `-routineDeviceMapDistribution` | routine-to-device mapping |
| `-adpr` | `-avgDevicesPerRoutine` | average devices per routine |
| `-e` | `-end` | end/termination condition |

## Running the paper's experiments

The parameter sweeps are **JUnit tests** in `src/test/java/SimulatorTest.java`,
one `@Test` per experiment. Each runs its sweep, writes a CSV, and then invokes
the matching plot script. This is the intended path from a clean checkout to a
figure — not the single `mvn exec:java` command above, which runs one
configuration.

```bash
mvn test -Dtest=SimulatorTest#inKGroupBenchmarkWOFailureTest
mvn test -Dtest=SimulatorTest                       # every experiment
```

| `@Test` method | Writes CSV under | Plot produced |
|---|---|---|
| `inKGroupBenchmarkWOFailureTest` | `outputs/in_kgroup/` | in-k-group delays + messages, into `outputs/in_kgroup_figures/` |
| `clientSyncDelayTest` | `outputs/sync_delay/` | client sync delay |
| `clientDelayTest` | `outputs/client_delay/` | client delay |
| `syncDelayTest` | `outputs/sync_delay/` | — (data only) |
| `bandwithTest` | `outputs/bandwidth/` | bandwidth |
| `bandwithBackgroundTest` | `outputs/bandwidth_bg/` | background + aggregated background bandwidth |
| `loadBalancingTest` | `outputs/balance/` | — (data only) |
| `debuggingSimulatorTest` | — | — (development helper) |

CSV files are named
`outputs/<name>/<name>_d<devices>..._<seed>.csv`, built by
`SimulatorTest.getOutputFilename(...)`.

## Plots

Plotting is **driven from Java, in Python**. `Simulator.createInKGroupDelaysPlot`
and its siblings shell out via `Runtime.exec` to:

```
python3 outputs/scripts/<script>.py <csv-file> <seed>
```

| Java method | Script |
|---|---|
| `createInKGroupMessagesPlot` | `outputs/scripts/plot_msgs.py` |
| `createInKGroupDelaysPlot` | `outputs/scripts/plot_in_kgroup_delays.py` |
| `createClientSyncDelayPlot` | `outputs/scripts/plot_client_sync_delay.py` |
| `createBandwidthPlot`, `createBandwidthBGPlot`, `createBandwidthAggregatedBGPlot` | `outputs/scripts/plot_bandwidth.py` |
| (balance / verification helpers) | `outputs/scripts/plot_balance_cdf.py`, `outputs/scripts/bw_verification.py` |

**These scripts were missing from this repository and have been restored** — the
Java code referenced `outputs/scripts/*.py` by path, but no `outputs/` directory
existed, so any test that plots would have failed. Worse than failing: on
`IOException` the plot helpers call `System.exit(-1)`, killing the JVM mid-test
rather than reporting a missing file.

Requirements for plotting to work:

- `python3` on `PATH`, with **matplotlib**, and **pandas** for
  `bw_verification.py` and `plot_balance_cdf.py`.
- Run Maven **from the repository root**, since the script path
  `outputs/scripts/...` and the CSV paths are both relative to the working
  directory.
- `outputs/scripts/plot_setting.py` is shared styling, imported by the others —
  keep it alongside them.

Figures are written next to the data, in `outputs/<name>_figures/`.
