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
        for (String name : new String[]{"stabilizer", "tier2", "transmutator", "matrix"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/action_" + name)), "done");
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
    public void catalogueSearchUsesRealRecipesAndAllIngredients(TestContext c) {
        var transmutations = net.alkahest.transmutation.TransmutationRecipe.all();
        var matrix = net.alkahest.matrix.MatrixRecipe.all();
        c.assertTrue(transmutations.stream().allMatch(r -> net.alkahest.research.RecipeCatalogueSearch.matches("", r)), "Empty search lists every transmutation");
        c.assertTrue(matrix.stream().allMatch(r -> net.alkahest.research.RecipeCatalogueSearch.matches("", r)), "Empty search lists every synthesis");
        var diamond = transmutations.stream().filter(r -> net.alkahest.research.RecipeCatalogueSearch.matches("iron_ingot diamond", r)).toList();
        c.assertTrue(diamond.size() == 1 && diamond.getFirst().output().isOf(net.minecraft.item.Items.DIAMOND), "Find the alternate iron to diamond recipe");
        c.assertTrue(net.alkahest.research.RecipeCatalogueSearch.matches("TERRA METALLUM", diamond.getFirst()), "Search across multiple essence fields");
        c.assertFalse(net.alkahest.research.RecipeCatalogueSearch.matches("iron_ingot diamond aqua", diamond.getFirst()), "Missing terms must not match");
        var arrows = matrix.stream().filter(r -> net.alkahest.research.RecipeCatalogueSearch.matches("feather arrow", r)).toList();
        c.assertTrue(arrows.size() == 1 && arrows.getFirst().output().isOf(net.minecraft.item.Items.ARROW), "Search pedestal component and result together");
        c.assertTrue(net.alkahest.research.RecipeCatalogueSearch.matches("flint aer", arrows.getFirst()), "Search base and essence together");
        c.assertFalse(net.alkahest.research.RecipeCatalogueSearch.matches("aer terra", arrows.getFirst()), "Unrelated essence is not included");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ironNecklaceCraftsOnTheNormalWorkbench(TestContext c) {
        var world=c.getWorld();
        var manager=world.getServer().getRecipeManager();
        var iron=new ItemStack(net.minecraft.item.Items.IRON_INGOT);
        var chain=new ItemStack(net.minecraft.item.Items.CHAIN);
        for (var stacks : java.util.List.of(java.util.List.of(iron,chain),java.util.List.of(chain,iron))) {
            var input=net.minecraft.recipe.input.CraftingRecipeInput.create(2,1,stacks);
            var match=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,world);
            c.assertTrue(match.isPresent(), "Necklace has a shapeless workbench recipe");
            var out=match.get().value().craft(input,world.getRegistryManager());
            c.assertTrue(out.isOf(ModItems.IRON_CHAIN) && out.getCount()==1, "Iron and chain make exactly one necklace");
        }
        var wrong=net.minecraft.recipe.input.CraftingRecipeInput.create(2,1,java.util.List.of(iron,new ItemStack(net.minecraft.item.Items.STRING)));
        var other=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,wrong,world);
        c.assertFalse(other.isPresent() && other.get().value().craft(wrong,world.getRegistryManager()).isOf(ModItems.IRON_CHAIN), "Wrong ingredients cannot produce the necklace");
        c.assertFalse(net.alkahest.essence.ItemEssenceMap.get(new ItemStack(ModItems.IRON_CHAIN),world).isEmpty(), "New recipe has a calculated essence composition");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ankletBlankCraftsSeparatelyFromNecklace(TestContext c) {
        var world=c.getWorld(); var manager=world.getServer().getRecipeManager();
        ItemStack chain=new ItemStack(net.minecraft.item.Items.CHAIN);
        ItemStack nugget=new ItemStack(net.minecraft.item.Items.IRON_NUGGET);
        for (var items : java.util.List.of(java.util.List.of(chain,nugget,nugget,ItemStack.EMPTY),
                                           java.util.List.of(nugget,ItemStack.EMPTY,chain,nugget))) {
            var input=net.minecraft.recipe.input.CraftingRecipeInput.create(2,2,items);
            var result=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,world);
            c.assertTrue(result.isPresent() && result.get().value().craft(input,world.getRegistryManager()).isOf(ModItems.ANKLET_BLANK),
                "Chain and two nuggets craft the anklet in any arrangement");
            c.assertTrue(result.get().value().craft(input,world.getRegistryManager()).getCount()==1, "One blank per craft");
        }
        var wrong=net.minecraft.recipe.input.CraftingRecipeInput.create(2,1,java.util.List.of(chain,nugget));
        var result=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,wrong,world);
        c.assertFalse(result.isPresent() && result.get().value().craft(wrong,world.getRegistryManager()).isOf(ModItems.ANKLET_BLANK),
            "One nugget is not enough");
        c.assertTrue(ModItems.IRON_CHAIN != ModItems.ANKLET_BLANK, "Necklace and anklet are separate persistent items");
        c.assertFalse(net.alkahest.essence.ItemEssenceMap.get(new ItemStack(ModItems.ANKLET_BLANK),world).isEmpty(), "Anklet has a calculated composition");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void earlyGoldAndDiamondAreRememberedAcrossReload(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
            c.assertTrue(entry != null, "Every research flag exists");
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        var gold = new ItemStack(net.minecraft.item.Items.GOLD_INGOT);
        var diamond = new ItemStack(net.minecraft.item.Items.DIAMOND);
        PracticalResearchStorage.observe(player, diamond);
        PracticalResearchStorage.observe(player, gold);
        long both = PracticalResearchPlan.TRANSMUTATION_GOLD | PracticalResearchPlan.TRANSMUTATION_DIAMOND;
        c.assertTrue((PracticalResearchStorage.observed(player) & both) == both, "Both early observations are saved");
        c.assertTrue((PracticalResearchStorage.completed(player) & both) == 0, "Neither child completes without the machine");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.observed(player) & both) == both, "Observations survive reload");
        var parent = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_transmutator"));
        player.getAdvancementTracker().grantCriterion(parent, "done");
        PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.completed(player) & both) == both, "Both branches settle when parent is ready");
        c.assertTrue(gold.getCount() == 1 && diamond.getCount() == 1, "Observation does not consume items");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & both) == both, "Completions survive reload");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void completedLegacyResearchMigratesWithoutDeletingOldState(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (String name : new String[]{"stabilizer", "tier2", "transmutator", "matrix"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/action_" + name)), "done");
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
        for (String name : new String[]{"stabilizer", "tier2", "transmutator", "matrix"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/action_" + name)), "done");
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void expandedBranchesRememberItemsBeforeTheirParents(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (String name : new String[]{"stabilizer", "tier2", "transmutator", "matrix"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/action_" + name)), "done");
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        var stabilizer = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_stabilizer"));
        var alkahest = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_alkahest"));
        player.getAdvancementTracker().grantCriterion(stabilizer, "done");
        player.getAdvancementTracker().grantCriterion(alkahest, "done");
        grant(player, "distillery");
        grant(player, "cauldron");
        // Completed machinery actions are remembered even before the paths to them are open.
        PracticalResearchStorage.assembly(player, PracticalResearchPlan.TRANSMUTATOR);
        PracticalResearchStorage.assembly(player, PracticalResearchPlan.MATRIX);
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.FROST_FLASK));
        c.assertTrue((PracticalResearchStorage.observed(player) & PracticalResearchPlan.FROST) != 0, "Early reagent is remembered");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.FROST) == 0, "It cannot finish before purified water");
        player.getAdvancementTracker().grantCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_water")), "done");
        for (var item : new net.minecraft.item.ItemConvertible[]{ModBlocks.ALCHEMY_PIPE, ModBlocks.ESSENCE_CHARGER,
                ModItems.IRON_CHAIN, ModItems.ALKAHEST_AMULET, ModBlocks.JAR, ModBlocks.TRANSMUTATOR_CORE, ModBlocks.TRANSMUTATOR_CHAMBER,
                ModBlocks.TRANSMUTATOR_CROWN, ModBlocks.MATRIX_CORE, ModBlocks.MATRIX_PEDESTAL})
            PracticalResearchStorage.observe(player, new ItemStack(item));
        long expected = PracticalResearchPlan.PIPE | PracticalResearchPlan.CHARGER | PracticalResearchPlan.NECKLACE_BLANK | PracticalResearchPlan.AMULET
            | PracticalResearchPlan.TRANSMUTATOR | PracticalResearchPlan.CHAMBER | PracticalResearchPlan.CROWN
            | PracticalResearchPlan.MATRIX | PracticalResearchPlan.PEDESTAL | PracticalResearchPlan.FROST;
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected, "Both branches join and the early flask completes");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.PURIFICATION) == 0, "Unobtained siblings remain unfinished");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected, "The expanded branch survives reload");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stabilizerAndJarBranchesRememberEarlyItemsWithoutRelocking(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (String name : new String[]{"stabilizer", "tier2", "transmutator", "matrix"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/action_" + name)), "done");
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"}) {
            var entry = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id()));
            player.getAdvancementTracker().revokeCriterion(entry, "done");
        }
        var jar = new ItemStack(ModBlocks.JAR);
        PracticalResearchStorage.observe(player, jar);
        PracticalResearchStorage.observe(player, new ItemStack(ModBlocks.STABILIZER_RECEIVER));
        PracticalResearchStorage.observe(player, new ItemStack(ModBlocks.STABILIZER_CREST));
        long branches = PracticalResearchPlan.JAR | PracticalResearchPlan.STABILIZER_RECEIVER | PracticalResearchPlan.STABILIZER_CREST;
        c.assertTrue((PracticalResearchStorage.observed(player) & branches) == branches, "All early items are remembered");
        c.assertTrue((PracticalResearchStorage.completed(player) & branches) == 0 && jar.getCount() == 1, "Items are not consumed and parents are required");
        var distillery = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_distillery"));
        var stabilizer = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_stabilizer"));
        var pipe = player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_pipe"));
        player.getAdvancementTracker().grantCriterion(distillery, "done");
        player.getAdvancementTracker().grantCriterion(stabilizer, "done");
        player.getAdvancementTracker().grantCriterion(pipe, "done");
        PracticalResearchStorage.migrate(player); // resolves previously observed items
        long actual = PracticalResearchStorage.completed(player);
        c.assertTrue((actual & branches) == branches, "Independent early branches finish once their parents are completed");
        c.assertTrue((actual & PracticalResearchPlan.PIPE) != 0, "Existing progress remains intact");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & (branches | PracticalResearchPlan.PIPE)) == (branches | PracticalResearchPlan.PIPE),
            "New and existing completions survive reload");
        c.complete();
    }

    private void clearResearch(net.minecraft.server.network.ServerPlayerEntity player) {
        for (var step : PracticalResearchPlan.STEPS) for (String kind : new String[]{"observed", "completed"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/" + kind + "_" + step.id())), "done");
        for (String name : new String[]{"stabilizer", "tier2", "transmutator", "matrix"})
            player.getAdvancementTracker().revokeCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/action_" + name)), "done");
    }
    private void grant(net.minecraft.server.network.ServerPlayerEntity player, String id) {
        player.getAdvancementTracker().grantCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_" + id)), "done");
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reagentResearchRemembersEarlyItemsAndPreservesOldProgress(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        clearResearch(player);
        grant(player, "matrix");
        ItemStack paste = new ItemStack(ModItems.HERBAL_PASTE, 2);
        c.assertTrue(player.getInventory().insertStack(paste), "Real inventory insertion observes paste");
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.CAUSTIC_SLIME));
        long early = PracticalResearchPlan.HERBAL_PASTE | PracticalResearchPlan.CAUSTIC_SLIME;
        c.assertTrue((PracticalResearchStorage.observed(player) & early) == early, "Early items are recorded independently");
        c.assertTrue((PracticalResearchStorage.completed(player) & early) == 0, "Both await purified water");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.MATRIX) != 0, "Old Matrix completion stays intact");
        grant(player, "water");
        PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.completed(player) & early) == early, "Both finish once the parent is complete");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.FIRE_DUST) == 0, "Unobtained sibling stays unfinished");
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.FIRE_DUST));
        long expected = early | PracticalResearchPlan.FIRE_DUST | PracticalResearchPlan.MATRIX | PracticalResearchPlan.WATER;
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected, "Obtaining fire dust finishes its own branch");
        c.assertTrue(player.getInventory().contains(new ItemStack(ModItems.HERBAL_PASTE)), "Research never consumes the received item");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected, "New high-bit and old progress survive reload");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ingredientResearchUnlocksEarlyProductsInSamePass(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld(); clearResearch(player);
        grant(player, "water");
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.ACID_BOMB));
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.NAPALM));
        long products = PracticalResearchPlan.ACID | PracticalResearchPlan.NAPALM;
        c.assertTrue((PracticalResearchStorage.completed(player) & products) == 0, "Early products wait for their ingredient studies");
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.CAUSTIC_SLIME));
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.ACID) != 0, "Slime completes before the already observed acid bomb");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.NAPALM) == 0, "The fire branch remains independent");
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.FIRE_DUST));
        c.assertTrue((PracticalResearchStorage.completed(player) & products) == products, "Dust also resolves the already observed napalm immediately");
        player.getAdvancementTracker().save(); player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & products) == products, "The chained progress survives reload");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reagentBrewingImmediatelyCreditsTheResearch(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        clearResearch(player); grant(player, "water");
        c.setBlockState(3, 1, 3, net.minecraft.block.Blocks.MAGMA_BLOCK);
        c.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        var cauldron = (net.alkahest.block.entity.CauldronBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 2, 3)));
        for (var output : java.util.List.of(ModItems.HERBAL_PASTE, ModItems.FIRE_DUST, ModItems.CAUSTIC_SLIME)) {
            var recipe = net.alkahest.recipe.CauldronEssenceRecipes.all().stream().filter(r -> r.result() == output).findFirst().orElseThrow();
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(net.minecraft.item.Items.WATER_BUCKET));
            cauldron.fill(player, net.minecraft.util.Hand.MAIN_HAND);
            for (var cost : recipe.cost().entrySet()) for (int i = 0; i < cost.getValue(); i++)
                c.assertTrue(cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(cost.getKey()))), "Essence dissolves");
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(recipe.catalyst()));
            c.assertTrue(cauldron.useItem(player, net.minecraft.util.Hand.MAIN_HAND), "Catalyst brews the real recipe");
            long flag = PracticalResearchPlan.forItem(net.minecraft.registry.Registries.ITEM.getId(output).toString()).bit();
            c.assertTrue((PracticalResearchStorage.completed(player) & flag) != 0, "Brewing and receiving the item credits its research");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void groupedWandObservationAndLegacyAerPersist(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld(); clearResearch(player);
        grant(player, "matrix");
        var wand = new ItemStack(ModItems.WANDS.get(net.alkahest.essence.EssenceType.AER));
        PracticalResearchStorage.observe(player, wand);
        c.assertTrue((PracticalResearchStorage.observed(player) & PracticalResearchPlan.IGNIS_WAND) != 0, "Aer records grouped entry");
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.IGNIS_WAND) == 0, "Wait for monocle");
        grant(player, "monocle"); PracticalResearchStorage.migrate(player);
        long expected = PracticalResearchPlan.IGNIS_WAND | PracticalResearchPlan.MATRIX;
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected, "Parent unlocks group without relocking old progress");
        clearResearch(player);
        player.getAdvancementTracker().grantCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/observed_aer_wand")), "done");
        PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.observed(player) & PracticalResearchPlan.IGNIS_WAND) != 0 &&
            (PracticalResearchStorage.completed(player) & PracticalResearchPlan.IGNIS_WAND) == 0, "Old early observation migrates and waits");
        grant(player, "monocle"); PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.IGNIS_WAND) != 0, "Migrated observation completes after parent");
        clearResearch(player);
        player.getAdvancementTracker().grantCriterion(player.getServer().getAdvancementLoader().get(Alkahest.id("practice/completed_aer_wand")), "done");
        PracticalResearchStorage.migrate(player);
        expected = PracticalResearchPlan.AER_WAND | PracticalResearchPlan.IGNIS_WAND;
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected, "Old Aer completion migrates even without parents");
        player.getAdvancementTracker().save(); player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & expected) == expected && wand.getCount() == 1, "Legacy and grouped progress survive reload");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void materialWorkbenchRecipesDoNotReplaceVanillaWool(TestContext c) {
        var world=c.getWorld(); var manager=world.getServer().getRecipeManager();
        var s=new ItemStack(net.minecraft.item.Items.STRING);
        var cloth=net.minecraft.recipe.input.CraftingRecipeInput.create(3,2,java.util.List.of(s,s,s,ItemStack.EMPTY,s,ItemStack.EMPTY));
        var match=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,cloth,world);
        c.assertTrue(match.isPresent() && match.get().value().craft(cloth,world.getRegistryManager()).isOf(ModItems.CLOTH), "Four strings in the T pattern yield cloth");
        var wool=net.minecraft.recipe.input.CraftingRecipeInput.create(2,2,java.util.List.of(s,s,s,s));
        var vanilla=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,wool,world);
        c.assertTrue(vanilla.isPresent() && vanilla.get().value().craft(wool,world.getRegistryManager()).isOf(net.minecraft.item.Items.WHITE_WOOL), "The vanilla wool recipe stays intact");
        var mix=net.minecraft.recipe.input.CraftingRecipeInput.create(2,2,java.util.List.of(
            new ItemStack(ModItems.BINDING_SALT), new ItemStack(net.minecraft.item.Items.AMETHYST_SHARD),
            new ItemStack(ModItems.CRYSTALS.get(net.alkahest.essence.EssenceType.ORDO)), new ItemStack(net.minecraft.item.Items.GOLD_NUGGET)));
        var reagent=manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,mix,world);
        c.assertTrue(reagent.isPresent() && reagent.get().value().craft(mix,world.getRegistryManager()).isOf(ModItems.STABILIZING_REAGENT), "Reagent is crafted shapelessly");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void jewelryBlankResearchRemembersObtainedItems(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld(); clearResearch(player);
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.IRON_CHAIN));
        PracticalResearchStorage.observe(player, new ItemStack(ModItems.ANKLET_BLANK));
        long blanks = PracticalResearchPlan.NECKLACE_BLANK | PracticalResearchPlan.ANKLET_BLANK;
        c.assertTrue((PracticalResearchStorage.observed(player) & blanks) == blanks, "Both independently observed");
        c.assertTrue((PracticalResearchStorage.completed(player) & blanks) == 0, "Both wait for cauldron research");
        grant(player, "cauldron"); PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.completed(player) & blanks) == blanks, "Obtained blanks complete separately");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player) & blanks) == blanks, "Blank research survives reload");
        clearResearch(player); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ringBlankRecipeAndResearch(TestContext c) {
        var world=c.getWorld(); var player=c.createMockCreativeServerPlayerInWorld(); clearResearch(player);
        var nugget=new ItemStack(net.minecraft.item.Items.GOLD_NUGGET);
        var input=net.minecraft.recipe.input.CraftingRecipeInput.create(3,3,java.util.List.of(ItemStack.EMPTY,nugget,ItemStack.EMPTY,nugget,ItemStack.EMPTY,nugget,ItemStack.EMPTY,nugget,ItemStack.EMPTY));
        var result=world.getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,world);
        c.assertTrue(result.isPresent() && result.get().value().craft(input,world.getRegistryManager()).isOf(ModItems.RING_BLANK),"Four nuggets craft the ring blank without research");
        PracticalResearchStorage.observe(player,new ItemStack(ModItems.RING_BLANK));
        PracticalResearchStorage.observe(player,new ItemStack(ModItems.RING_TERRA));
        long bits=PracticalResearchPlan.RING_BLANK|PracticalResearchPlan.RING_TERRA;
        c.assertTrue((PracticalResearchStorage.observed(player)&bits)==bits && (PracticalResearchStorage.completed(player)&bits)==0,"Early ring observations are remembered");
        grant(player,"cauldron"); grant(player,"transmutator"); grant(player,"stable_material"); grant(player,"matrix"); PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.completed(player)&bits)==bits,"Parents unlock both obtained items");
        player.getAdvancementTracker().save(); player.getAdvancementTracker().reload(world.getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player)&bits)==bits,"Ring research persists");
        clearResearch(player); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shieldResearchRecordsEarlyItems(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld(); clearResearch(player);
        PracticalResearchStorage.observe(player,new ItemStack(ModItems.RING_SHIELD));
        c.assertTrue((PracticalResearchStorage.observed(player)&PracticalResearchPlan.RING_SHIELD)!=0
            && (PracticalResearchStorage.completed(player)&PracticalResearchPlan.RING_SHIELD)==0,"An early ring is observed but waits for parents");
        grant(player,"cauldron"); grant(player,"ring_blank"); grant(player,"transmutator"); grant(player,"stable_material"); grant(player,"matrix");
        PracticalResearchStorage.migrate(player);
        c.assertTrue((PracticalResearchStorage.completed(player)&PracticalResearchPlan.RING_SHIELD)!=0,"Obtaining the ring completes research after parents");
        player.getAdvancementTracker().save(); player.getAdvancementTracker().reload(c.getWorld().getServer().getAdvancementLoader());
        c.assertTrue((PracticalResearchStorage.completed(player)&PracticalResearchPlan.RING_SHIELD)!=0,"Research survives reload");
        clearResearch(player); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void journalPayloadPreservesResearchBitsAboveThirtyOne(TestContext c) {
        long flags = PracticalResearchPlan.CAULDRON | PracticalResearchPlan.FIRE_DUST | PracticalResearchPlan.CAUSTIC_SLIME | PracticalResearchPlan.AER_WAND | PracticalResearchPlan.NECKLACE_BLANK | PracticalResearchPlan.ANKLET_BLANK
            | PracticalResearchPlan.CLOTH | PracticalResearchPlan.SPIRIT_CLOTH | PracticalResearchPlan.STABILIZING_REAGENT | PracticalResearchPlan.STABLE_MATERIAL | PracticalResearchPlan.RING_BLANK | PracticalResearchPlan.RING_TERRA | PracticalResearchPlan.RING_SHIELD | PracticalResearchPlan.RING_TERRA_2 | PracticalResearchPlan.RING_SHIELD_2 | PracticalResearchPlan.RING_SHIELD_3 | PracticalResearchPlan.RING_SHIELD_4;
        var buf = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), c.getWorld().getRegistryManager());
        try {
            net.alkahest.network.OpenJournalPayload.CODEC.encode(buf, new net.alkahest.network.OpenJournalPayload(flags));
            c.assertTrue(net.alkahest.network.OpenJournalPayload.CODEC.decode(buf).flags() == flags,
                "Journal payload preserves all high research bits");
        } finally { buf.release(); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void assembledMatrixRequiresWandAndTwoPedestals(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld(); clearResearch(player); grant(player, "amulet"); grant(player, "alkahest");
        c.setBlockState(3,3,3,ModBlocks.MATRIX_CORE);
        var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(3,3,3));
        PracticalResearchStorage.observe(player, new ItemStack(ModBlocks.MATRIX_CORE));
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.MATRIX)==0, "The core alone does not complete a full machine");
        var wand = new ItemStack(ModItems.WANDS.get(net.alkahest.essence.EssenceType.IGNIS));
        net.alkahest.block.entity.MatrixCoreBlockEntity.interactNearby(c.getWorld(),pos,player,wand);
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.MATRIX)==0, "An incomplete shell is rejected");
        for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
            if (x==0 && y==0 && z==0) continue;
            c.setBlockState(3+x,3+y,3+z,x!=0 && y!=0 && z!=0 ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
        }
        net.alkahest.block.entity.MatrixCoreBlockEntity.interactNearby(c.getWorld(),pos,player,wand);
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.MATRIX)==0, "Two usable pedestals are part of the complete set");
        c.setBlockState(0,2,3,ModBlocks.MATRIX_PEDESTAL); c.setBlockState(6,2,3,ModBlocks.MATRIX_PEDESTAL);
        net.alkahest.block.entity.MatrixCoreBlockEntity.interactNearby(c.getWorld(),pos,player,wand);
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.MATRIX)!=0, "Using a wand on the awakened complete assembly is credited");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stabilizerAndTransmutatorRecordRealActivation(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld(); clearResearch(player);
        grant(player,"alkahest"); grant(player,"pipe"); grant(player,"jar"); grant(player,"amulet");
        c.setBlockState(2,2,2,ModBlocks.STABILIZER_RECEIVER); c.setBlockState(2,3,2,ModBlocks.STABILIZER_CORE); c.setBlockState(2,4,2,ModBlocks.STABILIZER_CREST);
        var p = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(2,3,2));
        var stabilizer = (net.alkahest.block.entity.StabilizerCoreBlockEntity)c.getWorld().getBlockEntity(p);
        PracticalResearchStorage.observe(player,new ItemStack(ModBlocks.STABILIZER_CORE));
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.STABILIZER)==0,"Core item is not the assembly");
        stabilizer.toggle(player);
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.STABILIZER)!=0,"Real activation credits complete tower");
        c.setBlockState(5,2,4,ModBlocks.TRANSMUTATOR_CORE); c.setBlockState(5,3,4,ModBlocks.TRANSMUTATOR_CHAMBER); c.setBlockState(5,4,4,ModBlocks.TRANSMUTATOR_CROWN);
        var q = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(5,3,4));
        var wand = new ItemStack(ModItems.WANDS.get(net.alkahest.essence.EssenceType.AQUA));
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(q), net.minecraft.util.math.Direction.NORTH, q, false);
        c.getWorld().getBlockState(q).onUseWithItem(wand,c.getWorld(),player,net.minecraft.util.Hand.MAIN_HAND,hit);
        c.assertTrue((PracticalResearchStorage.completed(player) & PracticalResearchPlan.TRANSMUTATOR)!=0,"Wand on chamber awakens and records the assembled tower");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tierTwoResearchRequiresActualRunningMachine(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld(); clearResearch(player); grant(player,"alkahest"); grant(player,"distillery");
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_TIER2);
        var pos=c.getAbsolutePos(new net.minecraft.util.math.BlockPos(3,2,3));
        var cube=(net.alkahest.block.entity.Tier2DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        PracticalResearchStorage.observe(player,new ItemStack(ModBlocks.DISTILLERY_TIER2));
        cube.setStack(0,new ItemStack(net.minecraft.item.Items.STONE));
        player.currentScreenHandler=cube.createMenu(1,player.getInventory(),player);
        net.alkahest.block.entity.Tier2DistilleryBlockEntity.tick(c.getWorld(),pos,cube);
        PracticalResearchStorage.scan(player);
        c.assertTrue((PracticalResearchStorage.completed(player)&PracticalResearchPlan.TIER2)==0,"A cube without bellows has not started");
        c.setBlockState(3,3,3,ModBlocks.DISTILLERY_BELLOWS);
        net.alkahest.block.entity.Tier2DistilleryBlockEntity.tick(c.getWorld(),pos,cube);
        PracticalResearchStorage.scan(player);
        c.assertTrue((PracticalResearchStorage.completed(player)&PracticalResearchPlan.TIER2)!=0,"Watching a valid assembled cube start credits the player");
        c.complete();
    }

}
