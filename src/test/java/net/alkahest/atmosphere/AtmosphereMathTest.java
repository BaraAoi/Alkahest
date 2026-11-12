package net.alkahest.atmosphere;

import net.alkahest.essence.EssenceType;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import static net.alkahest.essence.EssenceType.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereMathTest {
    @Test void compositesSplitWithoutCreatingOrLosingAmount() {
        var parts = new EnumMap<EssenceType, Double>(EssenceType.class);
        AtmosphereMath.split(VAPOR, 8, parts);
        assertEquals(4, parts.get(AQUA), 1e-9);
        assertEquals(4, parts.get(IGNIS), 1e-9);
        parts.clear(); AtmosphereMath.split(SPIRITUS, 8, parts);
        assertEquals(4, parts.get(ORDO), 1e-9);
        assertEquals(2, parts.get(PERDITIO), 1e-9);
        assertEquals(2, parts.get(AQUA), 1e-9);
        assertEquals(8, parts.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        AtmosphereMath.split(VACUUM, 100, parts);
        assertEquals(8, parts.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }
    @Test void oldSaveConversionUsesPreviousHalfLife() {
        assertEquals(40, AtmosphereMath.legacyDecay(80, 12000), 1e-9);
        assertEquals(20, AtmosphereMath.legacyDecay(80, 24000), 1e-9);
        assertEquals(80, AtmosphereMath.legacyDecay(1000, 0), 1e-9);
        assertEquals(0, AtmosphereMath.legacyDecay(Double.NaN, 100), 1e-9);
        assertEquals(10, AtmosphereMath.legacyDecay(10, -1), 1e-9);
    }
    @Test void stabilityIsRecipeSpecificAndNeverGatesCrafts() {
        var levels = Map.of(IGNIS, 90.0, AQUA, 10.0);
        assertEquals(50, AtmosphereMath.stability(Map.of(IGNIS, 10), levels));
        assertEquals(100, AtmosphereMath.stability(Map.of(AQUA, 10), levels));
        assertEquals(70, AtmosphereMath.stability(Map.of(VAPOR, 10), levels));
        assertEquals(100, AtmosphereMath.stability(Map.of(), levels));
    }
    @Test void spawnChanceMonotonicallyIncreasesAndIsCapped() {
        assertEquals(0, AtmosphereMath.spawnChance(Map.of(IGNIS, 25.0)), 1e-9);
        assertEquals(.015, AtmosphereMath.spawnChance(Map.of(IGNIS, 40.0)), 1e-9);
        assertEquals(.12, AtmosphereMath.spawnChance(Map.of(IGNIS, 100.0, AQUA, 100.0)), 1e-9);
        assertEquals(.075, AtmosphereMath.spawnChance(Map.of(IGNIS, 100.0)), 1e-9);
    }
}
