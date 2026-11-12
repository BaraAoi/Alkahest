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
    public static final long PIPE = 1L << 9, CHARGER = 1L << 10, TIER2 = 1L << 11, BELLOWS = 1L << 12;
    public static final long AMULET = 1L << 13, TRANSMUTATOR = 1L << 14, CHAMBER = 1L << 15, CROWN = 1L << 16;
    public static final long MATRIX = 1L << 17, PEDESTAL = 1L << 18, MIDAS = 1L << 19;
    public static final long NAPALM = 1L << 20, ACID = 1L << 21, MIASMA = 1L << 22, LIGHTNING = 1L << 23;
    public static final long FROST = 1L << 24, PURIFICATION = 1L << 25;
    public static final long JAR = 1L << 26, STABILIZER_RECEIVER = 1L << 27, STABILIZER_CREST = 1L << 28;
    public static final long SEPARATOR = 1L << 29, CRYSTALLIZATION = 1L << 30;
    public static final long HERBAL_PASTE = 1L << 31, FIRE_DUST = 1L << 32, CAUSTIC_SLIME = 1L << 33;
    public static final long AER_WAND = 1L << 34;
    public static final long ALL = (1L << 35) - 1;
    public static final long ASSEMBLIES = STABILIZER | TIER2 | TRANSMUTATOR | MATRIX;
    // Keep obsolete flags for saves, but group the component entries into their machine.
    public static final long HIDDEN_COMPONENTS = BELLOWS | CHAMBER | CROWN | PEDESTAL | STABILIZER_RECEIVER | STABILIZER_CREST;

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
        new Step("stabilizer", "alkahest:stabilizer_core", STABILIZER, ALKAHEST, 1L << 36),
        new Step("pipe", "alkahest:alchemy_pipe", PIPE, DISTILLERY, 0),
        new Step("charger", "alkahest:essence_charger", CHARGER, DISTILLERY, 0),
        new Step("tier2", "alkahest:distillery_tier2", TIER2, ALKAHEST | DISTILLERY, 0),
        new Step("bellows", "alkahest:distillery_bellows", BELLOWS, TIER2, 0),
        new Step("amulet", "alkahest:alkahest_amulet", AMULET, ALKAHEST, 0),
        new Step("transmutator", "alkahest:transmutator_core", TRANSMUTATOR, PIPE | JAR | AMULET, 0),
        new Step("chamber", "alkahest:transmutator_chamber", CHAMBER, TRANSMUTATOR, 0),
        new Step("crown", "alkahest:transmutator_crown", CROWN, TRANSMUTATOR, 0),
        new Step("matrix", "alkahest:matrix_core", MATRIX, ALKAHEST | AMULET, 0),
        new Step("pedestal", "alkahest:matrix_pedestal", PEDESTAL, MATRIX, 0),
        new Step("midas", "alkahest:midas_elixir", MIDAS, WATER | DISTILLERY, 0),
        new Step("napalm", "alkahest:napalm", NAPALM, FIRE_DUST, 0),
        new Step("acid", "alkahest:acid_bomb", ACID, CAUSTIC_SLIME, 0),
        new Step("miasma", "alkahest:miasma", MIASMA, WATER, 0),
        new Step("lightning", "alkahest:lightning_in_a_bottle", LIGHTNING, WATER, 0),
        new Step("frost", "alkahest:frost_flask", FROST, WATER | DISTILLERY, 0),
        new Step("purification", "alkahest:purifying_flask", PURIFICATION, WATER | DISTILLERY, 0),
        new Step("jar", "alkahest:jar", JAR, DISTILLERY, 0),
        new Step("stabilizer_receiver", "alkahest:stabilizer_receiver", STABILIZER_RECEIVER, STABILIZER, 0),
        new Step("stabilizer_crest", "alkahest:stabilizer_crest", STABILIZER_CREST, STABILIZER, 0),
        new Step("separator", "alkahest:essence_separator", SEPARATOR, JAR, 0),
        new Step("crystallization", "alkahest:crystallization_powder", CRYSTALLIZATION, WATER, 0),
        new Step("herbal_paste", "alkahest:herbal_paste", HERBAL_PASTE, WATER, 0),
        new Step("fire_dust", "alkahest:fire_dust", FIRE_DUST, WATER, 0),
        new Step("caustic_slime", "alkahest:caustic_slime", CAUSTIC_SLIME, WATER, 0),
        new Step("aer_wand", "alkahest:crystal_wand_aer", AER_WAND, MONOCLE, 0)
    );

    public static Step forItem(String itemId) {
        for (Step step : STEPS) if (step.itemId().equals(itemId)) return step;
        return null;
    }

    public static boolean isAssembly(long bit) { return (ASSEMBLIES & bit) != 0; }
    public static boolean visible(long bit) { return (HIDDEN_COMPONENTS & bit) == 0; }
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
    public static long resolve(long completed, long observed, long actions) {
        long result = completed & ALL;
        boolean changed;
        do {
            changed = false;
            for (Step step : STEPS) {
                if ((result & step.bit()) == 0 && ((isAssembly(step.bit()) ? actions : observed) & step.bit()) != 0 && available(step, result)) {
                    result |= step.bit();
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private PracticalResearchPlan() { }
}
