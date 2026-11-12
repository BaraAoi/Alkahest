package net.alkahest.block.custom;

import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.text.Text;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Lower part of the 1x3x1 Stabilizer tower; holds the essence liquid being sorted. */
public class StabilizerReceiverBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty OCCUPIED = BooleanProperty.of("occupied");
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 16, 15);

    public StabilizerReceiverBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(OCCUPIED, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(OCCUPIED); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new StabilizerReceiverBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.STABILIZER_RECEIVER
            ? (w, p, s, be) -> StabilizerReceiverBlockEntity.tick(w, p, (StabilizerReceiverBlockEntity) be) : null;
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(ModBlocks.ALCHEMY_PIPE.asItem())) {
            if (!world.isClient && !((BlockItem) stack.getItem()).place(new ItemPlacementContext(player, hand, stack, hit)).isAccepted())
                player.sendMessage(Text.translatable("message.alkahest.pipe_no_space"), true);
            return ItemActionResult.success(world.isClient);
        }
        // An empty hand must fall through to onUse so the player can read the receiver status.
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof net.alkahest.item.custom.JarItem)) {
            if (!world.isClient) player.sendMessage(net.minecraft.text.Text.translatable("message.alkahest.stabilizer_instruction"), true);
            return ItemActionResult.success(world.isClient);
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof StabilizerReceiverBlockEntity receiver)
            receiver.pourJar(player, hand);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof StabilizerReceiverBlockEntity receiver)
            receiver.describe(player);
        return ActionResult.success(world.isClient);
    }

    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !moved
            && world.getBlockEntity(pos) instanceof StabilizerReceiverBlockEntity receiver)
            receiver.dropContents(world, pos);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
