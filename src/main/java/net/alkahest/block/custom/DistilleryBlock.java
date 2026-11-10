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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DistilleryBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty WORKING = BooleanProperty.of("working");
    private static final DustParticleEffect VAPOR = new DustParticleEffect(new Vector3f(.35f, .84f, .72f), .75f);
    public DistilleryBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(WORKING, false)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(WORKING); }
    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(WORKING)) return;
        // Cosmetic client particles only; no fire blocks or gameplay effects are created.
        if (world.isAir(pos.up()) && random.nextInt(3) == 0)
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + 1.02, pos.getZ() + .5, 0, .025, 0);
        if (random.nextInt(3) == 0) {
            Direction side = Direction.fromHorizontal(random.nextInt(4));
            double sx = pos.getX() + .5 + side.getOffsetX() * .51;
            double sz = pos.getZ() + .5 + side.getOffsetZ() * .51;
            world.addParticle(VAPOR, sx, pos.getY() + .32 + random.nextDouble() * .4, sz, 0, .012, 0);
        }
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DistilleryBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.DISTILLERY ? (w, p, s, be) -> DistilleryBlockEntity.tick(w, p, (DistilleryBlockEntity) be) : null;
    }
    private void open(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery) player.openHandledScreen(distillery);
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        open(world, pos, player); return ItemActionResult.success(world.isClient);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        open(world, pos, player); return ActionResult.success(world.isClient);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery) {
            ItemScatterer.spawn(world, pos, distillery.stacks());
            distillery.dropExtraPending(world, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
