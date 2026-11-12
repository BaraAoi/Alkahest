package net.alkahest.block.entity;

import net.alkahest.block.custom.Tier2DistilleryBlock;
import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.component.ComponentMap;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.screen.Tier2DistilleryScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier-2 distillery core. It is a multiblock: the player stacks any number of
 * {@link DistilleryBellowsBlockEntity} directly above it, and the produced liquid essence
 * accumulates in those bellows instead of crystal slots. Unlike the crude tier-1 cube it
 * distils every essence type of the item, including composite ones.
 */
public class Tier2DistilleryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
    public static final int DURATION = 300;
    public static final int MAX_BELLOWS = 8;
    private static final int[] INPUT_SLOTS = {0};
    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_BLOCKED = 2, STATUS_NO_BELLOWS = 3;

    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private final List<EssenceStack> pending = new ArrayList<>();
    private int progress;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> DURATION;
                case 2 -> status();
                default -> {
                    int indexOfType = index - Tier2DistilleryScreenHandler.PENDING_START;
                    if (indexOfType < 0 || indexOfType >= EssenceType.values().length) yield 0;
                    EssenceType type = EssenceType.values()[indexOfType];
                    yield pending.stream().filter(e -> e.type() == type).mapToInt(EssenceStack::amount).sum();
                }
            };
        }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int size() { return Tier2DistilleryScreenHandler.PROPERTY_COUNT; }
    };

    public Tier2DistilleryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLERY_TIER2, pos, state);
    }

    public int progress() { return progress; }
    public List<EssenceStack> pending() { return pending; }

    public int status() {
        ItemStack input = stacks.get(0);
        if (bellows().isEmpty() && (!pending.isEmpty() || (!input.isEmpty() && accepts(input, world)))) return STATUS_NO_BELLOWS;
        if (!depositDryRun()) return STATUS_BLOCKED;
        if (input.isEmpty() || !accepts(input, world)) return STATUS_IDLE;
        return STATUS_WORKING;
    }

    /** Any mapped item with at least one usable essence is accepted; Vacuum cannot be produced. */
    public static boolean accepts(ItemStack stack) {
        return accepts(stack, null);
    }
    public static boolean accepts(ItemStack stack, World world) {
        if (!ItemEssenceMap.safeToConsume(stack)) return false;
        return ItemEssenceMap.get(stack, world).stream()
            .anyMatch(essence -> essence.type() != EssenceType.VACUUM && essence.amount() > 0);
    }

    public List<DistilleryBellowsBlockEntity> bellows() {
        List<DistilleryBellowsBlockEntity> result = new ArrayList<>();
        if (world == null) return result;
        for (int i = 1; i <= MAX_BELLOWS; i++) {
            BlockPos above = pos.up(i);
            if (!world.isChunkLoaded(above)) break;
            if (!(world.getBlockEntity(above) instanceof DistilleryBellowsBlockEntity bellows)) break;
            result.add(bellows);
        }
        return result;
    }

    /** Would every queued essence find room right now? Simulates the current bellow column. */
    private boolean depositDryRun() {
        if (pending.isEmpty()) return true;
        List<DistilleryBellowsBlockEntity> bellows = bellows();
        if (bellows.isEmpty()) return false;
        int[] free = new int[bellows.size()];
        EssenceType[] kinds = new EssenceType[bellows.size()];
        for (int i = 0; i < bellows.size(); i++) {
            DistilleryBellowsBlockEntity bellow = bellows.get(i);
            kinds[i] = bellow.empty() ? null : bellow.storedType();
            free[i] = bellow.empty() ? DistilleryBellowsBlockEntity.CAPACITY : DistilleryBellowsBlockEntity.CAPACITY - bellow.amount();
        }
        for (EssenceStack stack : pending) {
            int left = stack.amount();
            // Fill same-type flasks before claiming an empty one for a new type.
            for (int pass = 0; pass < 2 && left > 0; pass++) for (int i = 0; i < bellows.size() && left > 0; i++) {
                if (pass == 0 ? kinds[i] != stack.type() : kinds[i] != null) continue;
                int take = Math.min(left, free[i]);
                if (take <= 0) continue;
                if (kinds[i] == null) kinds[i] = stack.type();
                free[i] -= take;
                left -= take;
            }
            if (left > 0) return false;
        }
        return true;
    }

    /** Push queued essence into the bellows; returns true when the queue is empty. */
    private boolean deposit() {
        if (pending.isEmpty()) return true;
        List<DistilleryBellowsBlockEntity> bellows = bellows();
        boolean changed = false;
        for (int i = 0; i < pending.size(); i++) {
            EssenceStack stack = pending.get(i);
            int left = stack.amount();
            for (int pass = 0; pass < 2 && left > 0; pass++) for (DistilleryBellowsBlockEntity bellow : bellows) {
                if (left <= 0) break;
                if (pass == 0 ? bellow.empty() || bellow.storedType() != stack.type() : !bellow.empty()) continue;
                left -= bellow.accept(stack.type(), left, stack.stability());
            }
            if (left <= 0) {
                pending.remove(i--); changed = true;
            } else if (left != stack.amount()) {
                pending.set(i, new EssenceStack(stack.type(), left, stack.stability())); changed = true;
            }
        }
        if (changed) markDirty();
        if (changed && world != null && !world.isClient) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
        return pending.isEmpty();
    }

    /** All essences of the item, including composites, with a stable-tier stability roll. */
    public static List<EssenceStack> outputs(ItemStack input, net.minecraft.util.math.random.Random random, boolean midas) {
        return outputs(input, random, midas, null);
    }
    public static List<EssenceStack> outputs(ItemStack input, net.minecraft.util.math.random.Random random, boolean midas, World world) {
        List<EssenceStack> result = new ArrayList<>();
        for (EssenceStack essence : ItemEssenceMap.get(input, world)) {
            if (essence.type() == EssenceType.VACUUM || essence.amount() <= 0) continue;
            // Preserve the previously approved Gelum output; other types use the full item composition.
            int total = essence.type() == EssenceType.GELUM ? Math.min(8, essence.amount()) : essence.amount();
            result.add(new EssenceStack(essence.type(), total, MidasTouchEffect.outputStability(0.55f + random.nextFloat() * 0.15f, midas)));
        }
        return result;
    }

    private static void setWorking(World world, BlockPos pos, boolean working) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof Tier2DistilleryBlock && state.get(Tier2DistilleryBlock.WORKING) != working)
            world.setBlockState(pos, state.with(Tier2DistilleryBlock.WORKING, working), Block.NOTIFY_ALL);
    }

    public static void tick(World world, BlockPos pos, Tier2DistilleryBlockEntity self) {
        tickWithMidas(world, pos, self, MidasTouchEffect.nearby(world, pos), world.getTime());
    }

    /** Shared by the real ticker and deterministic server GameTests. */
    static void tickWithMidas(World world, BlockPos pos, Tier2DistilleryBlockEntity self, boolean midas, long time) {
        if (world.isClient) return;
        if (!self.deposit()) { setWorking(world, pos, false); return; }
        ItemStack input = self.stacks.get(0);
        if (input.isEmpty() || !accepts(input, world)) {
            if (self.progress != 0) { self.progress = 0; self.markDirty(); }
            setWorking(world, pos, false);
            return;
        }
        if (self.bellows().isEmpty()) { setWorking(world, pos, false); return; }
        setWorking(world, pos, true);
        self.progress += MidasTouchEffect.progressStep(midas, time);
        if (self.progress >= DURATION) {
            List<EssenceStack> output = outputs(input, world.random, midas, world);
            self.pending.addAll(output);
            for (EssenceStack e : output)
                net.alkahest.atmosphere.AtmosphereEvents.emit(world, pos, e.type(), e.amount() * 0.1);
            input.decrement(1);
            if (input.isEmpty()) self.stacks.set(0, ItemStack.EMPTY);
            self.progress = 0;
            boolean delivered = self.deposit();
            setWorking(world, pos, delivered && !self.stacks.get(0).isEmpty());
        }
        self.markDirty();
    }

    // --- inventory ------------------------------------------------------------------------
    @Override public int size() { return 1; }
    @Override public boolean isEmpty() { return stacks.get(0).isEmpty(); }
    @Override public ItemStack getStack(int slot) { return stacks.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) { progress = 0; markDirty(); }
        return removed;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) { progress = 0; markDirty(); }
        return removed;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        ItemStack current = stacks.get(slot);
        if (current.isEmpty() || stack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(current, stack)) progress = 0;
        stacks.set(slot, stack);
        markDirty();
    }
    @Override public void clear() { stacks.clear(); pending.clear(); progress = 0; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) { return slot == 0 && accepts(stack, world); }
    /** Hopper access is input-only from all sides; liquid output leaves through bellows. */
    @Override public int[] getAvailableSlots(Direction side) { return INPUT_SLOTS; }
    @Override public boolean canInsert(int slot, ItemStack stack, Direction side) { return isValid(slot, stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction side) { return false; }

    @Override public Text getDisplayName() { return Text.translatable("screen.alkahest.distillery_tier2.title"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new Tier2DistilleryScreenHandler(syncId, playerInventory, this, properties);
    }

    @Override protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        if (!pending.isEmpty()) builder.add(ModDataComponents.DISTILLERY_CONTENTS, new JarContentsComponent(pending));
    }
    @Override protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        var saved = components.getOrDefault(ModDataComponents.DISTILLERY_CONTENTS, JarContentsComponent.EMPTY);
        pending.clear();
        saved.contents().stream().filter(entry -> entry.type() != EssenceType.VACUUM).forEach(pending::add);
        markDirty();
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, stacks, registries);
        nbt.putInt("Progress", progress);
        if (!pending.isEmpty()) {
            NbtList list = new NbtList();
            for (EssenceStack stack : pending) {
                NbtCompound entry = new NbtCompound();
                entry.putString("Type", stack.type().asString());
                entry.putInt("Amount", stack.amount());
                entry.putFloat("Stability", stack.stability());
                list.add(entry);
            }
            nbt.put("Pending", list);
        }
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, stacks, registries);
        progress = nbt.getInt("Progress");
        pending.clear();
        if (nbt.contains("Pending", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Pending", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound entry = list.getCompound(i);
                EssenceType type = null;
                for (EssenceType candidate : EssenceType.values())
                    if (candidate.asString().equals(entry.getString("Type"))) type = candidate;
                if (type != null) pending.add(new EssenceStack(type, entry.getInt("Amount"), entry.getFloat("Stability")));
            }
        }
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
