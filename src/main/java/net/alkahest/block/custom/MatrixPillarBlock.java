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

/** One of the eight corners of the Matrix cube. Carries no inventory: the core does the work. */
public class MatrixPillarBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    public MatrixPillarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }

    /** Clicking a corner with the amulet opens the interface of the cube it belongs to. */
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof net.alkahest.item.custom.CrystalWandItem) {
            MatrixCoreBlockEntity.toggleAwakeNearby(world, pos, player);
            return ItemActionResult.success(world.isClient);
        }
        if (!(stack.getItem() instanceof net.alkahest.item.custom.AlkahestAmuletItem)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        MatrixCoreBlockEntity.openNearby(world, pos, player);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        MatrixCoreBlockEntity.openNearby(world, pos, player);
        return ActionResult.success(world.isClient);
    }
}
