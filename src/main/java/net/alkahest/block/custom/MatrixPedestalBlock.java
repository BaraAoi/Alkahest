package net.alkahest.block.custom;

import net.alkahest.block.entity.MatrixPedestalBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Holds one item that the Matrix can consume; the item floats above the plinth. */
public class MatrixPedestalBlock extends Block implements BlockEntityProvider {
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 12, 13);

    public MatrixPedestalBlock(Settings settings) {
        super(settings);
    }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MatrixPedestalBlockEntity(pos, state);
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(world.getBlockEntity(pos) instanceof MatrixPedestalBlockEntity pedestal)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!pedestal.isEmpty()) {
            return ItemActionResult.SUCCESS;
        }
        pedestal.setHeld(stack.copyWithCount(1));
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE,
            net.minecraft.sound.SoundCategory.BLOCKS, 0.6f, 1.4f);
        return ItemActionResult.SUCCESS;
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MatrixPedestalBlockEntity pedestal)) return ActionResult.PASS;
        if (pedestal.isEmpty()) {
            return ActionResult.SUCCESS;
        }
        ItemStack taken = pedestal.takeOne();
        if (!player.getInventory().insertStack(taken)) player.dropItem(taken, false);
        world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK,
            net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.6f);
        return ActionResult.SUCCESS;
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof MatrixPedestalBlockEntity pedestal) {
            ItemStack held = pedestal.held();
            if (!held.isEmpty()) ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), held);
            pedestal.setHeld(ItemStack.EMPTY);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
