package net.alkahest.block.custom;

import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Essence bellows stacked above a tier-2 distillery; each bellow stores one essence type. */
public class DistilleryBellowsBlock extends Block implements BlockEntityProvider {
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 3);
    private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);

    public DistilleryBellowsBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(LEVEL, 0));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DistilleryBellowsBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof net.alkahest.item.custom.JarItem)) {
            if (!world.isClient) player.sendMessage(net.minecraft.text.Text.translatable("message.alkahest.bellows_instruction"), true);
            return ItemActionResult.success(world.isClient);
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof DistilleryBellowsBlockEntity bellows)
            bellows.pourToJar(player, hand);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof DistilleryBellowsBlockEntity bellows)
            bellows.describe(player);
        return ActionResult.success(world.isClient);
    }
}
