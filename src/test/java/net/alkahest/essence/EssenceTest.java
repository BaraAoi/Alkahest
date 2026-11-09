package net.alkahest.essence;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EssenceTest {
    @Test void catalogAndOpposites() {
        assertEquals(17, EssenceType.values().length);
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() == EssenceType.Tier.PRIMA) assertSame(type, type.opposite().opposite());
            if (type.tier() == EssenceType.Tier.COMPOSITA) assertEquals(2, type.parents().size());
        }
        assertEquals(List.of(EssenceType.MORTUUS, EssenceType.ORDO), EssenceType.SPIRITUS.parents());
    }

    @Test void mergeUsesWeightedStabilityAndEvaporatesOverflow() {
        var base = new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 80, 0.5f)));
        var addition = new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 40, 1.0f)));
        var merged = base.merge(addition, 100);
        assertEquals(100, merged.total());
        assertEquals(0.6f, merged.contents().getFirst().stability(), 0.0001f);
        assertEquals(80, base.total());
    }

    @Test void mergeKeepsDistinctTypes() {
        var base = new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA, 20, 0.4f)));
        var addition = new JarContentsComponent(List.of(new EssenceStack(EssenceType.TERRA, 30, 0.9f)));
        assertEquals(2, base.merge(addition, 100).contents().size());
    }

    @Test void codecRoundTripAndInvalidData() {
        var jar = new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 42, 0.9f)));
        var encoded = JarContentsComponent.CODEC.encodeStart(JsonOps.INSTANCE, jar).getOrThrow();
        assertEquals(jar, JarContentsComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var bad = JsonParser.parseString("{\"contents\":[{\"type\":\"ordo\",\"amount\":501,\"stability\":0.5}]}");
        assertTrue(JarContentsComponent.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent());
    }
}
