package net.alkahest.test;

import net.alkahest.Alkahest;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.research.PracticalResearchPlan;
import net.alkahest.research.PracticalResearchStorage;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public class PracticalResearchGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void observationsArePlayerSpecificPersistentAndWaitForParents(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
            c.assertTrue(entry != null, "Every practical flag has a persistent criterion");
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        ItemStack monocle = new ItemStack(ModItems.ALCHEMISTS_MONOCLE);
        PracticalResearchStorage.observe(player, monocle);
        c.assertTrue(monocle.getCount() == 1, "Observing an item never consumes it");
        c.assertTrue(PracticalResearchStorage.observed(player) == PracticalResearchPlan.MONOCLE, "An early item is recorded");
        c.assertTrue(PracticalResearchStorage.completed(player) == 0, "A locked child cannot be completed early");
        PracticalResearchStorage.observe(player, new ItemStack(ModBlocks.ALCHEMY_CAULDRON));
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.PURIFIED_WATER));
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.MONOCLE) == 0, "Both branches are required");
        PracticalResearchStorage.observe(player, new ItemStack(ModBlocks.DISTILLERY));
        long expected = PracticalResearchPlan.CAULDRON | PracticalResearchPlan.WATER | PracticalResearchPlan.DISTILLERY | PracticalResearchPlan.MONOCLE;
        c.assertTrue(PracticalResearchStorage.completed(player) == expected, "The saved early item completes when both parents are ready");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue(PracticalResearchStorage.completed(player) == expected, "Completion survives reload");
        c.assertTrue((PracticalResearchStorage.observed(player) & PracticalResearchPlan.MONOCLE) != 0, "Observation survives reload");
        c.complete();
    }
}
