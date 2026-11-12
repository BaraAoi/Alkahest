package net.alkahest.block.entity;

import net.alkahest.block.custom.WaterFaucetBlock;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The crystal is built into the recipe. The slot only preserves focuses from 0.1.84 saves. */
public final class WaterFaucetBlockEntity extends BlockEntity {
    private final DefaultedList<ItemStack> legacyFocus = DefaultedList.ofSize(1, ItemStack.EMPTY);
    public WaterFaucetBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.WATER_FAUCET, pos, state); }
    public DefaultedList<ItemStack> stacks() { return legacyFocus; }
    public void retrieveLegacyFocus(PlayerEntity player) {
        if (world == null || world.isClient) return;
        ItemStack result = legacyFocus.get(0);
        if (result.isEmpty()) { player.sendMessage(Text.translatable("message.alkahest.faucet_no_legacy"), true); return; }
        legacyFocus.set(0, ItemStack.EMPTY);
        if (!player.giveItemStack(result)) player.dropItem(result, false);
        markDirty();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof WaterFaucetBlock && state.get(WaterFaucetBlock.INSTALLED))
            world.setBlockState(pos, state.with(WaterFaucetBlock.INSTALLED, false));
        player.sendMessage(Text.translatable("message.alkahest.faucet_removed"), true);
    }
    public static void tick(World world, BlockPos pos, WaterFaucetBlockEntity self) {
        if (world.isClient || world.getTime() % 20 != 0 || !self.getCachedState().get(WaterFaucetBlock.ENABLED)
                || world.isReceivingRedstonePower(pos) || !world.isChunkLoaded(pos.down())) return;
        if (world.getBlockEntity(pos.down()) instanceof CauldronBlockEntity cauldron) cauldron.fillFromFaucet();
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) {
        super.writeNbt(nbt, registry); Inventories.writeNbt(nbt, legacyFocus, registry);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) {
        super.readNbt(nbt, registry); legacyFocus.clear(); Inventories.readNbt(nbt, legacyFocus, registry);
    }
}
