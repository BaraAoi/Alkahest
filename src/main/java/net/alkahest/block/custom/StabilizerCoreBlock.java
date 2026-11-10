package net.alkahest.block.custom;

import net.alkahest.block.entity.StabilizerCoreBlockEntity;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.item.custom.AlkahestVialItem;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Middle part of the 1x3x1 Stabilizer tower. Activated with the Alkahest amulet or vial. */
public class StabilizerCoreBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty FORMED = BooleanProperty.of("formed");
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 16, 13);

    public StabilizerCoreBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FORMED, false).with(ACTIVE, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FORMED, ACTIVE); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new StabilizerCoreBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.STABILIZER_CORE
            ? (w, p, s, be) -> StabilizerCoreBlockEntity.tick(w, p, (StabilizerCoreBlockEntity) be) : null;
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof AlkahestAmuletItem) && !(stack.getItem() instanceof AlkahestVialItem))
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient && world.getBlockEntity(pos) instanceof StabilizerCoreBlockEntity core) core.toggle(player);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof StabilizerCoreBlockEntity core) core.describe(player);
        return ActionResult.success(world.isClient);
    }
}
