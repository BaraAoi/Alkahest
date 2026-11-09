package net.alkahest.block.custom;

import net.alkahest.block.entity.CauldronBlockEntity;
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
    public AlchemyCauldronBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FILLED, false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new CauldronBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.CAULDRON ? (w, p, s, be) -> CauldronBlockEntity.tick(w, p, (CauldronBlockEntity) be) : null;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
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
