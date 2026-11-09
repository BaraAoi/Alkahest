package net.alkahest.block.custom;

import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DistilleryBlock extends Block implements BlockEntityProvider {
    public DistilleryBlock(Settings settings) { super(settings); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DistilleryBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.DISTILLERY ? (w, p, s, be) -> DistilleryBlockEntity.tick(w, p, (DistilleryBlockEntity) be) : null;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery) distillery.insert(player, hand);
        return ItemActionResult.success(world.isClient);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery) distillery.collect(player);
        return ActionResult.success(world.isClient);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery)
            ItemScatterer.spawn(world, pos, distillery.stacks());
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
