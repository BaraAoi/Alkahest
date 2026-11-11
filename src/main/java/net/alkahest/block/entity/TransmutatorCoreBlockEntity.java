package net.alkahest.block.entity;

import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.block.custom.TransmutatorCoreBlock;
import net.alkahest.block.custom.TransmutatorPillarBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.screen.TransmutatorScreenHandler;
import net.alkahest.transmutation.TransmutationRecipe;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core of the 3x1x3 Transmutator. It turns one item into another and pays for it with essence
 * that its eight pillars gather out of the surrounding jars — the units fly through the air as
 * coloured sparks instead of travelling through pipes.
 */
public class TransmutatorCoreBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    public static final int DURATION = 100;
    public static final int INPUT = 0, AMULET = 1, OUTPUT = 2;

    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_NO_ESSENCE = 2,
        STATUS_BLOCKED = 3, STATUS_UNFORMED = 4, STATUS_NO_AMULET = 5;

    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(3, ItemStack.EMPTY);
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

    public TransmutatorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSMUTATOR_CORE, pos, state);
    }

    public int progress() { return progress; }
    public TransmutationRecipe recipe() { return TransmutationRecipe.find(stacks.get(INPUT)); }

    public int status() {
        if (!formed()) return STATUS_UNFORMED;
        TransmutationRecipe recipe = recipe();
        if (recipe == null) return STATUS_IDLE;
        if (!hasAmulet()) return STATUS_NO_AMULET;
        if (!canStoreOutput(recipe.output())) return STATUS_BLOCKED;
        if (progress >= DURATION) return STATUS_NO_ESSENCE;
        return STATUS_WORKING;
    }

    /** All eight pillars must be present in the same horizontal plane around the core. */
    public boolean formed() {
        if (world == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!(world.getBlockState(pos.add(dx, 0, dz)).getBlock() instanceof TransmutatorPillarBlock)) return false;
            }
        }
        return true;
    }

    public List<TransmutatorPillarBlockEntity> pillars() {
        List<TransmutatorPillarBlockEntity> result = new ArrayList<>();
        if (world == null) return result;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (world.getBlockEntity(pos.add(dx, 0, dz)) instanceof TransmutatorPillarBlockEntity pillar) result.add(pillar);
            }
        }
        return result;
    }

    private boolean hasAmulet() {
        ItemStack amulet = stacks.get(AMULET);
        if (!(amulet.getItem() instanceof AlkahestAmuletItem)) return false;
        return amulet.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) > 0;
    }

    private boolean canStoreOutput(ItemStack product) {
        if (product.isEmpty()) return false;
        ItemStack output = stacks.get(OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.areItemsEqual(output, product)) return false;
        return output.getCount() + product.getCount() <= output.getMaxCount();
    }

    public static void tick(World world, BlockPos pos, TransmutatorCoreBlockEntity self) {
        if (world.isClient) return;
        TransmutationRecipe recipe = self.recipe();
        if (!self.formed() || recipe == null || !self.hasAmulet() || !self.canStoreOutput(recipe.output())) {
            if (self.progress != 0) { self.progress = 0; self.markDirty(); }
            setWorking(world, pos, false);
            return;
        }
        if (self.progress < DURATION) {
            int before = self.progress;
            self.progress = Math.min(DURATION, before + MidasTouchEffect.progressStep(
                MidasTouchEffect.nearby(world, pos), world.getTime()));
            // A boosted step can cross a multiple of ten without landing on it.
            if (before / 10 != self.progress / 10) self.prefill(world, recipe.cost());
            setWorking(world, pos, true);
            self.markDirty();
            return;
        }
        // The operation only completes when the pillars can pay the whole price; nothing is lost.
        if (!self.collect(world, recipe.cost())) {
            setWorking(world, pos, false);
            return;
        }
        ItemStack output = recipe.output().copy();
        ItemStack stored = self.stacks.get(OUTPUT);
        if (stored.isEmpty()) self.stacks.set(OUTPUT, output);
        else stored.increment(output.getCount());
        ItemStack input = self.stacks.get(INPUT);
        input.decrement(1);
        if (input.isEmpty()) self.stacks.set(INPUT, ItemStack.EMPTY);
        ItemStack amulet = self.stacks.get(AMULET);
        amulet.set(ModDataComponents.ESSENCE_CHARGE,
            Math.max(0, amulet.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) - 1));
        self.progress = 0;
        self.markDirty();
        playFinish(world, pos);
    }

    /**
     * Draws one unit of every still-missing essence out of a jar, so the jars feed the platform
     * gradually instead of everything appearing at the last moment. Nothing moves when the whole
     * price cannot be paid, which keeps short jars untouched.
     */
    private void prefill(World world, Map<EssenceType, Integer> cost) {
        List<TransmutatorPillarBlockEntity> pillars = pillars();
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            EssenceType type = entry.getKey();
            int need = entry.getValue();
            if (stored(pillars, type) + jarsAround(world, pillars, type) < need) return;
        }
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            EssenceType type = entry.getKey();
            if (stored(pillars, type) >= entry.getValue()) continue;
            for (TransmutatorPillarBlockEntity pillar : pillars) {
                if (pillar.buffer().total() >= TransmutatorPillarBlockEntity.CAPACITY) continue;
                int before = stored(pillars, type);
                pillar.request(world, type, 1);
                // One unit per pillar per pass is enough to keep a steady stream of sparks.
                if (stored(pillars, type) > before) break;
            }
        }
    }

    /** Pulls the price out of the surrounding jars through the pillars. */
    private boolean collect(World world, Map<EssenceType, Integer> cost) {
        List<TransmutatorPillarBlockEntity> pillars = pillars();
        // The entire price must be available before anything is pulled or consumed. Checking
        // and spending one type at a time destroys the earlier types on every waiting tick when
        // a later type is missing.
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            if (stored(pillars, entry.getKey()) + jarsAround(world, pillars, entry.getKey()) < entry.getValue()) return false;
        }
        // Reserve every type first. A full or unreachable pillar may still prevent a request;
        // in that case any reservation remains in its saved buffer, never partly spent.
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            EssenceType type = entry.getKey();
            for (TransmutatorPillarBlockEntity pillar : pillars) {
                int missing = entry.getValue() - stored(pillars, type);
                if (missing <= 0) break;
                pillar.request(world, type, missing);
            }
            if (stored(pillars, type) < entry.getValue()) return false;
        }
        // All costs are present in the buffers; only now commit the payment.
        for (Map.Entry<EssenceType, Integer> entry : cost.entrySet()) {
            EssenceType type = entry.getKey();
            int need = entry.getValue();
            for (TransmutatorPillarBlockEntity pillar : pillars) {
                if (need <= 0) break;
                int taken = pillar.take(type, need);
                if (taken <= 0) continue;
                need -= taken;
                sparkToCore(world, pillar.getPos(), type, taken);
            }
            if (need > 0) return false;
        }
        markDirty();
        return true;
    }

    /** Units of the type lying in the jars the platform can see, each jar counted once. */
    private static int jarsAround(World world, List<TransmutatorPillarBlockEntity> pillars, EssenceType type) {
        java.util.Map<BlockPos, Integer> seen = new java.util.HashMap<>();
        for (TransmutatorPillarBlockEntity pillar : pillars) {
            for (JarBlockEntity jar : pillar.jarsAround(world, type)) {
                seen.putIfAbsent(jar.getPos(), jar.contents().total());
            }
        }
        int total = 0;
        for (int amount : seen.values()) total += amount;
        return total;
    }

    private static int stored(List<TransmutatorPillarBlockEntity> pillars, EssenceType type) {
        int total = 0;
        for (TransmutatorPillarBlockEntity pillar : pillars) total += pillar.stored(type);
        return total;
    }

    /** Sparks travel from the pillar into the core, so the payment is visible. */
    private void sparkToCore(World world, BlockPos pillar, EssenceType type, int units) {
        if (!(world instanceof ServerWorld server)) return;
        int color = type.color();
        DustParticleEffect dust = new DustParticleEffect(
            new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), 0.7f);
        double x0 = pillar.getX() + 0.5, y0 = pillar.getY() + 1.1, z0 = pillar.getZ() + 0.5;
        double x1 = pos.getX() + 0.5, y1 = pos.getY() + 1.15, z1 = pos.getZ() + 0.5;
        int count = Math.min(10, 3 + units * 2);
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double x = x0 + (x1 - x0) * t;
            double y = y0 + (y1 - y0) * t + Math.sin(t * Math.PI) * 0.3;
            double z = z0 + (z1 - z0) * t;
            server.spawnParticles(dust, x, y, z, 0, (x1 - x) * 0.4, (y1 - y) * 0.4, (z1 - z) * 0.4, 1.0);
        }
    }

    private static void playFinish(World world, BlockPos pos) {
        if (world instanceof ServerWorld server) {
            server.spawnParticles(new DustParticleEffect(new Vector3f(1f, 0.85f, 0.35f), 1.0f),
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 26, 0.35, 0.3, 0.35, 0.05);
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 0.7f);
        }
    }

    private static void setWorking(World world, BlockPos pos, boolean working) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof TransmutatorCoreBlock && state.get(TransmutatorCoreBlock.WORKING) != working)
            world.setBlockState(pos, state.with(TransmutatorCoreBlock.WORKING, working), Block.NOTIFY_ALL);
    }

    // --- inventory --------------------------------------------------------------------------
    @Override public int size() { return 3; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return stacks.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) { if (slot != OUTPUT) progress = 0; markDirty(); }
        return removed;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) { if (slot != OUTPUT) progress = 0; markDirty(); }
        return removed;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        if (slot != OUTPUT) progress = 0;
        markDirty();
    }
    @Override public void clear() { stacks.clear(); progress = 0; markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public boolean isValid(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> TransmutationRecipe.find(stack) != null;
            case AMULET -> stack.getItem() instanceof AlkahestAmuletItem;
            default -> false;
        };
    }

    @Override public Text getDisplayName() { return Text.translatable("screen.alkahest.transmutator.title"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TransmutatorScreenHandler(syncId, playerInventory, this, properties);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, stacks, registries);
        nbt.putInt("Progress", progress);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, stacks, registries);
        progress = nbt.getInt("Progress");
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    /** Drops leftovers so breaking the core never eats items. */
    public void dropContents(World world, BlockPos pos) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        stacks.clear();
        progress = 0;
    }

    /** Only used by the screen to show what the current input needs. */
    public static List<EssenceStack> requirements(ItemStack input) {
        TransmutationRecipe recipe = TransmutationRecipe.find(input);
        if (recipe == null) return List.of();
        List<EssenceStack> list = new ArrayList<>();
        for (Map.Entry<EssenceType, Integer> entry : recipe.cost().entrySet()) {
            list.add(new EssenceStack(entry.getKey(), entry.getValue(), 1f));
        }
        return list;
    }
}
