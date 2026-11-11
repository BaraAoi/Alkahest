package net.alkahest.research;

import java.util.List;

/**
 * Pure progression rules for the planned practical research tree. Observation is kept
 * separately from completion: an item obtained early is not lost if its parents are locked.
 * Persistent IDs and bit positions must never be reordered in a released world.
 */
public final class PracticalResearchPlan {
    public record Step(String id, String itemId, long bit, long parents, long legacyCompleted) { }

    public static final long CAULDRON = 1L;
    public static final long WATER = 1L << 1;
    public static final long DISTILLERY = 1L << 2;
    public static final long MONOCLE = 1L << 3;
    public static final long IGNIS_WAND = 1L << 4;
    public static final long AQUA_WAND = 1L << 5;
    public static final long BINDING_SALT = 1L << 6;
    public static final long ALKAHEST = 1L << 7;
    public static final long STABILIZER = 1L << 8;
    public static final long ALL = (1L << 9) - 1;

    /** Legacy completion bits belong to EarlyResearch's saved advancement mapping. */
    public static final List<Step> STEPS = List.of(
        new Step("cauldron", "alkahest:alchemy_cauldron", CAULDRON, 0, 1L << 4),
        new Step("water", "alkahest:purified_water", WATER, CAULDRON, 1L << 8),
        new Step("distillery", "alkahest:distillery", DISTILLERY, CAULDRON, 1L << 12),
        new Step("monocle", "alkahest:alchemists_monocle", MONOCLE, WATER | DISTILLERY, 1L << 16),
        new Step("ignis_wand", "alkahest:crystal_wand_ignis", IGNIS_WAND, MONOCLE, 1L << 20),
        new Step("aqua_wand", "alkahest:crystal_wand_aqua", AQUA_WAND, MONOCLE, 1L << 24),
        new Step("binding_salt", "alkahest:binding_salt", BINDING_SALT, IGNIS_WAND | AQUA_WAND, 1L << 28),
        new Step("alkahest", "alkahest:alkahest_vial", ALKAHEST, BINDING_SALT, 1L << 32),
        new Step("stabilizer", "alkahest:stabilizer_core", STABILIZER, ALKAHEST, 1L << 36)
    );

    public static Step forItem(String itemId) {
        for (Step step : STEPS) if (step.itemId().equals(itemId)) return step;
        return null;
    }

    public static boolean available(Step step, long completed) {
        return (completed & step.parents()) == step.parents();
    }

    /** Grants no new legacy progress: only completed, never merely discovered, old nodes migrate. */
    public static long migrate(long legacyFlags) {
        long result = 0;
        for (Step step : STEPS) if ((legacyFlags & step.legacyCompleted()) != 0) result |= step.bit();
        return result;
    }

    /** Resolves all currently observed, eligible items, including cascades through both branches. */
    public static long resolve(long completed, long observed) {
        long result = completed & ALL;
        boolean changed;
        do {
            changed = false;
            for (Step step : STEPS) {
                if ((result & step.bit()) == 0 && (observed & step.bit()) != 0 && available(step, result)) {
                    result |= step.bit();
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private PracticalResearchPlan() { }
}
