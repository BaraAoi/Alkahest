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

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-dimension saved excess. Environmental baselines are disposable, loaded-chunk-only caches. */
public final class AtmosphereState extends PersistentState {
    private static final Type<AtmosphereState> TYPE = new Type<>(AtmosphereState::new, AtmosphereState::read, null);
    private static final int MAX_CELLS = 4096, CACHE_SIZE = 512;
    private static final double MAX_STORED = 1_000_000_000_000.0;
    static final long SPREAD_INTERVAL = 400;
    static final int WORK_PER_TICK = 16;
    private static final double SPREAD_DIVISOR = 256.0, MIN_TRANSFER = 0.000001;
    private static final long BASELINE_TICKS = 400;
    private final Map<Long, Cell> cells = new HashMap<>();
    private final ArrayDeque<Long> work = new ArrayDeque<>();
    private boolean needsMigration;
    // Bound expensive scans across all active machines and players in this dimension.
    private long lastScanTick = Long.MIN_VALUE;
    private final Map<Long, Baseline> baselines = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Baseline> e) { return size() > CACHE_SIZE; }
    };
    private static final class Cell {
        long time;
        boolean legacyDecay;
        final EnumMap<EssenceType, Double> excess = new EnumMap<>(EssenceType.class);
        Cell(long time) { this.time = time; }
    }
    private record Baseline(long time, Map<EssenceType, Double> values) { }

    public static AtmosphereState of(ServerWorld world) {
        AtmosphereState state = world.getPersistentStateManager().getOrCreate(TYPE, "alkahest_atmosphere");
        if (state.needsMigration) {
            // Freeze all legacy cells at their old visible value when this dimension first starts.
            for (Cell cell : state.cells.values()) state.prepare(cell, world.getTime());
            state.needsMigration = false;
        }
        return state;
    }
    public static BlockPos cellOrigin(BlockPos pos) {
        return new BlockPos(Math.floorDiv(pos.getX(), 16) * 16, Math.floorDiv(pos.getY(), 16) * 16,
            Math.floorDiv(pos.getZ(), 16) * 16);
    }
    /** Preserve the exact previously observable amount when importing old lazy-decay saves. */
    private void prepare(Cell cell, long now) {
        if (cell.legacyDecay) {
            long elapsed = now >= cell.time ? now - cell.time : 0;
            for (EssenceType type : AtmosphereMath.PRIMALS) {
                double value = AtmosphereMath.legacyDecay(cell.excess.getOrDefault(type, 0.0), elapsed);
                if (value == 0) cell.excess.remove(type); else cell.excess.put(type, value);
            }
            cell.legacyDecay = false;
            cell.time = now;
            markDirty();
        } else if (now < cell.time) { // time was moved backwards
            cell.time = now;
            markDirty();
        }
    }
    private Cell obtain(long key, long now) {
        Cell cell = cells.get(key);
        if (cell != null) { prepare(cell, now); return cell; }
        // Never evict existing atmosphere to make room; stop accepting new cells at the cap.
        if (cells.size() >= MAX_CELLS) return null;
        cell = new Cell(now); cells.put(key, cell); work.addLast(key); return cell;
    }
    /** Process a bounded round-robin slice; never load a chunk for atmosphere simulation. */
    public void tick(ServerWorld world) {
        long now = world.getTime();
        int checks = Math.min(WORK_PER_TICK, work.size());
        for (int i = 0; i < checks; i++) {
            long key = work.removeFirst();
            Cell cell = cells.get(key);
            if (cell == null) continue;
            work.addLast(key);
            diffuseOne(world, BlockPos.fromLong(key), now);
        }
    }
    /** Each face transfers 1/256 of the excess gradient per 20 loaded seconds. */
    void diffuseOne(ServerWorld world, BlockPos pos, long now) {
        BlockPos origin = cellOrigin(pos); Cell source = cells.get(origin.asLong());
        if (source == null || !world.isChunkLoaded(origin)) return;
        prepare(source, now);
        if (now - source.time < SPREAD_INTERVAL) return;
        source.time = now;
        for (EssenceType type : AtmosphereMath.PRIMALS) {
            double initial = source.excess.getOrDefault(type, 0.0), remaining = initial;
            if (initial <= 0) continue;
            for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
                BlockPos dest = origin.offset(direction, 16);
                if (dest.getY() < world.getBottomY() || dest.getY() >= world.getTopY()
                        || Math.abs((long) dest.getX()) > 30000000 || Math.abs((long) dest.getZ()) > 30000000
                        || !world.isChunkLoaded(dest)) continue;
                Cell target = cells.get(dest.asLong());
                if (target != null) prepare(target, now);
                double old = target == null ? 0 : target.excess.getOrDefault(type, 0.0);
                double moved = Math.min(remaining, Math.min((initial - old) / SPREAD_DIVISOR, MAX_STORED - old));
                if (moved < MIN_TRANSFER) continue; // retain tiny amounts rather than deleting them
                if (target == null) target = obtain(dest.asLong(), now);
                if (target == null) continue; // leave the amount in the source if the cell budget is full
                target.excess.put(type, old + moved);
                remaining -= moved;
            }
            if (remaining <= 0) source.excess.remove(type); else source.excess.put(type, remaining);
        }
        markDirty();
    }
    /** Add pollution only after actual operation/overflow succeeds; never forces a chunk to load. */
    public void add(ServerWorld world, BlockPos pos, EssenceType type, double amount) {
        var parts = new EnumMap<EssenceType, Double>(EssenceType.class);
        AtmosphereMath.split(type, amount, parts);
        if (parts.isEmpty() || !world.isChunkLoaded(pos)) return;
        Cell cell = obtain(cellOrigin(pos).asLong(), world.getTime());
        if (cell == null) return;
        parts.forEach((key, value) -> cell.excess.merge(key, Math.min(MAX_STORED, value),
            (a, b) -> Math.min(MAX_STORED, a + b)));
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
        if (cell != null) prepare(cell, world.getTime());
        for (EssenceType type : AtmosphereMath.PRIMALS)
            result.put(type, Math.min(AtmosphereMath.MAX_LEVEL, baseline.getOrDefault(type, 10.0)
                + (cell == null ? 0 : cell.excess.getOrDefault(type, 0.0))));
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
        if (cell == null || type == null || !Double.isFinite(amount) || amount <= 0) return;
        prepare(cell, world.getTime());
        double rest = Math.max(0, cell.excess.getOrDefault(type, 0.0) - amount);
        if (rest <= 0) cell.excess.remove(type); else cell.excess.put(type, rest);
        if (cell.excess.isEmpty()) {
            long key = cellOrigin(pos).asLong(); cells.remove(key); work.remove(key);
        }
        markDirty();
    }
    public static AtmosphereState read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        AtmosphereState state = new AtmosphereState();
        boolean oldFormat = nbt.getInt("Format") < 2;
        NbtList list = nbt.getList("Cells", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size() && state.cells.size() < MAX_CELLS; i++) {
            NbtCompound entry = list.getCompound(i); Cell cell = new Cell(entry.getLong("Time"));
            cell.legacyDecay = oldFormat || entry.getBoolean("LegacyDecay");
            state.needsMigration |= cell.legacyDecay;
            for (EssenceType type : AtmosphereMath.PRIMALS) {
                double value = entry.getDouble(type.asString());
                if (Double.isFinite(value) && value > 0) cell.excess.put(type, Math.min(MAX_STORED, value));
            }
            long key = entry.getLong("Pos");
            if (!cell.excess.isEmpty() && !state.cells.containsKey(key)) {
                state.cells.put(key, cell); state.work.addLast(key);
            }
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (var pair : cells.entrySet()) {
            if (pair.getValue().excess.isEmpty()) continue;
            NbtCompound entry = new NbtCompound(); entry.putLong("Pos", pair.getKey()); entry.putLong("Time", pair.getValue().time);
            if (pair.getValue().legacyDecay) entry.putBoolean("LegacyDecay", true);
            pair.getValue().excess.forEach((type, amount) -> entry.putDouble(type.asString(), amount));
            list.add(entry);
        }
        nbt.putInt("Format", 2); nbt.put("Cells", list); return nbt;
    }
}
