package net.alkahest.block.custom;

import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.item.custom.CrystalWandItem;
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

/**
 * One of the eight corners of the Matrix cube, a stepped pyramid. While the Matrix is dormant it
 * is a solid block; once the cube is awakened it turns into an invisible scaffold (the block is
 * still there and can be broken) and the core draws the finished halo instead.
 */
public class MatrixPillarBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public static final BooleanProperty REVEALED = BooleanProperty.of("revealed");

    public MatrixPillarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false).with(REVEALED, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, REVEALED);
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        MatrixCoreBlockEntity.interactNearby(world, pos, player, stack);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking()) return ActionResult.PASS;
        MatrixCoreBlockEntity.interactNearby(world, pos, player, ItemStack.EMPTY);
        return ActionResult.success(world.isClient);
    }
}
