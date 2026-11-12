package net.alkahest.atmosphere;

import net.minecraft.util.math.BlockPos;
import java.util.Optional;

/** Independent client cache; never shares the server PersistentState in integrated worlds. */
public final class AtmosphereViewState {
    public static final int TIMEOUT_TICKS = 100;
    private AtmosphereReading reading;
    private int age;
    public boolean receive(String data) {
        Optional<AtmosphereReading> decoded = AtmosphereReading.decode(data);
        if (decoded.isEmpty()) { clear(); return false; }
        reading = decoded.get(); age = 0; return true;
    }
    public void tick() { if (reading != null && ++age > TIMEOUT_TICKS) clear(); }
    public Optional<AtmosphereReading> forPosition(String dimension, BlockPos pos) {
        return reading != null && reading.matches(dimension, pos) ? Optional.of(reading) : Optional.empty();
    }
    public void clear() { reading = null; age = 0; }
}
