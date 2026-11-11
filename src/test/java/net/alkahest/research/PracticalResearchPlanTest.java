package net.alkahest.research;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static net.alkahest.research.PracticalResearchPlan.*;

class PracticalResearchPlanTest {
    @Test void branchParentsAndEarlyObservations() {
        long observed = CAULDRON | MONOCLE | WATER;
        long completed = resolve(0, observed);
        assertEquals(CAULDRON | WATER, completed);
        completed = resolve(completed, observed | DISTILLERY);
        assertEquals(CAULDRON | WATER | DISTILLERY | MONOCLE, completed);
        assertEquals(completed, resolve(completed, 0));
        assertEquals(ALL, resolve(0, ALL));
    }

    @Test void legacyCompletedOnlyAndHighBits() {
        long old = (1L << 4) | (1L << 16) | (1L << 32) | (1L << 36) | (1L << 33);
        assertEquals(CAULDRON | MONOCLE | ALKAHEST | STABILIZER, migrate(old));
        assertEquals(0, migrate(1L << 33)); // previously discovered is not previously completed
    }

    @Test void graphIsAcyclicAndIdsAreUnique() {
        var ids = new HashSet<String>();
        var items = new HashSet<String>();
        long preceding = 0;
        for (Step step : STEPS) {
            assertTrue(ids.add(step.id()));
            assertTrue(items.add(step.itemId()));
            assertEquals(0, step.parents() & ~preceding);
            assertEquals(0, step.bit() & preceding);
            assertEquals(1, Long.bitCount(step.bit()));
            assertSame(step, forItem(step.itemId()));
            preceding |= step.bit();
        }
        assertEquals(ALL, preceding);
        assertNull(forItem("minecraft:stone"));
    }
}
