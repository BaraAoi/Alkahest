# Optional route stress test

Run `scripts/pipe_stress/run.sh 1000000` with a Java 21 JDK. The argument is the
number of seeded random 6×6×6 layouts (default 100,000). It compiles the actual
`PipeRouteTree` source with small coordinate/direction stand-ins and compares
its shortest routes and endpoints against a separately implemented flood fill.
It deliberately does **not** simulate Minecraft worlds, block entities, network
sync, unloaded chunks, or payment; use the regular `test` and `runGametest`
tasks for those integration checks. The seed makes a failing case reproducible.
