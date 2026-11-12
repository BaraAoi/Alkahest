package net.alkahest.block.entity;

import net.alkahest.block.custom.DistilleryBlock;
import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.item.custom.AlkahestVialItem;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.alkahest.screen.DistilleryScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DistilleryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
    public static final int OUTPUT_START = 1, OUTPUT_END = 8, PENDING = 8, CATALYST = 9, SIZE = 10;
    public static final int RAW_DURATION = 1200;
    public static final int ALKAHEST_DURATION = 100;
    private static final int[] INPUT_SLOTS = {0}, OUTPUT_AND_CATALYST_SLOTS = {1, 2, 3, 4, 5, 6, 7, CATALYST};

    // Slots 0..8 retain their historical NBT indices; slot 9 holds the optional Alkahest solvent vial.
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private final List<ItemStack> extraPending = new ArrayList<>();
    private int progress;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> currentMaxProgress();
                case 2 -> hasPendingOutput() ? 2 : canProcess(stacks.get(0)) ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int size() { return 3; }
    };

    public DistilleryBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.DISTILLERY, pos, state); }
    public DefaultedList<ItemStack> stacks() { return stacks; }

    public boolean hasAlkahest() {
        ItemStack vial = stacks.get(CATALYST);
        return vial.isOf(ModItems.ALKAHEST_VIAL) && AlkahestVialItem.charges(vial) > 0;
    }

    public int currentMaxProgress() {
        return hasAlkahest() ? ALKAHEST_DURATION : RAW_DURATION;
    }

    private boolean hasPendingOutput() {
        return !stacks.get(PENDING).isEmpty() || !extraPending.isEmpty();
    }

    /** Raw Era II mode accepts only organic inputs containing Herba, Aqua or Mortuus. */
    public static boolean accepts(ItemStack stack) {
        return accepts(stack, null);
    }
    public static boolean accepts(ItemStack stack, World world) {
        return ItemEssenceMap.safeToConsume(stack) && ItemEssenceMap.get(stack, world).stream().anyMatch(e ->
            e.type() == EssenceType.HERBA || e.type() == EssenceType.AQUA || e.type() == EssenceType.MORTUUS);
    }

    /** Era III Alkahest mode accepts any mapped item that yields at least one crystallizable essence. */
    public static boolean acceptsWithAlkahest(ItemStack stack) {
        return acceptsWithAlkahest(stack, null);
    }
    public static boolean acceptsWithAlkahest(ItemStack stack, World world) {
        return ItemEssenceMap.safeToConsume(stack) && ItemEssenceMap.get(stack, world).stream()
            .anyMatch(e -> ModItems.CRYSTALS.containsKey(e.type()));
    }

    public static boolean acceptsCatalyst(ItemStack stack) {
        return stack.isOf(ModItems.ALKAHEST_VIAL) && AlkahestVialItem.charges(stack) > 0;
    }

    public boolean canProcess(ItemStack stack) {
        return hasAlkahest() ? acceptsWithAlkahest(stack, world) : accepts(stack, world);
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

    public void dropExtraPending(World world, BlockPos pos) {
        for (ItemStack extra : extraPending) {
            if (!extra.isEmpty()) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, extra);
            }
        }
        extraPending.clear();
    }

    public void discoverOutput(PlayerEntity player, ItemStack output) {
        if (world != null && !world.isClient && player instanceof ServerPlayerEntity researcher && ModItems.CRYSTALS.containsValue(output.getItem()))
            EarlyResearch.discoverSight(researcher, world, pos);
    }

    @Override public int size() { return SIZE; }
    @Override public boolean isEmpty() { return stacks.stream().allMatch(ItemStack::isEmpty) && extraPending.isEmpty(); }
    @Override public ItemStack getStack(int slot) { return stacks.get(slot); }

    @Override public ItemStack removeStack(int slot, int amount) {
        boolean wasAlkahest = hasAlkahest();
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == 0 || (slot == CATALYST && wasAlkahest != hasAlkahest())) progress = 0;
            markDirty();
        }
        return removed;
    }

    @Override public ItemStack removeStack(int slot) {
        boolean wasAlkahest = hasAlkahest();
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) {
            if (slot == 0 || (slot == CATALYST && wasAlkahest != hasAlkahest())) progress = 0;
            markDirty();
        }
        return removed;
    }

    @Override public void setStack(int slot, ItemStack stack) {
        if (slot == 0 && (stack.isEmpty() || stacks.get(0).isEmpty() || !ItemStack.areItemsAndComponentsEqual(stacks.get(0), stack))) {
            progress = 0;
        }
        boolean wasAlkahest = hasAlkahest();
        stacks.set(slot, stack);
        // Raw and solvent modes have different durations and output; never reuse progress
        // from the other mode, even when the same organic input is valid for both.
        if (slot == CATALYST && wasAlkahest != hasAlkahest()) progress = 0;
        markDirty();
    }

    @Override public void clear() { stacks.clear(); extraPending.clear(); progress = 0; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) {
        if (slot == 0) return canProcess(stack);
        if (slot == CATALYST) return acceptsCatalyst(stack);
        return false;
    }
    @Override public int[] getAvailableSlots(Direction side) { return side == Direction.UP ? INPUT_SLOTS : OUTPUT_AND_CATALYST_SLOTS; }
    @Override public boolean canInsert(int slot, ItemStack stack, Direction side) {
        if (side == Direction.UP) return slot == 0 && canProcess(stack);
        return side != Direction.DOWN && slot == CATALYST && acceptsCatalyst(stack);
    }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction side) {
        return side != Direction.UP && ((slot >= OUTPUT_START && slot < OUTPUT_END)
            || (slot == CATALYST && stack.isOf(Items.GLASS_BOTTLE)));
    }
    @Override public Text getDisplayName() { return Text.translatable("screen.alkahest.distillery.title"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new DistilleryScreenHandler(syncId, playerInventory, this, properties);
    }

    private boolean depositOnePending() {
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

    /** Average stability by crystal count when compatible crystals merge, or use empty outputs across all queued results. */
    private boolean depositPending() {
        while (true) {
            if (stacks.get(PENDING).isEmpty() && !extraPending.isEmpty()) {
                stacks.set(PENDING, extraPending.remove(0));
                markDirty();
            }
            if (stacks.get(PENDING).isEmpty()) {
                return true;
            }
            if (!depositOnePending()) {
                return false;
            }
        }
    }

    /** Update the visible state only on transitions; same-block changes preserve the inventory. */
    private static void setWorking(World world, BlockPos pos, boolean working) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DistilleryBlock && state.get(DistilleryBlock.WORKING) != working)
            world.setBlockState(pos, state.with(DistilleryBlock.WORKING, working), Block.NOTIFY_ALL);
    }

    public static void tick(World world, BlockPos pos, DistilleryBlockEntity self) {
        tickWithMidas(world, pos, self, MidasTouchEffect.nearby(world, pos), world.getTime());
    }

    /** Shared by the real ticker and deterministic server GameTests. */
    static void tickWithMidas(World world, BlockPos pos, DistilleryBlockEntity self, boolean midas, long time) {
        if (!self.depositPending()) { setWorking(world, pos, false); return; }
        ItemStack input = self.stacks.get(0);
        if (input.isEmpty() || !self.canProcess(input)) { self.cancelProgress(); setWorking(world, pos, false); return; }
        setWorking(world, pos, true);
        boolean alkahestMode = self.hasAlkahest();
        int targetTicks = alkahestMode ? ALKAHEST_DURATION : RAW_DURATION;
        self.progress += MidasTouchEffect.progressStep(midas, time);
        if (self.progress >= targetTicks) {
            if (alkahestMode) {
                List<ItemStack> outputs = new ArrayList<>();
                for (EssenceStack es : ItemEssenceMap.get(input, world)) {
                    Item crystalItem = ModItems.CRYSTALS.get(es.type());
                    if (crystalItem == null) continue;
                    ItemStack out = new ItemStack(crystalItem, Math.max(1, Math.min(8, es.amount())));
                    out.set(ModDataComponents.ESSENCE_STABILITY, MidasTouchEffect.outputStability(0.45f + world.random.nextFloat() * 0.15f, midas));
                    outputs.add(out);
                }
                self.stacks.set(CATALYST, AlkahestVialItem.consumeCharge(self.stacks.get(CATALYST)));
                if (!outputs.isEmpty()) {
                    self.stacks.set(PENDING, outputs.get(0));
                    for (int i = 1; i < outputs.size(); i++) {
                        self.extraPending.add(outputs.get(i));
                    }
                }
            } else {
                List<EssenceType> primaries = primaryCandidates(ItemEssenceMap.get(input, world));
                ItemStack result;
                if (primaries.isEmpty() || world.random.nextFloat() < 0.2f) result = new ItemStack(ModItems.ASH);
                else {
                    EssenceType type = primaries.get(world.random.nextInt(primaries.size()));
                    result = new ItemStack(ModItems.CRYSTALS.get(type), 1 + world.random.nextInt(3));
                    result.set(ModDataComponents.ESSENCE_STABILITY, MidasTouchEffect.outputStability(0.15f + world.random.nextFloat() * 0.15f, midas));
                }
                self.stacks.set(PENDING, result);
            }
            // A small atmospheric byproduct, independent of the useful output.
            for (EssenceStack e : ItemEssenceMap.get(input, world))
                net.alkahest.atmosphere.AtmosphereEvents.emit(world, pos, e.type(), Math.min(8, e.amount()) * 0.1);
            input.decrement(1);
            if (input.isEmpty()) self.stacks.set(0, ItemStack.EMPTY);
            self.progress = 0;
            boolean delivered = self.depositPending();
            setWorking(world, pos, delivered && self.canProcess(self.stacks.get(0)));
        }
        self.markDirty();
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, stacks, registries);
        nbt.putInt("Progress", progress);
        if (!extraPending.isEmpty()) {
            NbtList list = new NbtList();
            for (ItemStack extra : extraPending) {
                if (!extra.isEmpty()) {
                    list.add(extra.encode(registries));
                }
            }
            nbt.put("ExtraPending", list);
        }
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        stacks.clear();
        Inventories.readNbt(nbt, stacks, registries);
        int savedProgress = nbt.getInt("Progress");
        // Old saves may have switched modes in the GUI without resetting the counter.
        // No normal tick can persist a counter at or beyond its current duration.
        progress = savedProgress >= 0 && savedProgress < currentMaxProgress() ? savedProgress : 0;
        extraPending.clear();
        if (nbt.contains("ExtraPending", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("ExtraPending", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                ItemStack.fromNbt(registries, list.getCompound(i)).ifPresent(s -> {
                    if (!s.isEmpty()) extraPending.add(s);
                });
            }
        }
    }
}
