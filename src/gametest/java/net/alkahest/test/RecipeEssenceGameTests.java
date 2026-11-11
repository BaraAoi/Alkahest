package net.alkahest.test;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.essence.RecipeEssenceService;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public class RecipeEssenceGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void actualRecipesSupplyCauldronAndDistilleries(TestContext c) {
        ItemStack crafted = new ItemStack(TestItems.CRAFTED);
        var composition = CauldronBlockEntity.essencesOf(crafted, c.getWorld());
        c.assertTrue(composition.getOrDefault(EssenceType.HERBA, 0) == 2, "Apple Herba divided by two and rounded");
        c.assertTrue(composition.getOrDefault(EssenceType.AQUA, 0) == 1, "Half an Aqua rounds up");
        c.assertTrue(composition.getOrDefault(EssenceType.METALLUM, 0) == 3, "Iron Metallum divided by two");
        c.assertTrue(DistilleryBlockEntity.accepts(crafted, c.getWorld()), "Raw cube accepts computed organic material");
        c.assertTrue(Tier2DistilleryBlockEntity.accepts(crafted, c.getWorld()), "Tier two accepts computed material");
        var outputs = Tier2DistilleryBlockEntity.outputs(crafted, c.getWorld().random, false, c.getWorld());
        c.assertTrue(outputs.stream().anyMatch(e -> e.type() == EssenceType.METALLUM && e.amount() == 3), "Cube outputs match calculated composition");
        c.assertTrue(ItemEssenceMap.get(new ItemStack(TestItems.UNKNOWN), c.getWorld()).isEmpty(), "Unknown raw material remains unmapped");
        c.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        c.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        var cauldron = (CauldronBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, 2, 3)));
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        c.assertTrue(cauldron.dissolveOne(crafted), "Crafted item dissolves in the heated cauldron");
        c.assertTrue(cauldron.essences().getOrDefault(EssenceType.METALLUM, 0) == 3, "Dissolved composition is applied");
        c.setBlockState(5, 2, 3, ModBlocks.DISTILLERY_TIER2);
        c.setBlockState(5, 3, 3, ModBlocks.DISTILLERY_BELLOWS);
        var pos = c.getAbsolutePos(new BlockPos(5, 2, 3));
        var cube = (Tier2DistilleryBlockEntity) c.getWorld().getBlockEntity(pos);
        c.assertTrue(cube.isValid(0, crafted), "Inventory filtering uses the server snapshot");
        cube.setStack(0, crafted.copy());
        for (int i = 0; i < Tier2DistilleryBlockEntity.DURATION; i++) Tier2DistilleryBlockEntity.tick(c.getWorld(), pos, cube);
        c.assertTrue(cube.getStack(0).isEmpty(), "Cube processes the recipe-derived item");
        c.assertTrue(!cube.bellows().getFirst().empty(), "Computed essence is delivered to bellows");
        var before = ItemEssenceMap.get(crafted, c.getWorld());
        RecipeEssenceService.rebuild(c.getWorld().getServer());
        c.assertTrue(before.equals(ItemEssenceMap.get(crafted, c.getWorld())), "Rebuilding is deterministic");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reportedBuildingMaterialsAndItemsHaveEssences(TestContext c) {
        var world = c.getWorld();
        for (var item : java.util.List.of(
            Items.BLAST_FURNACE, ModBlocks.DISTILLERY_TIER2.asItem(), Items.CHERRY_SAPLING,
            Items.BAMBOO_BLOCK, Items.STRIPPED_BAMBOO_BLOCK, Items.CRACKED_STONE_BRICKS,
            Items.SMOOTH_STONE, Items.SMOOTH_STONE_SLAB, Items.MOSSY_COBBLESTONE,
            Items.MOSSY_STONE_BRICKS, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE,
            Items.POLISHED_DEEPSLATE, Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_TILES,
            Items.CRACKED_DEEPSLATE_BRICKS, Items.CRACKED_DEEPSLATE_TILES,
            Items.CHISELED_DEEPSLATE, Items.DEEPSLATE_DIAMOND_ORE, Items.TUFF,
            Items.POLISHED_TUFF, Items.TUFF_BRICKS, Items.MUD_BRICKS,
            Items.CHISELED_SANDSTONE, Items.SMOOTH_SANDSTONE, Items.CAKE, Items.HONEY_BLOCK,
            Items.BROWN_DYE, Items.BROWN_WOOL, Items.BLACK_CONCRETE, Items.SHULKER_BOX,
            Items.EXPOSED_COPPER, Items.WEATHERED_COPPER, Items.OXIDIZED_COPPER,
            Items.WAXED_OXIDIZED_COPPER, Items.NETHERITE_INGOT, ModItems.PURIFYING_FLASK)) {
            c.assertFalse(ItemEssenceMap.get(new ItemStack(item), world).isEmpty(),
                "Missing essence for " + net.minecraft.registry.Registries.ITEM.getId(item));
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reportIncludesEveryRegisteredItem(TestContext c) {
        var server = c.getWorld().getServer();
        var path = RecipeEssenceService.reportPath(server);
        try {
            var lines = java.nio.file.Files.readAllLines(path);
            var entries = lines.stream().filter(line -> !line.startsWith("#") && !line.startsWith("id\t"))
                .collect(java.util.stream.Collectors.toMap(line -> line.split("\t", -1)[0], line -> line));
            int total = 0;
            for (var item : net.minecraft.registry.Registries.ITEM) {
                total++;
                String id = net.minecraft.registry.Registries.ITEM.getId(item).toString();
                String line = entries.get(id);
                c.assertTrue(line != null, "Report is missing registry entry " + id);
                String[] columns = line.split("\t", -1);
                c.assertTrue(columns.length == 4, "Report has four columns for " + id);
                boolean mapped = !ItemEssenceMap.get(new ItemStack(item), c.getWorld()).isEmpty();
                c.assertTrue(mapped == !columns[2].equals("missing"), "Report status matches actual map for " + id);
                c.assertTrue(mapped == !columns[3].isEmpty(), "Report lists values exactly for mapped items " + id);
            }
            c.assertTrue(entries.size() == total, "Report has exactly one row for each registered item");
        } catch (java.io.IOException e) { throw new AssertionError("Cannot read essence report", e); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void filledContainersCannotBeDestroyedForEssence(TestContext c) {
        var world = c.getWorld();
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        c.assertTrue(Tier2DistilleryBlockEntity.accepts(shulker, world), "Empty shulker has a composition");
        shulker.set(net.minecraft.component.DataComponentTypes.CONTAINER,
            net.minecraft.component.type.ContainerComponent.fromStacks(java.util.List.of(new ItemStack(Items.DIAMOND))));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(net.minecraft.component.DataComponentTypes.BUNDLE_CONTENTS,
            new net.minecraft.component.type.BundleContentsComponent(java.util.List.of(new ItemStack(Items.DIAMOND))));
        ItemStack jar = new ItemStack(ModBlocks.JAR);
        net.alkahest.item.custom.JarItem.set(jar, new net.alkahest.essence.JarContentsComponent(
            java.util.List.of(new net.alkahest.essence.EssenceStack(EssenceType.AQUA, 5, 1f))));
        for (ItemStack filled : java.util.List.of(shulker, bundle, jar)) {
            c.assertFalse(ItemEssenceMap.get(filled, world).isEmpty(), "Monocle can still show the base composition");
            c.assertTrue(CauldronBlockEntity.essencesOf(filled, world).isEmpty(), "Cauldron must not destroy a filled container");
            c.assertFalse(Tier2DistilleryBlockEntity.accepts(filled, world), "Cube II must reject a filled container");
            c.assertFalse(DistilleryBlockEntity.acceptsWithAlkahest(filled, world), "Cube I must reject a filled container");
        }
        c.assertTrue(shulker.get(net.minecraft.component.DataComponentTypes.CONTAINER).streamNonEmpty().count() == 1,
            "Stored diamond is untouched");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void clientSnapshotIsAtomicAndIsolatedFromServer(TestContext c) {
        var serverBefore = ItemEssenceMap.get(new ItemStack(Items.APPLE), c.getWorld());
        RecipeEssenceService.clearClient();
        RecipeEssenceService.receive("BEGIN");
        RecipeEssenceService.receive("minecraft:apple=1:9,;");
        c.assertTrue(RecipeEssenceService.clientValues() == null, "Partial snapshot is never exposed");
        RecipeEssenceService.receive("END");
        c.assertTrue(RecipeEssenceService.clientValues().get(Items.APPLE).getFirst().amount() == 9, "Completed snapshot installed");
        c.assertTrue(serverBefore.equals(ItemEssenceMap.get(new ItemStack(Items.APPLE), c.getWorld())), "Client sync does not overwrite the server map");
        RecipeEssenceService.clearClient();
        c.assertTrue(RecipeEssenceService.clientValues() == null, "Disconnect clears old snapshot");
        c.complete();
    }
}
