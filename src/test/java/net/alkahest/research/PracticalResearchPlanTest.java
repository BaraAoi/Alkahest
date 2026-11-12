package net.alkahest.research;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static net.alkahest.research.PracticalResearchPlan.*;

class PracticalResearchPlanTest {
    @Test void branchParentsAndEarlyObservations() {
        long observed = CAULDRON | MONOCLE | WATER;
        long completed = resolve(0, observed, 0);
        assertEquals(CAULDRON | WATER, completed);
        completed = resolve(completed, observed | DISTILLERY, 0);
        assertEquals(CAULDRON | WATER | DISTILLERY | MONOCLE, completed);
        assertEquals(completed, resolve(completed, 0, 0));
        assertEquals(ALL, resolve(0, ALL, ASSEMBLIES));
    }

    @Test void newStorageAndStabilizerBranchesDoNotRelockOldProgress() {
        long old = (1L << 26) - 1;
        assertEquals(old, resolve(old, 0, 0));
        long observed = JAR | STABILIZER_RECEIVER | STABILIZER_CREST;
        assertEquals(0, resolve(0, observed, 0));
        assertEquals(DISTILLERY | JAR, resolve(DISTILLERY, observed, 0));
        long completed = resolve(STABILIZER | DISTILLERY, observed, 0);
        assertEquals(STABILIZER | DISTILLERY | observed, completed);
        assertEquals(STABILIZER, STEPS.get(27).parents());
        assertEquals(STABILIZER, STEPS.get(28).parents());
        assertNotEquals(STABILIZER_RECEIVER, STEPS.get(28).parents());
    }

    @Test void assembledActionsWaitForParentsAndAreSeparateFromItemOwnership() {
        long parent = PIPE | CHARGER;
        assertEquals(parent, resolve(parent, TRANSMUTATOR, 0));
        assertEquals(0, resolve(0, 0, TRANSMUTATOR));
        assertEquals(parent | TRANSMUTATOR, resolve(parent, 0, TRANSMUTATOR));
        assertEquals(MATRIX | AMULET | TRANSMUTATOR, resolve(AMULET | TRANSMUTATOR, 0, MATRIX));
        assertEquals(MATRIX | AMULET | TRANSMUTATOR | FROST, resolve(AMULET | TRANSMUTATOR, FROST, MATRIX));
        assertFalse(visible(CHAMBER)); assertFalse(visible(PEDESTAL));
        assertTrue(visible(MATRIX)); assertTrue(visible(SEPARATOR));
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
