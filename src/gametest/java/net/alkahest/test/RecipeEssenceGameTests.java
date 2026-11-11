package net.alkahest.test;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.essence.RecipeEssenceService;
import net.alkahest.registry.ModItems;
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
        var before = ItemEssenceMap.get(crafted, c.getWorld());
        RecipeEssenceService.rebuild(c.getWorld().getServer());
        c.assertTrue(before.equals(ItemEssenceMap.get(crafted, c.getWorld())), "Rebuilding is deterministic");
        c.complete();
    }
}
