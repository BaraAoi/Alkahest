#!/bin/sh
# Optional long-running route stress test; requires a Java 21 JDK.
set -eu
cd "$(dirname "$0")/../.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
javac --release 21 -d "$tmp" \
  scripts/pipe_stress/src/net/minecraft/util/math/BlockPos.java \
  scripts/pipe_stress/src/net/minecraft/util/math/Direction.java \
  src/main/java/net/alkahest/block/entity/PipeRouteTree.java \
  scripts/pipe_stress/src/net/alkahest/block/entity/RouteFuzz.java
java -Xmx512m -cp "$tmp" net.alkahest.block.entity.RouteFuzz "${1:-100000}"
