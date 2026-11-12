package net.alkahest.block.custom;

import net.alkahest.block.entity.WaterFaucetBlockEntity;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Small non-colliding spout above a cauldron; item entities can still fall past it. */
public final class WaterFaucetBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty INSTALLED = BooleanProperty.of("installed");
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 2, 2, 13, 16, 14);
    public WaterFaucetBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(INSTALLED, false)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(INSTALLED); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new WaterFaucetBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.WATER_FAUCET ? (w, p, s, be) -> WaterFaucetBlockEntity.tick(w, p, (WaterFaucetBlockEntity) be) : null;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient && world.getBlockEntity(pos) instanceof WaterFaucetBlockEntity faucet) faucet.insert(player, hand);
        return ItemActionResult.success(world.isClient);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof WaterFaucetBlockEntity faucet) faucet.take(player);
        return ActionResult.success(world.isClient);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && !state.isOf(next.getBlock()) && world.getBlockEntity(pos) instanceof WaterFaucetBlockEntity faucet)
            for (ItemStack focus : faucet.stacks()) if (!focus.isEmpty()) {
                // Eject away from the cauldron opening so the focus is not dissolved below.
                ItemScatterer.spawn(world, pos.getX() + 1.2, pos.getY() + .3, pos.getZ() + .5, focus);
            }
        super.onStateReplaced(state, world, pos, next, moved);
    }
}
