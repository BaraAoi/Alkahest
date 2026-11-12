package net.alkahest.block.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Immutable-in-use, multi-entry breadth-first tree for one source and essence type.
 * Created for one transfer pulse only, never retained across world or filter changes.
 */
final class PipeRouteTree {
    private final Map<BlockPos, BlockPos> previous;
    private final Map<BlockPos, Integer> distance;

    private PipeRouteTree(Map<BlockPos, BlockPos> previous, Map<BlockPos, Integer> distance) {
        this.previous = previous;
        this.distance = distance;
    }

    static PipeRouteTree build(BlockPos source, Predicate<BlockPos> allows) {
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos start = source.offset(direction);
            if (allows.test(start) && !previous.containsKey(start)) {
                previous.put(start, start);
                distance.put(start, 0);
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.offset(direction);
                if (!previous.containsKey(next) && allows.test(next)) {
                    previous.put(next, pos);
                    distance.put(next, distance.get(pos) + 1);
                    queue.add(next);
                }
            }
        }
        return new PipeRouteTree(previous, distance);
    }

    /** Empty means unreachable. Otherwise path runs from the destination to the source. */
    List<BlockPos> routeTo(BlockPos target) {
        BlockPos end = null;
        int best = Integer.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = target.offset(direction);
            Integer d = distance.get(candidate);
            if (d != null && d < best) {
                best = d;
                end = candidate;
            }
        }
        if (end == null) return List.of();
        List<BlockPos> path = new ArrayList<>();
        while (true) {
            path.add(end);
            BlockPos parent = previous.get(end);
            if (parent.equals(end)) return path;
            end = parent;
        }
    }
}
