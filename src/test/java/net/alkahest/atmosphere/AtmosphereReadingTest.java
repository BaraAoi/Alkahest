package net.alkahest.atmosphere;

import net.alkahest.essence.EssenceType;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static net.alkahest.essence.EssenceType.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereReadingTest {
    private AtmosphereReading reading() {
        Map<EssenceType, Double> levels = Map.of(IGNIS, 40.0, AQUA, 50.0);
        return AtmosphereReading.create("minecraft:overworld", new BlockPos(-1, 65, -17), levels, Map.of());
    }
    @Test void serializesExactValuesAndCorrectNegativeCell() {
        var a = reading();
        assertEquals(-16, a.x()); assertEquals(64, a.y()); assertEquals(-32, a.z());
        assertEquals(a, AtmosphereReading.decode(a.encode()).orElseThrow());
        assertTrue(a.matches("minecraft:overworld", new BlockPos(-16, 79, -32)));
        assertFalse(a.matches("minecraft:overworld", new BlockPos(0, 65, -17)));
        assertFalse(a.matches("minecraft:the_nether", new BlockPos(-1, 65, -17)));
    }
    @Test void malformedOrHostilePayloadsAreNeverDisplayed() {
        var wire = reading().encode();
        assertTrue(AtmosphereReading.decode(null).isEmpty());
        assertTrue(AtmosphereReading.decode("x".repeat(513)).isEmpty());
        assertTrue(AtmosphereReading.decode(wire + "|extra").isEmpty());
        assertTrue(AtmosphereReading.decode(wire.replace("40.0", "NaN")).isEmpty());
        assertTrue(AtmosphereReading.decode(wire.replace("40.0", "101")).isEmpty());
        assertTrue(AtmosphereReading.decode(wire.replace("|-16|", "|-15|")).isEmpty());
        assertTrue(AtmosphereReading.decode(wire.replace("minecraft:overworld", "invalid id")).isEmpty());
    }
    @Test void clientViewNeverUsesStaleOrOtherDimensionOrCellData() {
        var a = reading(); var view = new AtmosphereViewState();
        assertTrue(view.receive(a.encode()));
        assertTrue(view.forPosition(a.dimension(), new BlockPos(-1,65,-17)).isPresent());
        assertTrue(view.forPosition("minecraft:the_nether", new BlockPos(-1,65,-17)).isEmpty());
        assertTrue(view.forPosition(a.dimension(), new BlockPos(0,65,-17)).isEmpty());
        for (int i = 0; i < AtmosphereViewState.TIMEOUT_TICKS; i++) view.tick();
        assertTrue(view.forPosition(a.dimension(), new BlockPos(-1,65,-17)).isPresent());
        view.tick(); assertTrue(view.forPosition(a.dimension(), new BlockPos(-1,65,-17)).isEmpty());
        assertTrue(view.receive(a.encode()));
        assertFalse(view.receive("not a payload"));
        assertTrue(view.forPosition(a.dimension(), new BlockPos(-1,65,-17)).isEmpty());
        view.receive(a.encode()); view.clear();
        assertTrue(view.forPosition(a.dimension(), new BlockPos(-1,65,-17)).isEmpty());
    }
    @Test void telemetryNeedsBothEquipmentAndReceiverAndIsThrottled() {
        UUID id = UUID.fromString("37d06e73-ec61-4193-b21c-764f12e20ff4");
        long n = java.util.stream.LongStream.range(0, 400)
            .filter(t -> AtmosphereEvents.telemetryDue(t, id, true, true)).count();
        assertEquals(10, n);
        for (long t = 0; t < 40; t++) {
            assertFalse(AtmosphereEvents.telemetryDue(t, id, false, true));
            assertFalse(AtmosphereEvents.telemetryDue(t, id, true, false));
        }
    }
}
