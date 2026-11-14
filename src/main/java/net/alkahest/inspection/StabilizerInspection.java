package net.alkahest.inspection;

import net.alkahest.block.custom.StabilizerCoreBlock;
import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.registry.ModBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Read-only view of one tower. It never searches nearby towers or loads unrelated chunks. */
public final class StabilizerInspection {
    public record View(StabilizerReceiverBlockEntity receiver, boolean formed, boolean active) { }
    public static View inspect(World world, BlockPos targeted) {
        var state=world.getBlockState(targeted);
        BlockPos corePos;
        if (state.isOf(ModBlocks.STABILIZER_RECEIVER)) corePos=targeted.up();
        else if (state.isOf(ModBlocks.STABILIZER_CORE)) corePos=targeted;
        else if (state.isOf(ModBlocks.STABILIZER_CREST)) corePos=targeted.down();
        else return null;
        var core=world.getBlockState(corePos);
        boolean hasCore=core.isOf(ModBlocks.STABILIZER_CORE);
        // When the core is missing, do not associate a free-standing crest with an unrelated tank.
        StabilizerReceiverBlockEntity receiver = (hasCore || state.isOf(ModBlocks.STABILIZER_RECEIVER))
            && world.getBlockEntity(corePos.down()) instanceof StabilizerReceiverBlockEntity r ? r : null;
        boolean formed=hasCore && receiver!=null && world.getBlockState(corePos.up()).isOf(ModBlocks.STABILIZER_CREST);
        return new View(receiver, formed, formed && core.get(StabilizerCoreBlock.ACTIVE));
    }
    private StabilizerInspection() { }
}
