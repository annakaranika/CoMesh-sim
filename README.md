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
mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dt grid3,3 -np 0.4 -ns cp0.0_uniform -f 1 -el 100 -rtt 5 -rd 9"
```
