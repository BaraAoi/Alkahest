package net.alkahest.block.entity;

import net.alkahest.block.custom.AlchemicalBrickBlock;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.block.custom.MatrixPillarBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.screen.MatrixScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Centre of the 3x3x3 Matrix. The base item sits inside the core and the extra parts lie on the
 * pedestals around the cube; essence is drawn out of the jars through the air. A freshly built
 * cube is dormant — a wand has to wake it up before any ring starts to turn.
 */
public class MatrixCoreBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    public static final int DURATION = 160;
    public static final int BUFFER = 128;
    public static final int REACH = 12;
    public static final int PEDESTAL_REACH = 5;
    public static final int BASE = 0, OUTPUT = 1;

    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_NO_ESSENCE = 2,
        STATUS_BLOCKED = 3, STATUS_UNFORMED = 4, STATUS_NO_RECIPE = 5, STATUS_ASLEEP = 6;

    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private JarContentsComponent buffer = JarContentsComponent.EMPTY;
    private int progress;
    private boolean awake;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> DURATION;
                case 2 -> status();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int size() { return 3; }
    };

    public MatrixCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_CORE, pos, state);
    }

    public int progress() { return progress; }
    public JarContentsComponent buffer() { return buffer; }
    public boolean awake() { return awake; }

    /** Eight pillars on the corners and eighteen bricks on the rest of the 3x3x3 shell. */
    public boolean formed() {
        if (world == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block block = world.getBlockState(pos.add(dx, dy, dz)).getBlock();
                    boolean corner = dx != 0 && dy != 0 && dz != 0;
                    if (corner && !(block instanceof MatrixPillarBlock)) return false;
                    if (!corner && !(block instanceof AlchemicalBrickBlock)) return false;
                }
            }
        }
        return true;
    }

    /** Every pedestal standing around the cube. */
    public List<MatrixPedestalBlockEntity> pedestals() {
        List<MatrixPedestalBlockEntity> result = new ArrayList<>();
        if (world == null) return result;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-PEDESTAL_REACH, -2, -PEDESTAL_REACH),
            pos.add(PEDESTAL_REACH, 2, PEDESTAL_REACH))) {
            if (candidate.getSquaredDistance(pos) > PEDESTAL_REACH * PEDESTAL_REACH) continue;
            if (world.getBlockEntity(candidate) instanceof MatrixPedestalBlockEntity pedestal) result.add(pedestal);
        }
        return result;
    }

    public List<ItemStack> pedestalItems() {
        List<ItemStack> result = new ArrayList<>();
        for (MatrixPedestalBlockEntity pedestal : pedestals()) {
            if (!pedestal.isEmpty()) result.add(pedestal.held());
        }
        return result;
    }

    public MatrixRecipe recipe() {
        if (world == null) return null;
        return MatrixRecipe.find(stacks.get(BASE), pedestalItems());
    }

    public int status() {
        if (!formed()) return STATUS_UNFORMED;
        if (!awake) return STATUS_ASLEEP;
        MatrixRecipe recipe = recipe();
        if (recipe == null) return stacks.get(BASE).isEmpty() ? STATUS_IDLE : STATUS_NO_RECIPE;
        if (!canStoreOutput(recipe.output())) return STATUS_BLOCKED;
        if (progress >= DURATION) return STATUS_NO_ESSENCE;
        return STATUS_WORKING;
    }

    private boolean canStoreOutput(ItemStack product) {
        if (product.isEmpty()) return false;
        ItemStack output = stacks.get(OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.areItemsEqual(output, product)) return false;
        return output.getCount() + product.getCount() <= output.getMaxCount();
    }

    /** Wakes the cube up or puts it back to sleep; the block state follows immediately. */
    public void setAwake(World world, BlockPos pos, boolean value) {
        if (awake == value) return;
        awake = value;
        if (!value) progress = 0;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof MatrixCoreBlock && state.get(MatrixCoreBlock.AWAKE) != value) {
            world.setBlockState(pos, state.with(MatrixCoreBlock.AWAKE, value), Block.NOTIFY_ALL);
        }
        if (value) {
            wakeEffect(world, pos);
        } else {
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.7f, 0.8f);
        }
        markDirty();
    }

    public static void tick(World world, BlockPos pos, MatrixCoreBlockEntity self) {
        if (world.isClient) return;
        if (!self.formed()) {
            if (self.awake) self.setAwake(world, pos, false);
            if (self.progress != 0) { self.progress = 0; self.markDirty(); }
            setActive(world, pos, self, false);
            return;
        }
        MatrixRecipe recipe = self.awake ? self.recipe() : null;
        if (recipe == null || !self.canStoreOutput(recipe.output())) {
            if (self.progress != 0) { self.progress = 0; self.markDirty(); }
            setActive(world, pos, self, false);
            return;
        }
        if (self.progress < DURATION) {
            self.progress++;
            if (self.progress % 10 == 0) self.prefill(world, recipe.cost());
            setActive(world, pos, self, true);
            self.markDirty();
            return;
        }
        if (!self.pay(world, recipe.cost())) {
            setActive(world, pos, self, false);
            return;
        }
        consumeItems(recipe);
        self.progress = 0;
        self.markDirty();
        celebrate(world, pos);
    }

    /** Spends the base item and one copy of every part lying on the pedestals. */
    private void consumeItems(MatrixRecipe recipe) {
        ItemStack base = stacks.get(BASE);
        base.decrement(1);
        if (base.isEmpty()) stacks.set(BASE, ItemStack.EMPTY);
        for (net.minecraft.item.Item component : recipe.components()) {
            for (MatrixPedestalBlockEntity pedestal : pedestals()) {
                if (pedestal.isEmpty()) continue;
                if (pedestal.held().getItem() != component) continue;
                pedestal.takeOne();
                break;
            }
        }
        ItemStack product = recipe.output().copy();
        ItemStack stored = stacks.get(OUTPUT);
        if (stored.isEmpty()) stacks.set(OUTPUT, product);
        else stored.increment(product.getCount());
    }

    /** Draws one unit of every still-missing essence, so sparks fly for the whole operation. */
    private void prefill(World world, Map<EssenceType, Integer> cost) {
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            EssenceType type = entry.getKey();
            if (stored(type) >= entry.getValue()) continue;
            request(world, type, 1);
        }
    }

    /** Pays the whole price at once; nothing is taken when it cannot be paid. */
    private boolean pay(World world, Map<EssenceType, Integer> cost) {
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            int need = entry.getValue();
            if (stored(entry.getKey()) + jarsAround(world, entry.getKey()) < need) return false;
        }
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            int need = entry.getValue();
            while (need > 0 && stored(entry.getKey()) < need) {
                int pulled = request(world, entry.getKey(), need - stored(entry.getKey()));
                if (pulled <= 0) break;
            }
            if (stored(entry.getKey()) < need) return false;
            take(entry.getKey(), need);
        }
        markDirty();
        return true;
    }

    public int stored(EssenceType type) {
        for (EssenceStack stack : buffer.contents()) if (stack.type() == type) return stack.amount();
        return 0;
    }

    /** Pulls up to {@code count} units out of the nearest matching jar with a visible trail. */
    public int request(World world, EssenceType type, int count) {
        int room = BUFFER - buffer.total();
        if (room <= 0) return 0;
        JarBlockEntity jar = findJar(world, type);
        if (jar == null) return 0;
        EssenceStack source = jar.contents().contents().getFirst();
        int want = Math.min(Math.min(count, room), source.amount());
        if (want <= 0) return 0;
        List<EssenceStack> remainder = new ArrayList<>(jar.contents().contents());
        remainder.remove(source);
        if (source.amount() > want) {
            remainder.add(new EssenceStack(source.type(), source.amount() - want, source.stability()));
        }
        jar.setContents(new JarContentsComponent(remainder));
        buffer = buffer.merge(new JarContentsComponent(
            List.of(new EssenceStack(type, want, source.stability()))), BUFFER);
        flyTrail(world, jar.getPos(), type, want);
        markDirty();
        return want;
    }

    /** Removes up to {@code count} units of the type from the core buffer. */
    public int take(EssenceType type, int count) {
        int removed = 0;
        List<EssenceStack> remainder = new ArrayList<>();
        for (EssenceStack stack : buffer.contents()) {
            if (stack.type() != type || removed >= count) { remainder.add(stack); continue; }
            int taken = Math.min(count - removed, stack.amount());
            removed += taken;
            if (stack.amount() > taken) {
                remainder.add(new EssenceStack(type, stack.amount() - taken, stack.stability()));
            }
        }
        if (removed == 0) return 0;
        buffer = new JarContentsComponent(remainder);
        markDirty();
        return removed;
    }

    private int jarsAround(World world, EssenceType type) {
        int total = 0;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-REACH, -REACH, -REACH), pos.add(REACH, REACH, REACH))) {
            if (!(world.getBlockEntity(candidate) instanceof JarBlockEntity jar)) continue;
            JarContentsComponent contents = jar.contents();
            if (contents.contents().size() != 1) continue;
            if (contents.contents().getFirst().type() != type) continue;
            total += contents.total();
        }
        return total;
    }

    private JarBlockEntity findJar(World world, EssenceType type) {
        JarBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-REACH, -REACH, -REACH), pos.add(REACH, REACH, REACH))) {
            if (!(world.getBlockEntity(candidate) instanceof JarBlockEntity jar)) continue;
            JarContentsComponent contents = jar.contents();
            if (contents.total() == 0 || contents.contents().size() != 1) continue;
            if (contents.contents().getFirst().type() != type) continue;
            double distance = candidate.getSquaredDistance(pos);
            if (distance < bestDistance) { bestDistance = distance; best = jar; }
        }
        return best;
    }

    /** Essence visibly crosses the air from the jar to the core. */
    private void flyTrail(World world, BlockPos from, EssenceType type, int units) {
        if (!(world instanceof ServerWorld server)) return;
        int color = type.color();
        DustParticleEffect dust = new DustParticleEffect(
            new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), 0.8f);
        double x0 = from.getX() + 0.5, y0 = from.getY() + 1.05, z0 = from.getZ() + 0.5;
        double x1 = pos.getX() + 0.5, y1 = pos.getY() + 0.5, z1 = pos.getZ() + 0.5;
        int count = Math.min(18, 6 + units * 3);
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double arc = Math.sin(t * Math.PI) * 0.35;
            double x = x0 + (x1 - x0) * t;
            double y = y0 + (y1 - y0) * t + arc;
            double z = z0 + (z1 - z0) * t;
            server.spawnParticles(dust, x, y, z, 0, (x1 - x) * 0.3, (y1 - y) * 0.3, (z1 - z) * 0.3, 1.0);
        }
    }

    private static void celebrate(World world, BlockPos pos) {
        if (world instanceof ServerWorld server) {
            server.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.55f, 1f), 1.1f),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 40, 1.2, 1.2, 1.2, 0.08);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1f, 0.6f);
        }
    }

    /** The awakening: rings unlock, pyramids take flight, the whole cube lights up. */
    private static void wakeEffect(World world, BlockPos pos) {
        if (world instanceof ServerWorld server) {
            server.spawnParticles(new DustParticleEffect(new Vector3f(0.9f, 0.7f, 1f), 1.2f),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 60, 1.6, 1.6, 1.6, 0.12);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.9f, 1.2f);
    }

    /** Switches the glow of the whole shell, so a working Matrix lights up as one machine. */
    private static void setActive(World world, BlockPos pos, MatrixCoreBlockEntity self, boolean active) {
        if (self.active == active) return;
        self.active = active;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos target = pos.add(dx, dy, dz);
                    glow(world, target, world.getBlockState(target), active);
                }
            }
        }
        self.markDirty();
    }

    private static void glow(World world, BlockPos target, BlockState state, boolean value) {
        if (state.getBlock() instanceof MatrixCoreBlock) {
            if (state.get(MatrixCoreBlock.ACTIVE) != value)
                world.setBlockState(target, state.with(MatrixCoreBlock.ACTIVE, value), Block.NOTIFY_ALL);
        } else if (state.getBlock() instanceof MatrixPillarBlock) {
            if (state.get(MatrixPillarBlock.ACTIVE) != value)
                world.setBlockState(target, state.with(MatrixPillarBlock.ACTIVE, value), Block.NOTIFY_ALL);
        } else if (state.getBlock() instanceof AlchemicalBrickBlock) {
            if (state.get(AlchemicalBrickBlock.ACTIVE) != value)
                world.setBlockState(target, state.with(AlchemicalBrickBlock.ACTIVE, value), Block.NOTIFY_ALL);
        }
    }

    private boolean active;

    public boolean active() { return active; }

    // --- inventory --------------------------------------------------------------------------
    @Override public int size() { return 2; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return stacks.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            if (slot != OUTPUT) progress = 0;
            markDirty();
        }
        return removed;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) {
            if (slot != OUTPUT) progress = 0;
            markDirty();
        }
        return removed;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        if (slot != OUTPUT) progress = 0;
        markDirty();
    }
    @Override public void clear() { stacks.clear(); progress = 0; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) { return slot != OUTPUT; }

    @Override public Text getDisplayName() { return Text.translatable("screen.alkahest.matrix.title"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new MatrixScreenHandler(syncId, playerInventory, this, properties);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, stacks, registries);
        nbt.putInt("Progress", progress);
        nbt.putBoolean("Awake", awake);
        nbt.putBoolean("Active", active);
        nbt.put("Buffer", JarContentsComponent.CODEC.encodeStart(NbtOps.INSTANCE, buffer)
            .result().orElseGet(NbtCompound::new));
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, stacks, registries);
        progress = nbt.getInt("Progress");
        awake = nbt.getBoolean("Awake");
        active = nbt.getBoolean("Active");
        buffer = JarContentsComponent.CODEC.parse(NbtOps.INSTANCE, nbt.get("Buffer"))
            .result().orElse(JarContentsComponent.EMPTY);
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    public void dropContents(World world, BlockPos pos) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        stacks.clear();
        progress = 0;
    }

    /** Finds the built Matrix a clicked shell block belongs to and opens its interface. */
    public static boolean openNearby(World world, BlockPos clicked, PlayerEntity player) {
        if (world.isClient) return true;
        for (BlockPos candidate : BlockPos.iterate(clicked.add(-2, -2, -2), clicked.add(2, 2, 2))) {
            if (!(world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core) || !core.formed()) continue;
            player.openHandledScreen(core);
            return true;
        }
        player.sendMessage(Text.translatable("message.alkahest.matrix_unformed"), true);
        return false;
    }

    /** Wakes or sleeps the built Matrix next to a clicked shell block. */
    public static boolean toggleAwakeNearby(World world, BlockPos clicked, PlayerEntity player) {
        if (world.isClient) return true;
        for (BlockPos candidate : BlockPos.iterate(clicked.add(-2, -2, -2), clicked.add(2, 2, 2))) {
            if (!(world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core) || !core.formed()) continue;
            boolean wake = !core.awake();
            core.setAwake(world, candidate, wake);
            player.sendMessage(Text.translatable(wake ? "message.alkahest.matrix_awake"
                : "message.alkahest.matrix_asleep"), true);
            return true;
        }
        player.sendMessage(Text.translatable("message.alkahest.matrix_unformed"), true);
        return false;
    }

    public static boolean nearbyCore(World world, BlockPos clicked) {
        for (BlockPos candidate : BlockPos.iterate(clicked.add(-2, -2, -2), clicked.add(2, 2, 2))) {
            if (world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core && core.formed()) return true;
        }
        return false;
    }
}
