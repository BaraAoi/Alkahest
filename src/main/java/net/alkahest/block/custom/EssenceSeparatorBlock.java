package net.alkahest.block.custom;

import net.alkahest.block.entity.EssenceSeparatorBlockEntity;
import net.alkahest.item.custom.JarItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
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

public class EssenceSeparatorBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty OCCUPIED = BooleanProperty.of("occupied");
    private static final VoxelShape SHAPE = Block.createCuboidShape(2,0,2,14,13,14);
    public EssenceSeparatorBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(OCCUPIED,false)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(OCCUPIED); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new EssenceSeparatorBlockEntity(pos,state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof JarItem)) {
            if (!world.isClient) player.sendMessage(net.minecraft.text.Text.translatable("message.alkahest.separator_instruction"), true);
            return ItemActionResult.success(world.isClient);
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof EssenceSeparatorBlockEntity separator) separator.useJar(player, hand);
        return ItemActionResult.success(world.isClient);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof EssenceSeparatorBlockEntity separator) separator.collect(player);
        return ActionResult.success(world.isClient);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof EssenceSeparatorBlockEntity separator)
            ItemScatterer.spawn(world, pos, separator.stacks());
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
