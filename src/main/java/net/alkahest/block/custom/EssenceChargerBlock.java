package net.alkahest.block.custom;

import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EssenceChargerBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty MOUNTED = BooleanProperty.of("mounted");
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 6, 13);
    public EssenceChargerBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(MOUNTED, false)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(MOUNTED); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(MOUNTED, context.getWorld().getBlockState(context.getBlockPos().down()).isOf(ModBlocks.JAR));
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN ? state.with(MOUNTED, neighborState.isOf(ModBlocks.JAR))
            : super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new EssenceChargerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.ESSENCE_CHARGER ? (w, p, s, be) -> EssenceChargerBlockEntity.tick(w, p) : null;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof JarItem jar)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient) {
            var top = new BlockHitResult(Vec3d.ofCenter(pos).add(0, .5, 0), Direction.UP, pos, false);
            if (!jar.place(new ItemPlacementContext(player, hand, stack, top)).isAccepted())
                player.sendMessage(Text.translatable("message.alkahest.charger_no_space"), true);
        }
        return ItemActionResult.success(world.isClient);
    }
}
