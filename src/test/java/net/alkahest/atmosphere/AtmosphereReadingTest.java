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
    @Test void seamlessTransitionsPreferExactPrefetchAndKeepLastWhileWaiting() {
        var a = reading(); var view = new AtmosphereViewState();
        BlockPos here = new BlockPos(-1,65,-17), east = new BlockPos(0,65,-17), further = new BlockPos(16,65,-17);
        var b = AtmosphereReading.create(a.dimension(), east, Map.of(IGNIS, 70.0), Map.of());
        assertTrue(view.receive(a.encode()));
        assertEquals(a, view.forPosition(a.dimension(), here).orElseThrow());
        assertTrue(view.receive(b.encode())); // Prefetch cannot replace the displayed cell.
        assertEquals(a, view.forPosition(a.dimension(), here).orElseThrow());
        assertEquals(b, view.forPosition(a.dimension(), east).orElseThrow());
        assertEquals(b, view.forPosition(a.dimension(), further).orElseThrow()); // temporary fallback
        assertFalse(view.receive("not a payload"));
        assertEquals(b, view.forPosition(a.dimension(), further).orElseThrow());
        assertTrue(view.forPosition("minecraft:the_nether", further).isEmpty());
        assertTrue(view.forPosition(a.dimension(), further).isEmpty()); // no cross-dimension fallback
        assertEquals(b, view.forPosition(a.dimension(), east).orElseThrow());
        for (int i = 0; i < AtmosphereViewState.TIMEOUT_TICKS; i++) view.tick();
        assertEquals(b, view.forPosition(a.dimension(), further).orElseThrow());
        view.tick(); assertTrue(view.forPosition(a.dimension(), further).isEmpty());
        assertTrue(view.forPosition(a.dimension(), east).isEmpty());
        view.receive(a.encode()); view.clear();
        assertTrue(view.forPosition(a.dimension(), here).isEmpty());
    }
    @Test void prefetchCoversAllAdjacentCellsIncludingNegativeCorners() {
        UUID id = UUID.fromString("37d06e73-ec61-4193-b21c-764f12e20ff4");
        var seen = new java.util.HashSet<BlockPos>();
        BlockPos pos = new BlockPos(-1,65,-17), origin = AtmosphereState.cellOrigin(pos);
        for (int t = 0; t < 52; t++) {
            int index = AtmosphereEvents.prefetchIndex(t, id);
            if (index < 0) continue;
            BlockPos target = AtmosphereEvents.prefetchPosition(pos, index);
            assertNotEquals(origin, target);
            assertTrue(Math.abs(target.getX() - origin.getX()) <= 16);
            assertTrue(Math.abs(target.getY() - origin.getY()) <= 16);
            assertTrue(Math.abs(target.getZ() - origin.getZ()) <= 16);
            assertTrue(seen.add(target));
        }
        assertEquals(26, seen.size());
        assertTrue(seen.contains(origin.add(-16, -16, -16)));
        assertTrue(seen.contains(origin.add(16, 16, 16)));
        assertThrows(IllegalArgumentException.class, () -> AtmosphereEvents.prefetchPosition(pos, 26));
    }
    @Test void cacheHasBoundedCapacity() {
        var view = new AtmosphereViewState(); var dimension = "minecraft:overworld";
        for (int i = 0; i < 65; i++) {
            var r = AtmosphereReading.create(dimension, new BlockPos(i * 16, 64, 0), Map.of(IGNIS, (double) i), Map.of());
            assertTrue(view.receive(r.encode()));
        }
        assertTrue(view.forPosition(dimension, new BlockPos(0,64,0)).isEmpty());
        assertTrue(view.forPosition(dimension, new BlockPos(64*16,64,0)).isPresent());
    }
    @Test void telemetryNeedsBothEquipmentAndReceiverAndIsThrottled() {
        UUID id = UUID.fromString("37d06e73-ec61-4193-b21c-764f12e20ff4");
        long n = java.util.stream.LongStream.range(0, 400)
            .filter(t -> AtmosphereEvents.telemetryDue(t, id, true, true)).count();
        assertEquals(20, n);
        for (long t = 0; t < 40; t++) {
            assertFalse(AtmosphereEvents.telemetryDue(t, id, false, true));
            assertFalse(AtmosphereEvents.telemetryDue(t, id, true, false));
        }
    }
}
