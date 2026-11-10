package net.alkahest.block.custom;

import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.item.custom.CrystalWandItem;
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
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Centre of the 3x3x3 Matrix. A finished cube is dormant: rings lie flat and nothing works until
 * a crystal wand wakes it. Only then do the rings spin and the pyramid hold the field.
 */
public class MatrixCoreBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty AWAKE = BooleanProperty.of("awake");
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    public MatrixCoreBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(AWAKE, false).with(ACTIVE, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AWAKE, ACTIVE);
    }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MatrixCoreBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.MATRIX_CORE
            ? (w, p, s, be) -> MatrixCoreBlockEntity.tick(w, p, (MatrixCoreBlockEntity) be) : null;
    }

    /**
     * No window: the altar is physical. A wand wakes it, a held item is placed inside the core and
     * floats there, and an empty hand takes the product (or the untouched base) back out.
     */
    private void interact(World world, BlockPos pos, PlayerEntity player, ItemStack held) {
        if (world.isClient || player.isSneaking()) return;
        if (held.getItem() instanceof CrystalWandItem) {
            MatrixCoreBlockEntity.awakenNearby(world, pos, player);
            return;
        }
        MatrixCoreBlockEntity core = MatrixCoreBlockEntity.coreNearby(world, pos);
        if (core == null) {
            player.sendMessage(net.minecraft.text.Text.translatable("message.alkahest.matrix_unformed"), true);
            return;
        }
        if (!held.isEmpty()) core.insertBase(held, player);
        else core.takeFromCore(player);
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        interact(world, pos, player, stack);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking()) return ActionResult.PASS;
        interact(world, pos, player, ItemStack.EMPTY);
        return ActionResult.success(world.isClient);
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof MatrixCoreBlockEntity core) {
            core.dropContents(world, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** A working Matrix leaks violet sparks out of its core. */
    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(ACTIVE) || random.nextInt(3) != 0) return;
        world.addParticle(new DustParticleEffect(new Vector3f(.85f, .55f, 1f), .7f),
            pos.getX() + .5 + (random.nextDouble() - .5) * .7, pos.getY() + .5,
            pos.getZ() + .5 + (random.nextDouble() - .5) * .7, 0, .03, 0);
    }
}
