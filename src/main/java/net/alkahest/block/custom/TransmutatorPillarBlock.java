package net.alkahest.block.custom;

import net.alkahest.block.entity.TransmutatorPillarBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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

/** One of the eight quartz pillars of the Transmutator; it hosts the essence buffer of a side. */
public class TransmutatorPillarBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty CHARGED = BooleanProperty.of("charged");

    public TransmutatorPillarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(CHARGED, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(CHARGED); }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TransmutatorPillarBlockEntity(pos, state);
    }

    private void report(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof TransmutatorPillarBlockEntity pillar) {
            pillar.describe(player);
        }
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        report(world, pos, player);
        return ItemActionResult.success(world.isClient);
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        report(world, pos, player);
        return ActionResult.success(world.isClient);
    }
}
