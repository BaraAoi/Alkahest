package net.alkahest.research;

import net.alkahest.Alkahest;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side persistence for practical research. Recipes are never blocked.
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
        if (step == null || PracticalResearchPlan.isAssembly(step.bit()) || has(player, "observed", step)) return;
        grant(player, "observed", step);
        settle(player);
    }

    /** Record a successful real interaction, independently of currently unlocked parents. */
    public static void assembly(ServerPlayerEntity player, long bit) {
        if (!PracticalResearchPlan.isAssembly(bit)) return;
        for (var step : PracticalResearchPlan.STEPS) if (step.bit() == bit) {
            if (grant(player, "action", step)) settle(player);
            return;
        }
    }

    /** Scan only items belonging to the player, not items in an open container. */
    public static void scan(ServerPlayerEntity player) {
        long before = completed(player);
        if (player.currentScreenHandler instanceof net.alkahest.screen.Tier2DistilleryScreenHandler screen
                && screen.runningForResearch()) assembly(player, PracticalResearchPlan.TIER2);
        for (int i = 0; i < player.getInventory().size(); i++) observe(player, player.getInventory().getStack(i));
        observe(player, player.currentScreenHandler.getCursorStack());
        dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
            if (component.isEquipped(net.alkahest.registry.ModItems.ALCHEMISTS_MONOCLE))
                observe(player, new ItemStack(net.alkahest.registry.ModItems.ALCHEMISTS_MONOCLE));
            if (component.isEquipped(net.alkahest.registry.ModItems.RING_SHIELD))
                observe(player, new ItemStack(net.alkahest.registry.ModItems.RING_SHIELD));
            if (component.isEquipped(net.alkahest.registry.ModItems.RING_TERRA))
                observe(player, new ItemStack(net.alkahest.registry.ModItems.RING_TERRA));
            if (component.isEquipped(net.alkahest.registry.ModItems.ALKAHEST_AMULET))
                observe(player, new ItemStack(net.alkahest.registry.ModItems.ALKAHEST_AMULET));
        });
        if (completed(player) != before) {
            player.sendMessage(net.minecraft.text.Text.translatable("message.alkahest.practice.completed"), true);
            player.getWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
    }

    /** Idempotent migration; no old advancement is removed or modified. */
    public static void migrate(ServerPlayerEntity player) {
        long old = PracticalResearchPlan.migrate(EarlyResearch.flags(player));
        for (var step : PracticalResearchPlan.STEPS)
            if ((old & step.bit()) != 0) grant(player, "completed", step);
        settle(player);
    }

    /** Resolve to a fixed point: dependencies may appear after their children in the stable ID list. */
    private static void settle(ServerPlayerEntity player) {
        long observed = observed(player);
        long existing = completed(player);
        // One-way, idempotent migration of the former Aer entry into the grouped wand entry.
        var grouped = PracticalResearchPlan.STEPS.get(4);
        if ((existing & PracticalResearchPlan.AER_WAND) != 0) grant(player, "completed", grouped);
        if ((observed & PracticalResearchPlan.AER_WAND) != 0) {
            grant(player, "observed", grouped);
            observed = observed(player);
        }
        long before, after = completed(player);
        do {
            before = after;
            for (var step : PracticalResearchPlan.STEPS) {
                boolean achieved = PracticalResearchPlan.isAssembly(step.bit()) ? has(player, "action", step)
                    : (observed & step.bit()) != 0;
                if (achieved && (after & step.bit()) == 0 && PracticalResearchPlan.available(step, after)
                        && grant(player, "completed", step)) {
                    // Read the server's real advancement state rather than assuming the write succeeded.
                    after = completed(player);
                }
            }
        } while (after != before);
    }

    private PracticalResearchStorage() { }
}
