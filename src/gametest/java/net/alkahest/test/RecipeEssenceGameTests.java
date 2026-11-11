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
        ItemStack crafted = new ItemStack(ModItems.ASH);
        var composition = CauldronBlockEntity.essencesOf(crafted, c.getWorld());
        c.assertTrue(composition.getOrDefault(EssenceType.HERBA, 0) == 2, "Apple Herba divided by two and rounded");
        c.assertTrue(composition.getOrDefault(EssenceType.AQUA, 0) == 1, "Half an Aqua rounds up");
        c.assertTrue(composition.getOrDefault(EssenceType.METALLUM, 0) == 3, "Iron Metallum divided by two");
        c.assertTrue(DistilleryBlockEntity.accepts(crafted, c.getWorld()), "Raw cube accepts computed organic material");
        c.assertTrue(Tier2DistilleryBlockEntity.accepts(crafted, c.getWorld()), "Tier two accepts computed material");
        var outputs = Tier2DistilleryBlockEntity.outputs(crafted, c.getWorld().random, false, c.getWorld());
        c.assertTrue(outputs.stream().anyMatch(e -> e.type() == EssenceType.METALLUM && e.amount() == 3), "Cube outputs match calculated composition");
        c.assertTrue(ItemEssenceMap.get(new ItemStack(Items.BEDROCK), c.getWorld()).isEmpty(), "Unknown raw material remains unmapped");
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
