package net.alkahest.block.entity;

import net.alkahest.block.custom.WaterFaucetBlock;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One reusable, physically installed Aqua focus. It is never consumed by filling. */
public final class WaterFaucetBlockEntity extends BlockEntity {
    private final DefaultedList<ItemStack> slots = DefaultedList.ofSize(1, ItemStack.EMPTY);
    public WaterFaucetBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.WATER_FAUCET, pos, state); }
    public DefaultedList<ItemStack> stacks() { return slots; }
    public static boolean accepts(ItemStack stack) {
        return stack.isOf(ModItems.CRYSTALS.get(EssenceType.AQUA)) || stack.isOf(ModItems.WANDS.get(EssenceType.AQUA));
    }
    public void insert(PlayerEntity player, Hand hand) {
        if (world == null || world.isClient) return;
        var held = player.getStackInHand(hand);
        if (!slots.get(0).isEmpty()) { player.sendMessage(Text.translatable("message.alkahest.faucet_occupied"), true); return; }
        if (!accepts(held)) { player.sendMessage(Text.translatable("message.alkahest.faucet_needs_aqua"), true); return; }
        slots.set(0, held.copyWithCount(1));
        held.decrement(1); // The installed physical item is retained even in creative mode.
        changed();
        player.sendMessage(Text.translatable("message.alkahest.faucet_inserted"), true);
    }
    public void take(PlayerEntity player) {
        if (world == null || world.isClient) return;
        ItemStack result = slots.get(0);
        if (result.isEmpty()) { player.sendMessage(Text.translatable("message.alkahest.faucet_needs_aqua"), true); return; }
        slots.set(0, ItemStack.EMPTY);
        if (!player.giveItemStack(result)) player.dropItem(result, false);
        changed();
        player.sendMessage(Text.translatable("message.alkahest.faucet_removed"), true);
    }
    private void changed() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof WaterFaucetBlock && state.get(WaterFaucetBlock.INSTALLED) != !slots.get(0).isEmpty())
                world.setBlockState(pos, state.with(WaterFaucetBlock.INSTALLED, !slots.get(0).isEmpty()), Block.NOTIFY_ALL);
        }
    }
    /** At most once per second; redstone pauses it and water in an already filled cauldron is untouched. */
    public static void tick(World world, BlockPos pos, WaterFaucetBlockEntity self) {
        if (world.isClient || world.getTime() % 20 != 0 || !accepts(self.slots.get(0)) || world.isReceivingRedstonePower(pos)) return;
        if (!world.isChunkLoaded(pos.down())) return;
        if (world.getBlockEntity(pos.down()) instanceof CauldronBlockEntity cauldron) cauldron.fillFromFaucet();
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) { super.writeNbt(nbt, registry); Inventories.writeNbt(nbt, slots, registry); }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) { super.readNbt(nbt, registry); slots.clear(); Inventories.readNbt(nbt, slots, registry); }
}
