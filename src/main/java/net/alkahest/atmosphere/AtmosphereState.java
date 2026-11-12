package net.alkahest.atmosphere;

import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-dimension saved excess. Environmental baselines are disposable, loaded-chunk-only caches. */
public final class AtmosphereState extends PersistentState {
    private static final Type<AtmosphereState> TYPE = new Type<>(AtmosphereState::new, AtmosphereState::read, null);
    private static final int MAX_CELLS = 4096, CACHE_SIZE = 512;
    private static final long BASELINE_TICKS = 400;
    private final Map<Long, Cell> cells = new HashMap<>();
    // Bound expensive scans across all active machines and players in this dimension.
    private long lastScanTick = Long.MIN_VALUE;
    private final Map<Long, Baseline> baselines = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Baseline> e) { return size() > CACHE_SIZE; }
    };
    private static final class Cell {
        long time;
        final EnumMap<EssenceType, Double> excess = new EnumMap<>(EssenceType.class);
        Cell(long time) { this.time = time; }
    }
    private record Baseline(long time, Map<EssenceType, Double> values) { }

    public static AtmosphereState of(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, "alkahest_atmosphere");
    }
    public static BlockPos cellOrigin(BlockPos pos) {
        return new BlockPos(Math.floorDiv(pos.getX(), 16) * 16, Math.floorDiv(pos.getY(), 16) * 16,
            Math.floorDiv(pos.getZ(), 16) * 16);
    }
    private void age(Cell cell, long now) {
        long elapsed = now >= cell.time ? now - cell.time : 0;
        if (elapsed > 0) for (EssenceType type : AtmosphereMath.PRIMALS) {
            double value = AtmosphereMath.decay(cell.excess.getOrDefault(type, 0.0), elapsed);
            if (value < .001) cell.excess.remove(type); else cell.excess.put(type, value);
        }
        cell.time = now;
    }
    private Cell obtain(long key, long now) {
        Cell cell = cells.get(key);
        if (cell != null) { age(cell, now); return cell; }
        if (cells.size() >= MAX_CELLS) {
            Long oldest = cells.entrySet().stream().min(java.util.Comparator.comparingLong(e -> e.getValue().time))
                .map(Map.Entry::getKey).orElse(null);
            if (oldest != null) cells.remove(oldest);
        }
        cell = new Cell(now); cells.put(key, cell); return cell;
    }
    /** Add pollution only after actual operation/overflow succeeds; never forces a chunk to load. */
    public void add(ServerWorld world, BlockPos pos, EssenceType type, double amount) {
        var parts = new EnumMap<EssenceType, Double>(EssenceType.class);
        AtmosphereMath.split(type, amount, parts);
        if (parts.isEmpty() || !world.isChunkLoaded(pos)) return;
        Cell cell = obtain(cellOrigin(pos).asLong(), world.getTime());
        parts.forEach((key, value) -> cell.excess.merge(key, Math.min(AtmosphereMath.MAX_EXCESS, value),
            (a, b) -> Math.min(AtmosphereMath.MAX_EXCESS, a + b)));
        markDirty();
    }
    public void add(ServerWorld world, BlockPos pos, Map<EssenceType, ? extends Number> amounts, double factor) {
        if (factor <= 0 || !Double.isFinite(factor)) return;
        amounts.forEach((type, amount) -> add(world, pos, type, amount.doubleValue() * factor));
    }
    public Map<EssenceType, Double> levels(ServerWorld world, BlockPos pos) {
        BlockPos origin = cellOrigin(pos);
        Map<EssenceType, Double> baseline = baseline(world, origin);
        EnumMap<EssenceType, Double> result = new EnumMap<>(EssenceType.class);
        Cell cell = cells.get(origin.asLong());
        long elapsed = cell == null || world.getTime() < cell.time ? 0 : world.getTime() - cell.time;
        for (EssenceType type : AtmosphereMath.PRIMALS)
            result.put(type, Math.min(AtmosphereMath.MAX_LEVEL, baseline.getOrDefault(type, 10.0)
                + (cell == null ? 0 : AtmosphereMath.decay(cell.excess.getOrDefault(type, 0.0), elapsed))));
        return result;
    }
    /** Whether a real block scan exists, rather than the temporary neutral fallback. */
    public boolean hasBaseline(BlockPos pos) { return baselines.containsKey(cellOrigin(pos).asLong()); }
    public Map<EssenceType, Double> baseline(ServerWorld world, BlockPos pos) {
        BlockPos origin = cellOrigin(pos); long key = origin.asLong(), now = world.getTime();
        Baseline cached = baselines.get(key);
        if (cached != null && now >= cached.time && now - cached.time < BASELINE_TICKS) return cached.values;
        var totals = new EnumMap<EssenceType, Double>(EssenceType.class);
        for (EssenceType type : AtmosphereMath.PRIMALS) totals.put(type, 10.0);
        if (!world.isChunkLoaded(origin)) return cached == null ? totals : cached.values;
        if (lastScanTick == now) return cached == null ? totals : cached.values;
        lastScanTick = now;
        var profiles = new HashMap<Block, Map<EssenceType, Double>>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            int yy = origin.getY() + y;
            if (yy < world.getBottomY() || yy >= world.getTopY()) continue;
            Block block = world.getBlockState(cursor.set(origin.getX() + x, yy, origin.getZ() + z)).getBlock();
            Map<EssenceType, Double> profile = profiles.computeIfAbsent(block, b -> profile(world, b));
            profile.forEach((type, value) -> totals.merge(type, value / 256.0, Double::sum));
        }
        Map<EssenceType, Double> immutable = Map.copyOf(totals);
        baselines.put(key, new Baseline(now, immutable));
        return immutable;
    }
    private static Map<EssenceType, Double> profile(ServerWorld world, Block block) {
        var result = new EnumMap<EssenceType, Double>(EssenceType.class);
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) result.put(EssenceType.AER, 1.0);
        else if (block == Blocks.WATER) result.put(EssenceType.AQUA, 1.0);
        else if (block == Blocks.LAVA) result.put(EssenceType.IGNIS, 1.0);
        else for (EssenceStack value : ItemEssenceMap.get(new ItemStack(block.asItem()), world))
            AtmosphereMath.split(value.type(), value.amount(), result);
        result.replaceAll((type, value) -> Math.min(1, value)); // no single valuable block dominates
        return result;
    }
    /** Spend only accumulated excess, never the environmental baseline. */
    public void spend(ServerWorld world, BlockPos pos, EssenceType type, double amount) {
        Cell cell = cells.get(cellOrigin(pos).asLong());
        if (cell == null || amount <= 0) return;
        age(cell, world.getTime());
        double rest = Math.max(0, cell.excess.getOrDefault(type, 0.0) - amount);
        if (rest < .001) cell.excess.remove(type); else cell.excess.put(type, rest);
        markDirty();
    }
    public static AtmosphereState read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        AtmosphereState state = new AtmosphereState();
        NbtList list = nbt.getList("Cells", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size() && state.cells.size() < MAX_CELLS; i++) {
            NbtCompound entry = list.getCompound(i); Cell cell = new Cell(entry.getLong("Time"));
            for (EssenceType type : AtmosphereMath.PRIMALS) {
                double value = entry.getDouble(type.asString());
                if (Double.isFinite(value) && value > 0) cell.excess.put(type, Math.min(AtmosphereMath.MAX_EXCESS, value));
            }
            if (!cell.excess.isEmpty()) state.cells.put(entry.getLong("Pos"), cell);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (var pair : cells.entrySet()) {
            if (pair.getValue().excess.isEmpty()) continue;
            NbtCompound entry = new NbtCompound(); entry.putLong("Pos", pair.getKey()); entry.putLong("Time", pair.getValue().time);
            pair.getValue().excess.forEach((type, amount) -> entry.putDouble(type.asString(), amount));
            list.add(entry);
        }
        nbt.put("Cells", list); return nbt;
    }
}
