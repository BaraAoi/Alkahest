package net.alkahest.shield;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShieldImpactEffectTest {
    @Test void onlyActualAbsorptionProducesAnImpact() {
        assertFalse(ShieldImpactEffect.shouldTrigger(0));
        assertFalse(ShieldImpactEffect.shouldTrigger(-1));
        assertFalse(ShieldImpactEffect.shouldTrigger(Float.NaN));
        assertTrue(ShieldImpactEffect.shouldTrigger(0.1f));
        assertTrue(ShieldImpactEffect.shouldTrigger(6));
    }
    @Test void fadesOutAfterFourteenTicks() {
        assertEquals(1f, ShieldImpactEffect.opacity(0));
        assertTrue(ShieldImpactEffect.opacity(3) > ShieldImpactEffect.opacity(8));
        assertTrue(ShieldImpactEffect.opacity(13) > 0);
        assertEquals(0f, ShieldImpactEffect.opacity(14));
        assertEquals(0f, ShieldImpactEffect.opacity(100));
        assertEquals(0f, ShieldImpactEffect.opacity(-1));
    }
}
