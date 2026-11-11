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
    public void observationsPersistAndWaitForParents(TestContext c) {
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void completedLegacyResearchMigratesWithoutDeletingOldState(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        // A fresh tracker state for the legacy milestones used here, including a discovered-only node.
        for (String name : new String[]{"cauldron_complete", "alkahest_complete", "stabilization_discovered", "stabilization_complete"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("research/" + name));
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        var oldCauldron = player.getServer().getAdvancementLoader().get(Alkahest.id("research/cauldron_complete"));
        var oldAlkahest = player.getServer().getAdvancementLoader().get(Alkahest.id("research/alkahest_complete"));
        var oldDiscovered = player.getServer().getAdvancementLoader().get(Alkahest.id("research/stabilization_discovered"));
        player.getAdvancementTracker().grantCriterion(oldCauldron, "done");
        player.getAdvancementTracker().grantCriterion(oldAlkahest, "done");
        player.getAdvancementTracker().grantCriterion(oldDiscovered, "done");
        PracticalResearchStorage.migrate(player);
        long migrated = PracticalResearchStorage.completed(player);
        c.assertTrue((migrated & (PracticalResearchPlan.CAULDRON | PracticalResearchPlan.ALKAHEST))
            == (PracticalResearchPlan.CAULDRON | PracticalResearchPlan.ALKAHEST), "Completed old nodes are preserved even when the new order differs");
        c.assertTrue((migrated & PracticalResearchPlan.STABILIZER) == 0, "Merely discovering an old node does not complete it");
        c.assertTrue(player.getAdvancementTracker().getProgress(oldAlkahest).isDone(), "Migration does not revoke original progress");
        PracticalResearchStorage.migrate(player);
        c.assertTrue(PracticalResearchStorage.completed(player) == migrated, "Repeated migration is idempotent");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue(PracticalResearchStorage.completed(player) == migrated, "Migrated progress survives reload");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryScanAndCauldronNoLongerTriggerHypotheses(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        var old = player.getServer().getAdvancementLoader().get(Alkahest.id("research/cauldron_discovered"));
        player.getAdvancementTracker().grantCriterion(old, "done");
        c.setBlockState(2, 1, 2, net.minecraft.block.Blocks.MAGMA_BLOCK);
        c.setBlockState(2, 2, 2, ModBlocks.ALCHEMY_CAULDRON);
        var cauldron = (net.alkahest.block.entity.CauldronBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 2, 2)));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(net.minecraft.item.Items.WATER_BUCKET));
        cauldron.useItem(player, net.minecraft.util.Hand.MAIN_HAND);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(net.minecraft.item.Items.CHARCOAL));
        c.assertTrue(cauldron.useItem(player, net.minecraft.util.Hand.MAIN_HAND), "Charcoal dissolves");
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(net.minecraft.item.Items.COAL));
        c.assertTrue(cauldron.useItem(player, net.minecraft.util.Hand.MAIN_HAND), "Coal dissolves instead of launching an obsolete hypothesis");
        c.assertTrue(cauldron.essences().get(net.alkahest.essence.EssenceType.IGNIS) == 6, "No hypothesis consumes the contents");
        ItemStack received = new ItemStack(ModBlocks.ALCHEMY_CAULDRON);
        c.assertTrue(player.getInventory().insertStack(received), "The player receives the cauldron");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.CAULDRON) != 0, "Inventory insertion immediately advances the tree");
        c.assertTrue(player.getInventory().contains(new ItemStack(ModBlocks.ALCHEMY_CAULDRON)), "Research never consumes the item");
        c.complete();
    }
}
