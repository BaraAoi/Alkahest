package net.alkahest.block.custom;

import net.alkahest.block.entity.WaterFaucetBlockEntity;
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
import net.minecraft.item.Items;
import net.minecraft.item.ItemUsage;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Compact non-colliding spout, powered by its integral Aqua crystal. */
public final class WaterFaucetBlock extends Block implements BlockEntityProvider {
    // Retain the old property for saved blockstates; it does not control operation anymore.
    public static final BooleanProperty INSTALLED = BooleanProperty.of("installed");
    public static final BooleanProperty ENABLED = BooleanProperty.of("enabled");
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 7, 4, 12, 15, 12);
    public WaterFaucetBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(INSTALLED, false).with(ENABLED, true)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(INSTALLED, ENABLED); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new WaterFaucetBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.WATER_FAUCET ? (w, p, s, be) -> WaterFaucetBlockEntity.tick(w, p, (WaterFaucetBlockEntity) be) : null;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.isOf(Items.BUCKET)) {
            if (!world.isClient) {
                if (state.get(ENABLED) && !world.isReceivingRedstonePower(pos)) {
                    player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.WATER_BUCKET)));
                    world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 0.7f, 1.0f);
                } else player.sendMessage(Text.translatable("message.alkahest.faucet_off"), true);
            }
            return ItemActionResult.success(world.isClient);
        }
        // Let empty-hand/default interaction toggle; don't accidentally consume held items.
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            if (player.isSneaking() && player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()) {
                if (world.getBlockEntity(pos) instanceof WaterFaucetBlockEntity faucet) faucet.retrieveLegacyFocus(player);
            } else {
                boolean enabled = !state.get(ENABLED);
                world.setBlockState(pos, state.with(ENABLED, enabled), Block.NOTIFY_ALL);
                player.sendMessage(Text.translatable(enabled ? "message.alkahest.faucet_on" : "message.alkahest.faucet_off"), true);
                world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.4f, enabled ? 0.6f : 0.5f);
            }
        }
        return ActionResult.success(world.isClient);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && !state.isOf(next.getBlock()) && world.getBlockEntity(pos) instanceof WaterFaucetBlockEntity faucet)
            for (ItemStack focus : faucet.stacks()) if (!focus.isEmpty())
                ItemScatterer.spawn(world, pos.getX() + 1.2, pos.getY() + .3, pos.getZ() + .5, focus);
        super.onStateReplaced(state, world, pos, next, moved);
    }
}
