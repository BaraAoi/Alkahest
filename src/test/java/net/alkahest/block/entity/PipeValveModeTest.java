package net.alkahest.block.entity;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipeValveModeTest {
    @Test void redstoneTruthTableAndCycle() {
        assertTrue(PipeValveMode.OPEN.allows(false));
        assertTrue(PipeValveMode.OPEN.allows(true));
        assertTrue(PipeValveMode.CLOSE_WHEN_POWERED.allows(false));
        assertFalse(PipeValveMode.CLOSE_WHEN_POWERED.allows(true));
        assertFalse(PipeValveMode.OPEN_WHEN_POWERED.allows(false));
        assertTrue(PipeValveMode.OPEN_WHEN_POWERED.allows(true));
        assertFalse(PipeValveMode.CLOSED.allows(false));
        assertFalse(PipeValveMode.CLOSED.allows(true));
        var mode = PipeValveMode.OPEN;
        for (int i = 0; i < 4; i++) mode = mode.next();
        assertEquals(PipeValveMode.OPEN, mode);
    }

    @Test void itemCodecAndLegacyNbtIdentifiersAreStable() {
        for (PipeValveMode mode : PipeValveMode.values()) {
            var encoded = PipeValveMode.CODEC.encodeStart(JsonOps.INSTANCE, mode).getOrThrow();
            assertEquals(mode.asString(), encoded.getAsString());
            assertEquals(mode, PipeValveMode.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
            assertEquals(mode, PipeValveMode.fromId(mode.asString()));
        }
        assertEquals(PipeValveMode.OPEN, PipeValveMode.fromId(""));
        assertEquals(PipeValveMode.OPEN, PipeValveMode.fromId("invalid"));
    }
}
