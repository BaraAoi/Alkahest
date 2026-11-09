package net.alkahest.block.custom;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.research.EarlyResearch;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class AlchemyCauldronBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty FILLED = BooleanProperty.of("filled");
    public static final BooleanProperty DIRTY = BooleanProperty.of("dirty");
    public AlchemyCauldronBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FILLED, false).with(DIRTY, false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FILLED, DIRTY);
    }
    // Match the vanilla cauldron's hollow collision shape so dropped ingredients can fall inside.
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return Blocks.CAULDRON.getDefaultState().getCollisionShape(world, pos, context);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return Blocks.CAULDRON.getDefaultState().getOutlineShape(world, pos, context);
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new CauldronBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.CAULDRON ? (w, p, s, be) -> CauldronBlockEntity.tick(w, p, (CauldronBlockEntity) be) : null;
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof ServerPlayerEntity player) EarlyResearch.discoverCauldron(player, world, pos);
    }
    private static void discover(World world, BlockPos pos, PlayerEntity player) {
        // Existing worlds may already contain a placed cauldron before the journal is opened.
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) EarlyResearch.discoverCauldron(serverPlayer, world, pos);
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        discover(world, pos, player);
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            if (stack.getItem() instanceof CrystalWandItem wand) {
                if (cauldron.activate(player)) wand.consumeUse(player, hand);
            } else if (stack.isOf(Items.WATER_BUCKET)) cauldron.fill(player, hand);
            else cauldron.add(player, hand);
        }
        return ItemActionResult.success(world.isClient);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        discover(world, pos, player);
        if (!world.isClient && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron) {
            if (player.isSneaking()) cauldron.empty(player); else cauldron.activate(player);
        }
        return ActionResult.success(world.isClient);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof CauldronBlockEntity cauldron)
            ItemScatterer.spawn(world, pos, cauldron.stacks());
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
