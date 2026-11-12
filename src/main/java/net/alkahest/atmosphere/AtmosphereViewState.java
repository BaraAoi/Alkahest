package net.alkahest.atmosphere;

import net.minecraft.util.math.BlockPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Bounded client-only cache. Readings are always produced by the server. */
public final class AtmosphereViewState {
    public static final int TIMEOUT_TICKS = 100;
    private static final int MAX_READINGS = 64;
    private record Key(String dimension, BlockPos origin) { }
    private record Entry(AtmosphereReading reading, long receivedAt) { }
    private final Map<Key, Entry> readings = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Entry> entry) { return size() > MAX_READINGS; }
    };
    private long clock;
    private Entry displayed;

    public boolean receive(String data) {
        Optional<AtmosphereReading> decoded = AtmosphereReading.decode(data);
        if (decoded.isEmpty()) return false;
        AtmosphereReading reading = decoded.get();
        readings.put(new Key(reading.dimension(), new BlockPos(reading.x(), reading.y(), reading.z())), new Entry(reading, clock));
        return true;
    }
    private boolean fresh(Entry entry) { return entry != null && clock - entry.receivedAt() <= TIMEOUT_TICKS; }
    public void tick() {
        clock++;
        readings.values().removeIf(entry -> !fresh(entry));
        if (!fresh(displayed)) displayed = null;
    }
    public Optional<AtmosphereReading> forPosition(String dimension, BlockPos pos) {
        Entry exact = readings.get(new Key(dimension, AtmosphereState.cellOrigin(pos)));
        if (fresh(exact)) { displayed = exact; return Optional.of(exact.reading()); }
        // Keep the last visible reading briefly when prefetch is late; never cross dimensions.
        if (fresh(displayed) && displayed.reading().dimension().equals(dimension)) return Optional.of(displayed.reading());
        displayed = null;
        return Optional.empty();
    }
    public void clear() { readings.clear(); displayed = null; clock = 0; }
}
