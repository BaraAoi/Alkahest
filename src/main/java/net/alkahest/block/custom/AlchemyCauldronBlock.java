package net.alkahest.block.custom;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.research.EarlyResearch;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class AlchemyCauldronBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty FILLED = BooleanProperty.of("filled");
    public static final BooleanProperty DIRTY = BooleanProperty.of("dirty");

    public AlchemyCauldronBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FILLED, false).with(DIRTY, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FILLED, DIRTY);
    }

    // Match the vanilla cauldron's hollow collision shape so dropped ingredients can fall inside.
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return Blocks.CAULDRON.getDefaultState().getCollisionShape(world, pos, context);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return Blocks.CAULDRON.getDefaultState().getOutlineShape(world, pos, context);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CauldronBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.CAULDRON ? (w, p, s, be) -> CauldronBlockEntity.tick(w, p, (CauldronBlockEntity) be) : null;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(FILLED) || !(world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) || !cauldron.isHeated()) {
            return;
        }
        int color = cauldron.essenceColor();
        if (color >= 0 && random.nextInt(2) == 0) {
            Vector3f rgb = new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f);
            double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
            double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
            world.addParticle(new DustParticleEffect(rgb, 0.85f), x, pos.getY() + 0.96, z, 0, 0.02, 0);
        } else if (random.nextInt(4) == 0) {
            double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
            double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
            world.addParticle(ParticleTypes.SPLASH, x, pos.getY() + 0.95, z, 0, 0.02, 0);
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof ServerPlayerEntity player) {
            EarlyResearch.discoverCauldron(player, world, pos);
        }
    }

    private static void discover(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            EarlyResearch.discoverCauldron(serverPlayer, world, pos);
        }
    }

    /** Handles sneak + right-click bulk dissolution for non-block items while letting blocks/hoppers place normally. */
    public static ActionResult sneakUseWithItem(World world, BlockPos pos, PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (held.isEmpty() || held.getItem() instanceof BlockItem) {
            return ActionResult.PASS;
        }
        discover(world, pos, player);
        if (!world.isClient && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            cauldron.useItem(player, hand);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        discover(world, pos, player);
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            cauldron.useItem(player, hand);
        }
        return ItemActionResult.success(world.isClient);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        discover(world, pos, player);
        if (!world.isClient && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            if (player.isSneaking()) {
                var trinketOpt = dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player);
                if (trinketOpt.isPresent()) {
                    for (var entry : trinketOpt.get().getEquipped(s -> s.getItem() instanceof net.alkahest.item.custom.AlkahestAmuletItem)) {
                        if (cauldron.useAmulet(world, pos, state, player, entry.getRight())) {
                            return ActionResult.success(false);
                        }
                    }
                }
            }
            cauldron.describeEssences(player);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            if (!world.isClient) {
                cauldron.ejectLegacyItems();
                cauldron.crystallize();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
