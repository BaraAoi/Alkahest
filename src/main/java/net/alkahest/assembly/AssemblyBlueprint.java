package net.alkahest.assembly;

import net.alkahest.block.custom.AlchemicalBrickBlock;
import net.alkahest.block.custom.MatrixPillarBlock;
import net.alkahest.block.custom.TransmutatorPillarBlock;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Read-only templates matching the currently implemented machines, never loads chunks. */
public final class AssemblyBlueprint {
    public enum Kind { STABILIZER, TIER2, TRANSMUTATOR, MATRIX }
    public record Missing(BlockPos pos, Block expected, boolean obstructed) { }
    public record Inspection(Kind kind, List<Missing> missing, int unknown) { }
    public static Kind kind(World world, BlockPos core) {
        if (!world.isChunkLoaded(core)) return null;
        var state = world.getBlockState(core);
        if (state.isOf(ModBlocks.STABILIZER_CORE)) return Kind.STABILIZER;
        if (state.isOf(ModBlocks.DISTILLERY_TIER2)) return Kind.TIER2;
        if (state.isOf(ModBlocks.TRANSMUTATOR_CORE)) return Kind.TRANSMUTATOR;
        if (state.isOf(ModBlocks.MATRIX_CORE)) return Kind.MATRIX;
        return null;
    }
    public static Inspection inspect(World world, BlockPos core) {
        Kind kind = kind(world, core);
        if (kind == null) return null;
        List<Missing> missing = new ArrayList<>();
        int unknown = 0;
        if (kind == Kind.TRANSMUTATOR) {
            boolean legacy = true;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                var p = core.add(dx, 0, dz);
                if (!world.isChunkLoaded(p)) { unknown++; legacy = false; }
                else if (!(world.getBlockState(p).getBlock() instanceof TransmutatorPillarBlock)) legacy = false;
            }
            // An existing legacy platform is valid; otherwise show the modern vertical tower.
            if (legacy) return new Inspection(kind, List.of(), 0);
            // Unknown legacy positions do not invalidate a fully present vertical tower.
            unknown = 0;
        }
        List<Missing> expected = new ArrayList<>();
        switch (kind) {
            case STABILIZER -> {
                expected.add(new Missing(core.down(), ModBlocks.STABILIZER_RECEIVER, false));
                expected.add(new Missing(core.up(), ModBlocks.STABILIZER_CREST, false));
            }
            case TIER2 -> expected.add(new Missing(core.up(), ModBlocks.DISTILLERY_BELLOWS, false));
            case TRANSMUTATOR -> {
                expected.add(new Missing(core.up(), ModBlocks.TRANSMUTATOR_CHAMBER, false));
                expected.add(new Missing(core.up(2), ModBlocks.TRANSMUTATOR_CROWN, false));
            }
            case MATRIX -> {
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    boolean corner = dx != 0 && dy != 0 && dz != 0;
                    expected.add(new Missing(core.add(dx, dy, dz), corner ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK, false));
                }
            }
        }
        for (var part : expected) {
            var pos = part.pos();
            if ((pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) || !world.isChunkLoaded(pos)) { unknown++; continue; }
            var state = world.getBlockState(pos);
            boolean matches = part.expected() == ModBlocks.MATRIX_PILLAR ? state.getBlock() instanceof MatrixPillarBlock
                : part.expected() == ModBlocks.ALCHEMICAL_BRICK ? state.getBlock() instanceof AlchemicalBrickBlock
                : state.isOf(part.expected());
            if (!matches) missing.add(new Missing(pos, part.expected(), !state.isAir()));
        }
        return new Inspection(kind, List.copyOf(missing), unknown);
    }
    private AssemblyBlueprint() { }
}
