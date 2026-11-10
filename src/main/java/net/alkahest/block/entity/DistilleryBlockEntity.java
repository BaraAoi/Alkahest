package net.alkahest.block.entity;

import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.alkahest.screen.DistilleryScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DistilleryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
    public static final int OUTPUT_START = 1, OUTPUT_END = 8, PENDING = 8, SIZE = 9;
    private static final int[] INPUT_SLOTS = {0}, OUTPUT_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    // Slots 0 and 1 retain their old NBT indices; slot 8 is an inaccessible, persisted pending result.
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private int progress;
    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> 1200;
                case 2 -> !stacks.get(PENDING).isEmpty() ? 2 : accepts(stacks.get(0)) ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int size() { return 3; }
    };
    public DistilleryBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.DISTILLERY, pos, state); }
    public DefaultedList<ItemStack> stacks() { return stacks; }
    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && ItemEssenceMap.get(stack).stream().anyMatch(e ->
            e.type() == EssenceType.HERBA || e.type() == EssenceType.AQUA || e.type() == EssenceType.MORTUUS);
    }
    /** Unique primary leaves, recursively resolving nested compounds; Vacuum has no parents and yields nothing. */
    public static List<EssenceType> primaryCandidates(List<EssenceStack> essences) {
        Set<EssenceType> result = new LinkedHashSet<>();
        for (EssenceStack essence : essences) addPrimary(essence.type(), result);
        return List.copyOf(result);
    }
    private static void addPrimary(EssenceType type, Set<EssenceType> result) {
        if (type.tier() == EssenceType.Tier.PRIMA) result.add(type);
        else if (type.tier() == EssenceType.Tier.COMPOSITA) type.parents().forEach(parent -> addPrimary(parent, result));
    }
    public void cancelProgress() { if (progress != 0) { progress = 0; markDirty(); } }
    public void discoverOutput(PlayerEntity player, ItemStack output) {
        if (world != null && !world.isClient && player instanceof ServerPlayerEntity researcher && ModItems.CRYSTALS.containsValue(output.getItem()))
            EarlyResearch.discoverSight(researcher, world, pos);
    }
    @Override public int size() { return SIZE; }
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
        if (slot == 0 && (stack.isEmpty() || stacks.get(0).isEmpty() || !ItemStack.areItemsAndComponentsEqual(stacks.get(0), stack))) progress = 0;
        stacks.set(slot, stack); markDirty();
    }
    @Override public void clear() { stacks.clear(); progress = 0; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) { return slot == 0 && accepts(stack); }
    @Override public int[] getAvailableSlots(Direction side) { return side == Direction.UP ? INPUT_SLOTS : OUTPUT_SLOTS; }
    @Override public boolean canInsert(int slot, ItemStack stack, Direction side) { return side == Direction.UP && slot == 0 && accepts(stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction side) { return slot >= OUTPUT_START && slot < OUTPUT_END; }
    @Override public Text getDisplayName() { return Text.translatable("screen.alkahest.distillery.title"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new DistilleryScreenHandler(syncId, playerInventory, this, properties);
    }
    /** Average stability by crystal count when compatible crystals merge, or use an empty output. */
    private boolean depositPending() {
        ItemStack pending = stacks.get(PENDING);
        if (pending.isEmpty()) return true;
        for (int i = OUTPUT_START; i < OUTPUT_END; i++) {
            ItemStack existing = stacks.get(i);
            if (existing.isEmpty() || !existing.isOf(pending.getItem())) continue;
            ItemStack comparable = pending.copy();
            Float stability = existing.get(ModDataComponents.ESSENCE_STABILITY);
            if (stability != null && comparable.contains(ModDataComponents.ESSENCE_STABILITY)) comparable.set(ModDataComponents.ESSENCE_STABILITY, stability);
            if (ItemStack.areItemsAndComponentsEqual(existing, comparable) && existing.getCount() + pending.getCount() <= existing.getMaxCount()) {
                Float newStability = pending.get(ModDataComponents.ESSENCE_STABILITY);
                if (stability != null && newStability != null) {
                    float mean = (stability * existing.getCount() + newStability * pending.getCount()) / (existing.getCount() + pending.getCount());
                    existing.set(ModDataComponents.ESSENCE_STABILITY, mean);
                }
                existing.increment(pending.getCount()); stacks.set(PENDING, ItemStack.EMPTY); markDirty(); return true;
            }
        }
        for (int i = OUTPUT_START; i < OUTPUT_END; i++) if (stacks.get(i).isEmpty()) {
            stacks.set(i, pending); stacks.set(PENDING, ItemStack.EMPTY); markDirty(); return true;
        }
        return false;
    }
    public static void tick(World world, BlockPos pos, DistilleryBlockEntity self) {
        if (!self.depositPending()) return;
        ItemStack input = self.stacks.get(0);
        if (input.isEmpty() || !accepts(input)) { self.cancelProgress(); return; }
        if (++self.progress >= 1200) {
            List<EssenceType> primaries = primaryCandidates(ItemEssenceMap.get(input));
            ItemStack result;
            if (primaries.isEmpty() || world.random.nextFloat() < 0.2f) result = new ItemStack(ModItems.ASH);
            else {
                EssenceType type = primaries.get(world.random.nextInt(primaries.size()));
                result = new ItemStack(ModItems.CRYSTALS.get(type), 1 + world.random.nextInt(3));
                result.set(ModDataComponents.ESSENCE_STABILITY, 0.15f + world.random.nextFloat() * 0.15f);
            }
            input.decrement(1);
            if (input.isEmpty()) self.stacks.set(0, ItemStack.EMPTY);
            self.stacks.set(PENDING, result); self.progress = 0;
            self.depositPending();
        }
        self.markDirty();
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, stacks, registries); nbt.putInt("Progress", progress);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); stacks.clear(); Inventories.readNbt(nbt, stacks, registries); progress = nbt.getInt("Progress");
    }
}
