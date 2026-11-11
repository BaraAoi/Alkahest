package net.alkahest.block.custom;

import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
import net.alkahest.item.custom.CrystalWandItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The chamber or crown of the compact Transmutator. Interactions address the core below. */
public final class TransmutatorAssemblyBlock extends Block {
    public static final BooleanProperty AWAKE = BooleanProperty.of("awake");
    private final int coreDistance;

    public TransmutatorAssemblyBlock(Settings settings, int coreDistance) {
        super(settings);
        this.coreDistance = coreDistance;
        setDefaultState(getStateManager().getDefaultState().with(AWAKE, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(AWAKE); }

    private void interact(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
        if (world.isClient) return;
        BlockPos below = pos.down(coreDistance);
        if (!(world.getBlockEntity(below) instanceof TransmutatorCoreBlockEntity core) || !core.verticalFormed()) return;
        if (stack.getItem() instanceof CrystalWandItem) core.wake();
        else if (core.awake()) player.openHandledScreen(core);
        else player.sendMessage(net.minecraft.text.Text.translatable("message.alkahest.transmutator_dormant"), true);
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
}
