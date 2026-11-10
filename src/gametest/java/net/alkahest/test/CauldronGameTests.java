package net.alkahest.test;

import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.recipe.CauldronEssenceRecipes;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.EnumMap;

public class CauldronGameTests implements FabricGameTest {
    private static ItemEntity item(TestContext context, double x, double y, double z, ItemStack stack) {
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        ItemEntity item = new ItemEntity(context.getWorld(), origin.getX() + x, origin.getY() + y, origin.getZ() + z, stack);
        item.setVelocity(0, 0, 0);
        context.getWorld().spawnEntity(item);
        return item;
    }

    private static CauldronBlockEntity cauldron(TestContext context) {
        context.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        context.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        return (CauldronBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(new BlockPos(3, 2, 3)));
    }

    private static PlayerEntity fill(TestContext context, CauldronBlockEntity cauldron) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        return player;
    }

    private static void supplyEssences(CauldronBlockEntity cauldron, java.util.Map<EssenceType, Integer> cost) {
        for (var entry : cost.entrySet()) {
            ItemStack crystal = new ItemStack(ModItems.CRYSTALS.get(entry.getKey()));
            for (int i = 0; i < entry.getValue(); i++) {
                if (!cauldron.dissolveOne(crystal)) {
                    throw new AssertionError("Failed to dissolve crystal for " + entry.getKey());
                }
            }
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void fallingItemsDissolveAndSurplusRemainsForCrystallization(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = fill(context, cauldron);
        ItemEntity coal = item(context, 3.5, 4.4, 3.5, new ItemStack(Items.COAL));
        ItemEntity apple = item(context, 3.5, 4.8, 3.5, new ItemStack(Items.APPLE));
        ItemEntity unmappedBottle = item(context, 3.5, 5.1, 3.5, new ItemStack(Items.GLASS_BOTTLE));
        context.runAtTick(45, () -> {
            context.assertTrue(coal.isRemoved() && apple.isRemoved(), "Mapped items dropped into hot water must dissolve");
            context.assertTrue(!unmappedBottle.isRemoved(), "Unmapped catalyst items dropped into water must not dissolve");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 3, "Coal must add 3 Ignis");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.AQUA, 0) == 1, "Apple must add 1 Aqua");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.TERRA, 0) == 1, "Coal must add 1 Terra");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.HERBA, 0) == 3, "Apple must add 3 Herba");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.ORDO, 0) == 1, "Apple must add 1 Ordo");

            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
            cauldron.add(player, Hand.MAIN_HAND);
            context.assertTrue(cauldron.activate(player), "Bottle catalyst with dissolved Aqua and Ignis must brew purified water");
            context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.PURIFIED_WATER)), "Purified water must reach player");
            BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
            context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Brewing consumes water");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 1, "1 surplus Ignis must remain in cauldron");
            context.assertTrue(!cauldron.essences().containsKey(EssenceType.AQUA), "Exact Aqua cost must be removed");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.HERBA, 0) == 3, "Unused Herba must remain");

            // Refill water and crystallize surplus by dropping crystallization powder into the cauldron.
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            cauldron.fill(player, Hand.MAIN_HAND);
            ItemEntity powder = item(context, 3.5, 4.2, 3.5, new ItemStack(ModItems.CRYSTALLIZATION_POWDER, 2));
            context.runAtTick(80, () -> {
                context.assertTrue(cauldron.essences().isEmpty(), "Crystallization powder must clear all dissolved surplus");
                context.assertTrue(!powder.isRemoved() && powder.getStack().getCount() == 1, "Only one crystallization powder is consumed");
                Box area = new Box(pos).expand(2.5);
                var crystals = context.getWorld().getEntitiesByClass(ItemEntity.class, area,
                    e -> e.isAlive() && ModItems.CRYSTALS.containsValue(e.getStack().getItem()));
                int totalCrystals = 0;
                for (ItemEntity entity : crystals) {
                    Float stability = entity.getStack().get(ModDataComponents.ESSENCE_STABILITY);
                    context.assertTrue(stability != null && Math.abs(stability - CauldronBlockEntity.RESIDUE_STABILITY) < 0.0001f,
                        "Precipitated crystals must have very low 5% stability: " + stability);
                    totalCrystals += entity.getStack().getCount();
                }
                context.assertTrue(totalCrystals == 6, "Expected 1 Ignis + 1 Terra + 3 Herba + 1 Ordo = 6 crystals, got " + totalCrystals);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void essenceCapAndOutsideItemsAreRespected(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        fill(context, cauldron);
        // Each diamond adds 15 Terra, 15 Ordo, 10 Lux; 17 fit under 256 (17 * 15 = 255), 18th would hit 270.
        ItemEntity many = item(context, 3.5, 4.0, 3.5, new ItemStack(Items.DIAMOND, 20));
        ItemEntity outside = item(context, 4.3, 2.5, 3.5, new ItemStack(Items.COAL));
        outside.setNoGravity(true);
        context.runAtTick(35, () -> {
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.TERRA, 0) == 255, "17 diamonds should dissolve up to 255 Terra");
            context.assertTrue(!many.isRemoved() && many.getStack().getCount() == 3, "Items exceeding the 256 per-essence cap must remain in the world");
            context.assertTrue(!outside.isRemoved() && outside.getStack().getCount() == 1, "Items outside the opening must not be dissolved");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void dryOrColdCauldronDoesNotDissolveAndEssencesSurviveSave(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        ItemEntity dryItem = item(context, 3.5, 3.5, 3.5, new ItemStack(Items.COAL, 2));
        context.runAtTick(25, () -> {
            context.assertTrue(!dryItem.isRemoved() && dryItem.getStack().getCount() == 2, "Dry cauldron must not dissolve items");
            context.assertTrue(cauldron.essences().isEmpty(), "Dry cauldron should remain empty");
            dryItem.discard();

            PlayerEntity player = fill(context, cauldron);
            context.setBlockState(3, 1, 3, Blocks.STONE);
            context.assertFalse(cauldron.dissolveOne(new ItemStack(Items.COAL)), "Cold cauldron must not dissolve items");
            context.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
            context.assertTrue(cauldron.dissolveOne(new ItemStack(Items.GOLD_INGOT)), "Heated cauldron with water dissolves mapped items");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_PANE));
            cauldron.add(player, Hand.MAIN_HAND);

            var registry = context.getWorld().getRegistryManager();
            var saved = cauldron.createNbt(registry);
            CauldronBlockEntity restored = new CauldronBlockEntity(cauldron.getPos(), cauldron.getCachedState());
            restored.readComponentlessNbt(saved, registry);
            context.assertTrue(restored.essences().getOrDefault(EssenceType.ORDO, 0) == 6, "Dissolved essences must persist in NBT");
            context.assertTrue(restored.essences().getOrDefault(EssenceType.LUX, 0) == 2, "Secondary essences must persist in NBT");
            context.assertTrue(restored.stacks().get(0).isOf(Items.GLASS_PANE), "Placed catalyst must persist in NBT");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allEssenceCatalystRecipesBrewAndRightClickCrystallizationWorks(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.getInventory().selectedSlot = 8;

        for (var recipe : CauldronEssenceRecipes.all()) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            cauldron.fill(player, Hand.MAIN_HAND);
            supplyEssences(cauldron, recipe.cost());
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(recipe.catalyst()));
            cauldron.add(player, Hand.MAIN_HAND);
            context.assertTrue(cauldron.activate(player), "Recipe must brew for catalyst " + recipe.catalyst());
            context.assertTrue(player.getInventory().contains(new ItemStack(recipe.result())), "Expected result " + recipe.result());
            context.assertTrue(cauldron.essences().isEmpty(), "Exact essence cost leaves no residue for " + recipe.result());
            context.assertTrue(cauldron.stacks().stream().allMatch(ItemStack::isEmpty), "Catalyst slot must be cleared");
        }

        // Reject multi-item catalyst attempts without losing placed items or essences.
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        var extra = new EnumMap<EssenceType, Integer>(EssenceType.class);
        extra.put(EssenceType.IGNIS, 3);
        extra.put(EssenceType.POTENTIA, 3);
        extra.put(EssenceType.TERRA, 2);
        supplyEssences(cauldron, extra);
        cauldron.stacks().set(0, new ItemStack(Items.GUNPOWDER));
        cauldron.stacks().set(1, new ItemStack(Items.STONE));
        context.assertFalse(cauldron.activate(player), "Two non-research items must not brew");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 3, "Rejected attempt preserves essences");
        cauldron.stacks().set(1, ItemStack.EMPTY);
        context.assertTrue(cauldron.activate(player), "Single catalyst with required essences brews");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.TERRA, 0) == 2, "Unrelated Terra remains as residue");

        // Right-clicking crystallization powder requires water + heat and consumes 1 powder in survival.
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CRYSTALLIZATION_POWDER, 2));
        context.assertFalse(cauldron.crystallizeWith(player, Hand.MAIN_HAND), "Dry cauldron cannot crystallize");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CRYSTALLIZATION_POWDER, 2));
        context.assertTrue(cauldron.crystallizeWith(player, Hand.MAIN_HAND), "Right-click crystallization must precipitate residue");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "Survival crystallization consumes 1 powder");
        context.assertTrue(cauldron.essences().isEmpty(), "Residue cleared");
        context.assertFalse(cauldron.crystallizeWith(player, Hand.MAIN_HAND), "Empty residue does not consume powder");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "Unused powder preserved");
        context.complete();
    }
}
