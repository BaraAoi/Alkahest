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
 * Centre of the 3x3x3 Matrix. One base item plus up to three extra items are synthesised into a
 * new item; the essence is pulled out of the jars around the cube through the air, so the whole
 * multiblock needs no pipes at all.
 */
public class MatrixCoreBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    public static final int DURATION = 160;
    public static final int BUFFER = 128;
    public static final int REACH = 12;
    public static final int BASE = 0, OUTPUT = 4;
    public static final int[] COMPONENTS = {1, 2, 3};

    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_NO_ESSENCE = 2,
        STATUS_BLOCKED = 3, STATUS_UNFORMED = 4, STATUS_NO_RECIPE = 5;

    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(5, ItemStack.EMPTY);
    private JarContentsComponent buffer = JarContentsComponent.EMPTY;
    private int progress;

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

    public List<ItemStack> components() {
        List<ItemStack> list = new ArrayList<>();
        for (int slot : COMPONENTS) list.add(stacks.get(slot));
        return list;
    }

    public MatrixRecipe recipe() { return MatrixRecipe.find(stacks.get(BASE), components()); }

    public int status() {
        if (!formed()) return STATUS_UNFORMED;
        MatrixRecipe recipe = recipe();
        if (recipe == null) return stacks.get(BASE).isEmpty() ? STATUS_IDLE : STATUS_NO_RECIPE;
        if (!canStoreOutput(recipe.output())) return STATUS_BLOCKED;
        if (progress >= DURATION) return STATUS_NO_ESSENCE;
        return STATUS_WORKING;
    }

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

    private boolean canStoreOutput(ItemStack product) {
        if (product.isEmpty()) return false;
        ItemStack output = stacks.get(OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.areItemsEqual(output, product)) return false;
        return output.getCount() + product.getCount() <= output.getMaxCount();
    }

    public static void tick(World world, BlockPos pos, MatrixCoreBlockEntity self) {
        if (world.isClient) return;
        MatrixRecipe recipe = self.recipe();
        if (!self.formed() || recipe == null || !self.canStoreOutput(recipe.output())) {
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
        ItemStack product = recipe.output().copy();
        ItemStack stored = self.stacks.get(OUTPUT);
        if (stored.isEmpty()) self.stacks.set(OUTPUT, product);
        else stored.increment(product.getCount());
        ItemStack base = self.stacks.get(BASE);
        base.decrement(1);
        if (base.isEmpty()) self.stacks.set(BASE, ItemStack.EMPTY);
        for (int slot : COMPONENTS) {
            ItemStack component = self.stacks.get(slot);
            if (component.isEmpty()) continue;
            // Only one copy of each component is spent, and only as many as the recipe asks.
            if (recipe.components().contains(component.getItem())) {
                component.decrement(1);
                if (component.isEmpty()) self.stacks.set(slot, ItemStack.EMPTY);
            }
        }
        self.progress = 0;
        self.markDirty();
        celebrate(world, pos);
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

    /** Switches the glow of the whole shell, so a working Matrix lights up as one machine. */
    private static void setActive(World world, BlockPos pos, MatrixCoreBlockEntity self, boolean active) {
        if (self.active == active) return;
        self.active = active;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos target = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(target);
                    glow(world, target, state, active);
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
    @Override public int size() { return 5; }
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
        nbt.putBoolean("Active", active);
        nbt.put("Buffer", JarContentsComponent.CODEC.encodeStart(NbtOps.INSTANCE, buffer)
            .result().orElseGet(NbtCompound::new));
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, stacks, registries);
        progress = nbt.getInt("Progress");
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

    /** Ticks a core next to a clicked shell block, used by the block classes. */
    public static boolean nearbyCore(World world, BlockPos clicked) {
        for (BlockPos candidate : BlockPos.iterate(clicked.add(-2, -2, -2), clicked.add(2, 2, 2))) {
            if (world.getBlockEntity(candidate) instanceof MatrixCoreBlockEntity core && core.formed()) return true;
        }
        return false;
    }

}
