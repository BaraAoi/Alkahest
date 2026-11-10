package net.alkahest.block.custom;

import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Centre of the 3x1x3 Transmutator. Essence is not piped in: the eight pillars pull it out of
 * nearby jars through the air, so the machine only has to be surrounded by its own pillars.
 */
public class TransmutatorCoreBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty WORKING = BooleanProperty.of("working");

    public TransmutatorCoreBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(WORKING, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(WORKING); }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TransmutatorCoreBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.TRANSMUTATOR_CORE
            ? (w, p, s, be) -> TransmutatorCoreBlockEntity.tick(w, p, (TransmutatorCoreBlockEntity) be) : null;
    }

    private void open(World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient || player.isSneaking()) return;
        if (world.getBlockEntity(pos) instanceof TransmutatorCoreBlockEntity core) {
            if (!core.formed()) {
                player.sendMessage(Text.translatable("message.alkahest.transmutator_unformed"), true);
                return;
            }
            player.openHandledScreen(core);
        }
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        open(world, pos, player);
        return player.isSneaking() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking()) return ActionResult.PASS;
        open(world, pos, player);
        return ActionResult.success(world.isClient);
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof TransmutatorCoreBlockEntity core) {
            core.dropContents(world, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** While it works the core sheds a few golden sparks. */
    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(WORKING) || random.nextInt(3) != 0) return;
        world.addParticle(new DustParticleEffect(new Vector3f(1f, .85f, .35f), .6f),
            pos.getX() + .5 + (random.nextDouble() - .5) * .5, pos.getY() + 1.05,
            pos.getZ() + .5 + (random.nextDouble() - .5) * .5, 0, .02, 0);
    }
}
