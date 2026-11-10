package net.alkahest.block.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.research.EarlyResearch;
import net.minecraft.server.network.ServerPlayerEntity;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.math.Direction;
import net.alkahest.screen.DistilleryScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DistilleryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
    // Keep the existing two-slot serialized layout for old worlds.
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private int progress;
    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) { return index == 0 ? progress : index == 1 ? 1200 : 0; }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int size() { return 2; }
    };
    public DistilleryBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.DISTILLERY, pos, state); }
    public DefaultedList<ItemStack> stacks() { return stacks; }
    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && ItemEssenceMap.get(stack).stream().anyMatch(e ->
            e.type() == EssenceType.HERBA || e.type() == EssenceType.AQUA || e.type() == EssenceType.MORTUUS);
    }
    public void insert(PlayerEntity player, Hand hand) {
        if (!stacks.get(0).isEmpty() || !stacks.get(1).isEmpty()) { message(player, "message.alkahest.distillery_busy"); return; }
        ItemStack held = player.getStackInHand(hand);
        if (!accepts(held)) { message(player, "message.alkahest.distillery_organic"); return; }
        stacks.set(0, held.copyWithCount(1)); held.decrementUnlessCreative(1, player);
        progress = 0; markDirty(); message(player, "message.alkahest.distillery_started");
    }
    public void collect(PlayerEntity player) {
        if (stacks.get(1).isEmpty()) {
            if (!stacks.get(0).isEmpty()) player.sendMessage(Text.translatable("message.alkahest.distillery_progress", Math.min(100, progress * 100 / 1200)), true);
            else message(player, "message.alkahest.distillery_wait");
            return;
        }
        ItemStack output = stacks.get(1); stacks.set(1, ItemStack.EMPTY);
        discoverOutput(player, output);
        if (!player.giveItemStack(output)) player.dropItem(output, false);
        markDirty();
    }
    public void discoverOutput(PlayerEntity player, ItemStack output) {
        if (world != null && !world.isClient && player instanceof ServerPlayerEntity researcher && ModItems.CRYSTALS.containsValue(output.getItem()))
            EarlyResearch.discoverSight(researcher, world, pos);
    }
    @Override public int size() { return 2; }
    @Override public boolean isEmpty() { return stacks.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return stacks.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) { if (slot == 0) progress = 0; markDirty(); }
        return removed;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) { if (slot == 0) progress = 0; markDirty(); }
        return removed;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        if (slot == 0) progress = 0;
        stacks.set(slot, stack); markDirty();
    }
    @Override public void clear() { stacks.clear(); progress = 0; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) { return slot == 0 && accepts(stack); }
    @Override public int[] getAvailableSlots(Direction side) { return side == Direction.UP ? new int[]{0} : new int[]{1}; }
    @Override public boolean canInsert(int slot, ItemStack stack, Direction side) { return side == Direction.UP && slot == 0 && stacks.get(0).isEmpty() && accepts(stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction side) { return slot == 1; }
    @Override public Text getDisplayName() { return Text.translatable("screen.alkahest.distillery.title"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new DistilleryScreenHandler(syncId, playerInventory, this, properties);
    }
    public static void tick(World world, BlockPos pos, DistilleryBlockEntity self) {
        if (self.stacks.get(0).isEmpty() || !self.stacks.get(1).isEmpty()) return;
        if (!accepts(self.stacks.get(0))) { if (self.progress != 0) { self.progress = 0; self.markDirty(); } return; }
        if (++self.progress >= 1200) {
            var essences = ItemEssenceMap.get(self.stacks.get(0));
            ItemStack result;
            if (essences.isEmpty() || world.random.nextFloat() < 0.2f) result = new ItemStack(ModItems.ASH);
            else {
                var essence = essences.get(world.random.nextInt(essences.size()));
                result = new ItemStack(ModItems.CRYSTALS.get(essence.type()), 1 + world.random.nextInt(3));
                result.set(ModDataComponents.ESSENCE_STABILITY, 0.15f + world.random.nextFloat() * 0.15f);
            }
            self.stacks.get(0).decrement(1);
            if (self.stacks.get(0).isEmpty()) self.stacks.set(0, ItemStack.EMPTY);
            self.stacks.set(1, result); self.progress = 0;
        }
        self.markDirty();
    }
    private void message(PlayerEntity player, String key) { player.sendMessage(Text.translatable(key), true); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, stacks, registries); nbt.putInt("Progress", progress);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); Inventories.readNbt(nbt, stacks, registries); progress = nbt.getInt("Progress");
    }
}
