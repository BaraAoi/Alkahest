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
}
