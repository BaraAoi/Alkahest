package net.alkahest.research;

import net.alkahest.Alkahest;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side persistence for the new practical tree. Not yet connected to gameplay;
 * keep the old progress and handlers intact until all recipe gates are ready.
 */
public final class PracticalResearchStorage {
    private static AdvancementEntry entry(ServerPlayerEntity player, String kind, PracticalResearchPlan.Step step) {
        return player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
    }
    private static boolean has(ServerPlayerEntity player, String kind, PracticalResearchPlan.Step step) {
        AdvancementEntry entry = entry(player, kind, step);
        return entry != null && player.getAdvancementTracker().getProgress(entry).isDone();
    }
    private static boolean grant(ServerPlayerEntity player, String kind, PracticalResearchPlan.Step step) {
        AdvancementEntry entry = entry(player, kind, step);
        return entry != null && player.getAdvancementTracker().grantCriterion(entry, "done");
    }
    private static long flags(ServerPlayerEntity player, String kind) {
        long result = 0;
        for (var step : PracticalResearchPlan.STEPS) if (has(player, kind, step)) result |= step.bit();
        return result;
    }
    public static long observed(ServerPlayerEntity player) { return flags(player, "observed"); }
    public static long completed(ServerPlayerEntity player) { return flags(player, "completed"); }

    /** Record an item received by this player, even if the node is not available yet. */
    public static void observe(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        var step = PracticalResearchPlan.forItem(Registries.ITEM.getId(stack.getItem()).toString());
        if (step == null) return;
        grant(player, "observed", step);
        settle(player);
    }

    /** Idempotent migration; no old advancement is removed or modified. */
    public static void migrate(ServerPlayerEntity player) {
        long old = PracticalResearchPlan.migrate(EarlyResearch.flags(player));
        for (var step : PracticalResearchPlan.STEPS)
            if ((old & step.bit()) != 0) grant(player, "completed", step);
        settle(player);
    }

    /** Grant in parent order and re-read persistent state, so missing data cannot advance children. */
    private static void settle(ServerPlayerEntity player) {
        long observed = observed(player);
        for (var step : PracticalResearchPlan.STEPS) {
            long completed = completed(player);
            if ((observed & step.bit()) != 0 && (completed & step.bit()) == 0
                && PracticalResearchPlan.available(step, completed)) grant(player, "completed", step);
        }
    }

    private PracticalResearchStorage() { }
}
