package net.alkahest.block.custom;

import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Tier-2 multiblock distillery: essence bellows must be stacked directly above it. */
public class Tier2DistilleryBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty WORKING = BooleanProperty.of("working");
    private static final DustParticleEffect VAPOR = new DustParticleEffect(new Vector3f(.55f, .36f, .86f), .8f);
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

    public Tier2DistilleryBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(WORKING, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(WORKING); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new Tier2DistilleryBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.DISTILLERY_TIER2
            ? (w, p, s, be) -> Tier2DistilleryBlockEntity.tick(w, p, (Tier2DistilleryBlockEntity) be) : null;
    }

    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(WORKING)) return;
        if (world.isAir(pos.up()) && random.nextInt(3) == 0)
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + 1.02, pos.getZ() + .5, 0, .025, 0);
        if (random.nextInt(4) == 0) {
            Direction side = Direction.fromHorizontal(random.nextInt(4));
            world.addParticle(VAPOR, pos.getX() + .5 + side.getOffsetX() * .51,
                pos.getY() + .45 + random.nextDouble() * .3, pos.getZ() + .5 + side.getOffsetZ() * .51, 0, .012, 0);
        }
    }

    private void open(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof Tier2DistilleryBlockEntity distillery)
            player.openHandledScreen(distillery);
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        open(world, pos, player);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        open(world, pos, player);
        return ActionResult.success(world.isClient);
    }

    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof Tier2DistilleryBlockEntity distillery)
            ItemScatterer.spawn(world, pos, distillery);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
