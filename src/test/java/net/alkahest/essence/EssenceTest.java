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
    @Test void distilleryCodecAcceptsLargeQueuesWithoutChangingJarLimits() {
        var queue = new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 500, .6f),
            new EssenceStack(EssenceType.ORDO, 500, .8f)));
        var encoded = JarContentsComponent.DISTILLERY_CODEC.encodeStart(JsonOps.INSTANCE, queue).getOrThrow();
        assertEquals(queue, JarContentsComponent.DISTILLERY_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(JarContentsComponent.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent());
        var duplicate = new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 1, .6f),
            new EssenceStack(EssenceType.IGNIS, 2, .6f)));
        assertTrue(JarContentsComponent.DISTILLERY_CODEC.encodeStart(JsonOps.INSTANCE, duplicate).error().isPresent());
        var legacy = new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA, 5, .4f)));
        var oldData = JarContentsComponent.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(legacy, JarContentsComponent.DISTILLERY_CODEC.parse(JsonOps.INSTANCE, oldData).getOrThrow());
    }
    @Test void jarVisualLevelAndMixtureColor() {
        var empty = JarContentsComponent.EMPTY;
        assertEquals(0, JarVisuals.level(empty));
        assertEquals(0xffffff, JarVisuals.color(empty));
        var single = new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 1, .5f)));
        assertEquals(1, JarVisuals.level(single));
        assertEquals(EssenceType.ORDO.color(), JarVisuals.color(single));
        assertEquals(1, JarVisuals.level(new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 10, .5f)))));
        assertEquals(2, JarVisuals.level(new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 11, .5f)))));
        assertEquals(10, JarVisuals.level(new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 100, .5f)))));
        assertEquals(10, JarVisuals.level(new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 500, .5f)))));
        var mix = new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 1, .5f), new EssenceStack(EssenceType.AQUA, 3, .5f)));
        int a = EssenceType.IGNIS.color(), b = EssenceType.AQUA.color();
        int expected = ((((a >> 16 & 255) + 3 * (b >> 16 & 255)) / 4) << 16)
            | ((((a >> 8 & 255) + 3 * (b >> 8 & 255)) / 4) << 8) | (((a & 255) + 3 * (b & 255)) / 4);
        assertEquals(expected, JarVisuals.color(mix));
    }

}
