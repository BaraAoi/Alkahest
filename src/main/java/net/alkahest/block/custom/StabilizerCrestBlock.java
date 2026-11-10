package net.alkahest.block.custom;

import net.alkahest.block.entity.StabilizerCoreBlockEntity;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.item.custom.AlkahestVialItem;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Top crystal of the Stabilizer tower; forwards activation clicks to the core below. */
public class StabilizerCrestBlock extends Block {
    public static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 14, 12);

    public StabilizerCrestBlock(Settings settings) { super(settings); }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof AlkahestAmuletItem) && !(stack.getItem() instanceof AlkahestVialItem))
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient && world.getBlockState(pos.down()).isOf(ModBlocks.STABILIZER_CORE)
            && world.getBlockEntity(pos.down()) instanceof StabilizerCoreBlockEntity core) core.toggle(player);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockState(pos.down()).isOf(ModBlocks.STABILIZER_CORE)
            && world.getBlockEntity(pos.down()) instanceof StabilizerCoreBlockEntity core) core.describe(player);
        return ActionResult.success(world.isClient);
    }
}
