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
        long parent = PIPE | JAR | AMULET;
        assertEquals(parent, resolve(parent, TRANSMUTATOR, 0));
        assertEquals(0, resolve(0, 0, TRANSMUTATOR));
        assertEquals(parent | TRANSMUTATOR, resolve(parent, 0, TRANSMUTATOR));
        assertEquals(MATRIX | ALKAHEST, resolve(ALKAHEST, 0, MATRIX));
        assertEquals(ALKAHEST, resolve(ALKAHEST, AMULET, 0));
        assertEquals(MATRIX | ALKAHEST, resolve(ALKAHEST, AMULET, MATRIX));
        assertEquals(MATRIX | AMULET | ALKAHEST | NECKLACE_BLANK | CAULDRON,
            resolve(ALKAHEST | CAULDRON, AMULET | NECKLACE_BLANK, MATRIX));
        assertEquals(MATRIX | AMULET | ALKAHEST, resolve(AMULET | ALKAHEST, 0, MATRIX));
        assertEquals(MATRIX | AMULET | ALKAHEST, resolve(AMULET | ALKAHEST, FROST, MATRIX));
        assertEquals(WATER | DISTILLERY | FROST, resolve(WATER | DISTILLERY, FROST, 0));
        assertFalse(visible(CHAMBER)); assertFalse(visible(PEDESTAL));
        assertTrue(visible(MATRIX)); assertTrue(visible(SEPARATOR));
    }

    @Test void legacyCompletedOnlyAndHighBits() {
        long old = (1L << 4) | (1L << 16) | (1L << 32) | (1L << 36) | (1L << 33);
        assertEquals(CAULDRON | MONOCLE | ALKAHEST | STABILIZER, migrate(old));
        assertEquals(0, migrate(1L << 33)); // previously discovered is not previously completed
    }

    @Test void newReagentsUseHighBitsAndKeepOldCompletions() {
        long old = (1L << 31) - 1;
        long additions = HERBAL_PASTE | FIRE_DUST | CAUSTIC_SLIME;
        assertEquals(old, resolve(old, 0, 0));
        assertEquals(0, old & additions);
        assertEquals(0, resolve(0, additions, 0));
        assertEquals(WATER | additions, resolve(WATER, additions, 0));
        assertEquals(old | additions, resolve(old, additions, 0));
        assertEquals(33, Long.numberOfTrailingZeros(CAUSTIC_SLIME));
    }

    @Test void ingredientsLeadToTheirProductsWithoutRelockingCompletedNodes() {
        assertEquals(CAUSTIC_SLIME, forItem("alkahest:acid_bomb").parents());
        assertEquals(FIRE_DUST, forItem("alkahest:napalm").parents());
        assertEquals(WATER | DISTILLERY, forItem("alkahest:midas_elixir").parents());
        assertEquals(WATER | DISTILLERY, forItem("alkahest:purifying_flask").parents());
        assertEquals(ALKAHEST | DISTILLERY, forItem("alkahest:distillery_tier2").parents());
        long early = ACID | NAPALM | FIRE_DUST | CAUSTIC_SLIME;
        assertEquals(WATER, resolve(WATER, ACID | NAPALM, 0));
        assertEquals(WATER | early, resolve(WATER, early, 0), "Later-index parents settle earlier-index children");
        assertEquals(ACID | NAPALM, resolve(ACID | NAPALM, 0, 0), "Old completions do not relock");
    }

    @Test void groupedWandsAndLegacyAerMigration() {
        for (String name : new String[]{"ignis", "terra", "aer", "ordo", "perditio"})
            assertEquals(IGNIS_WAND, forItem("alkahest:crystal_wand_" + name).bit());
        assertEquals(AQUA_WAND, forItem("alkahest:crystal_wand_aqua").bit());
        assertEquals(0, resolve(0, AER_WAND, 0));
        assertEquals(MONOCLE | IGNIS_WAND | AER_WAND, resolve(MONOCLE, AER_WAND, 0));
        assertEquals(AER_WAND | IGNIS_WAND, resolve(AER_WAND, 0, 0));
        assertFalse(visible(AER_WAND));
        assertEquals(WATER | DISTILLERY, forItem("alkahest:water_faucet").parents());
    }

    @Test void transmutationResearchRemembersEarlyVanillaItemsAndKeepsOldProgress() {
        assertEquals(TRANSMUTATION_GOLD, forItem("minecraft:gold_ingot").bit());
        assertEquals(TRANSMUTATION_DIAMOND, forItem("minecraft:diamond").bit());
        assertEquals(TRANSMUTATOR, forItem("minecraft:gold_ingot").parents());
        assertEquals(TRANSMUTATION_GOLD, forItem("minecraft:diamond").parents());
        long observations = TRANSMUTATION_GOLD | TRANSMUTATION_DIAMOND;
        assertEquals(0, resolve(0, observations, 0));
        assertEquals(TRANSMUTATOR | observations, resolve(TRANSMUTATOR, observations, 0));
        long old = (1L << 36) - 1;
        assertEquals(old, resolve(old, 0, 0));
        assertEquals(TRANSMUTATION_DIAMOND, resolve(TRANSMUTATION_DIAMOND, 0, 0));
    }

    @Test void graphIsAcyclicAndIdsAreUnique() {
        var ids = new HashSet<String>();
        var items = new HashSet<String>();
        long all = 0;
        for (Step step : STEPS) {
            assertTrue(ids.add(step.id()));
            assertTrue(items.add(step.itemId()));
            assertEquals(0, step.bit() & all);
            assertEquals(1, Long.bitCount(step.bit()));
            assertEquals(0, step.parents() & ~ALL);
            if (step.bit() != AER_WAND) assertSame(step, forItem(step.itemId()));
            all |= step.bit();
        }
        assertEquals(ALL, all);
        // Stable bit/index order is not topological; prove acyclicity independently of order.
        long reachable = 0;
        for (int pass = 0; pass < STEPS.size(); pass++) {
            for (Step step : STEPS) if ((step.parents() & reachable) == step.parents()) reachable |= step.bit();
        }
        assertEquals(ALL, reachable, "Every node must have a path from a root, without cycles");
        assertNull(forItem("minecraft:stone"));
    }
}
