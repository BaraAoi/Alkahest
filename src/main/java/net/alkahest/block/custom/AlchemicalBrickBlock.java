package net.alkahest.block.custom;

import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The eighteen faces and edges of the Matrix cube; also a decent building block. */
public class AlchemicalBrickBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    public AlchemicalBrickBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof net.alkahest.item.custom.AlkahestAmuletItem)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (MatrixCoreBlockEntity.nearbyCore(world, pos)) MatrixCoreBlockEntity.openNearby(world, pos, player);
        return ItemActionResult.success(world.isClient);
    }
}
