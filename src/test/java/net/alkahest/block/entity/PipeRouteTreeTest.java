package net.alkahest.block.entity;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipeRouteTreeTest {
    /** All 65,536 ways to close a 4x4 grid; reference distance uses bitset wavefronts. */
    @Test void exhaustiveFourByFourGridsChooseAnAllowedShortestRoute() {
        BlockPos source = new BlockPos(-1, 0, 1), target = new BlockPos(4, 0, 2);
        for (int mask = 0; mask < 1 << 16; mask++) {
            final int blocked = mask;
            var tree = PipeRouteTree.build(source, p -> p.getY() == 0 && p.getX() >= 0 && p.getX() < 4
                && p.getZ() >= 0 && p.getZ() < 4 && (blocked & (1 << (p.getZ() * 4 + p.getX()))) == 0);
            List<BlockPos> path = tree.routeTo(target);
            int expected = referenceDistance(mask);
            if (expected < 0) {
                assertTrue(path.isEmpty(), "mask=" + mask);
                continue;
            }
            assertEquals(expected + 1, path.size(), "shortest path, mask=" + mask);
            assertEquals(1, path.getFirst().getManhattanDistance(target));
            assertEquals(1, path.getLast().getManhattanDistance(source));
            for (int i = 0; i < path.size(); i++) {
                BlockPos p = path.get(i);
                assertEquals(0, mask & (1 << (p.getZ() * 4 + p.getX())), "path cannot use a blocked cell");
                if (i > 0) assertEquals(1, p.getManhattanDistance(path.get(i - 1)));
            }
        }
    }

    private static int referenceDistance(int mask) {
        int open = ~mask & 0xffff, frontier = (1 << 4) & open, visited = 0;
        for (int distance = 0; frontier != 0; distance++) {
            if ((frontier & (1 << 11)) != 0) return distance;
            visited |= frontier;
            int left = (frontier & 0xeeee) >>> 1, right = (frontier & 0x7777) << 1;
            int up = frontier >>> 4, down = (frontier << 4) & 0xffff;
            frontier = (left | right | up | down) & open & ~visited;
        }
        return -1;
    }

    @Test void searchesAllAdjacentEntryAndExitSegmentsInThreeDimensions() {
        BlockPos source = new BlockPos(0, 0, 0), target = new BlockPos(2, 0, 0);
        var allowed = java.util.Set.of(new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(2, 1, 0));
        var path = PipeRouteTree.build(source, allowed::contains).routeTo(target);
        assertEquals(List.of(new BlockPos(2, 1, 0), new BlockPos(1, 1, 0), new BlockPos(0, 1, 0)), path);
        assertTrue(PipeRouteTree.build(source, p -> false).routeTo(target).isEmpty());
    }
    @Test void seededThreeDimensionalNetworksMatchAnIndependentShortestPathSearch() {
        var random = new java.util.Random(0xA1CA4E57L);
        BlockPos source = new BlockPos(0, 0, 0), target = new BlockPos(2, 1, 1);
        for (int trial = 0; trial < 3000; trial++) {
            java.util.Set<BlockPos> open = new java.util.HashSet<>();
            double probability = .15 + random.nextDouble() * .75;
            for (int x=-2; x<=3; x++) for (int y=-2; y<=2; y++) for (int z=-2; z<=2; z++) {
                BlockPos p = new BlockPos(x,y,z);
                if (!p.equals(source) && !p.equals(target) && random.nextDouble() < probability) open.add(p);
            }
            List<BlockPos> actual = PipeRouteTree.build(source, open::contains).routeTo(target);
            int expected = reference3dDistance(open, source, target);
            assertEquals(expected, actual.isEmpty() ? -1 : actual.size(), "trial=" + trial);
            if (!actual.isEmpty()) {
                assertEquals(1, actual.getFirst().getManhattanDistance(target));
                assertEquals(1, actual.getLast().getManhattanDistance(source));
                for (int n=0; n<actual.size(); n++) {
                    assertTrue(open.contains(actual.get(n)));
                    if (n>0) assertEquals(1, actual.get(n).getManhattanDistance(actual.get(n-1)));
                }
            }
        }
    }

    /** Independent layer-by-layer flood fill, without using Minecraft Direction or a route tree. */
    private static int reference3dDistance(java.util.Set<BlockPos> open, BlockPos source, BlockPos target) {
        java.util.Set<BlockPos> reached = new java.util.HashSet<>();
        reached.add(source);
        java.util.Set<BlockPos> frontier = java.util.Set.of(source);
        for (int length=1; length<=open.size(); length++) {
            java.util.Set<BlockPos> next = new java.util.HashSet<>();
            for (BlockPos candidate : open) {
                if (reached.contains(candidate)) continue;
                for (BlockPos p : frontier) {
                    if (p.getManhattanDistance(candidate)!=1) continue;
                    if (candidate.getManhattanDistance(target)==1) return length;
                    next.add(candidate);
                    break;
                }
            }
            if (next.isEmpty()) return -1;
            reached.addAll(next);
            frontier = next;
        }
        return -1;
    }

}
