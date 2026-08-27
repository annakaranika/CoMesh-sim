# CoMesh-sim

This is the repo for simulation experiments of the paper 'There is More Control in Egalitarian Edge IoT Meshes'.

## Requirements:
* JDK 15+
* Apache Maven 3.6.3+

This is a Maven project. You can compile it by running:
```
mvn compiler:compile
```
The source code is located at the `src` directory.

The scripts that create the workloads are in the `workloads/scripts` directory.
Workloads are automatically created when the `Simulator` is run.

To run a simulation, you can execute:
```
mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dts grid -dtd 3,3 -np 0.4 -cp 0.0 -nsd uniform -f 1 -el 100 -owd 5 -rd 9"
```

Note on `-owd 5`: it replaces an older `-rtt 5`. One-way delay is not a
round-trip time, so confirm the intended value before relying on results.

The paper's actual experiments are the JUnit tests in
`src/test/java/SimulatorTest.java` — each runs a sweep, writes a CSV under
`outputs/<name>/`, and calls a Python plot script in `outputs/scripts/` to draw
the figure:

```
mvn test -Dtest=SimulatorTest#inKGroupBenchmarkWOFailureTest
```

Plotting needs `python3` with matplotlib (and pandas for two of the scripts), and
Maven must be run from the repository root. `EXPERIMENTS.md` documents every
flag, every test, and which script draws which figure.
