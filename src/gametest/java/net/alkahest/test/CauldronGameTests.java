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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.Map;

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

    private static void supplyEssences(CauldronBlockEntity cauldron, Map<EssenceType, Integer> cost) {
        for (var entry : cost.entrySet()) {
            ItemStack crystal = new ItemStack(ModItems.CRYSTALS.get(entry.getKey()));
            for (int i = 0; i < entry.getValue(); i++) {
                if (!cauldron.dissolveOne(crystal)) {
                    throw new AssertionError("Failed to dissolve crystal for " + entry.getKey());
                }
            }
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 110)
    public void droppedCatalystAlwaysDissolvesWhileRightClickCatalystBrews(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = fill(context, cauldron);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));

        // Preload 3 Ignis + 3 Potentia — enough for the gunpowder catalyst recipe (fire dust).
        supplyEssences(cauldron, Map.of(EssenceType.IGNIS, 3, EssenceType.POTENTIA, 3));
        // Dropping gunpowder into the cauldron MUST dissolve it into essences instead of triggering the catalyst!
        ItemEntity droppedGunpowder = item(context, 3.5, 4.2, 3.5, new ItemStack(Items.GUNPOWDER));
        ItemEntity droppedUnknown = item(context, 3.5, 4.5, 3.5, new ItemStack(Items.BEDROCK));
        context.runAtTick(35, () -> {
            context.assertTrue(droppedGunpowder.isRemoved(), "Dropped gunpowder must dissolve into essences even when its recipe is ready");
            context.assertTrue(!droppedUnknown.isRemoved(), "Unmapped bedrock dropped into water does not brew or vanish");
            droppedUnknown.discard();
            context.assertTrue(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Dissolving does not consume water");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 7, "3 + 4 = 7 Ignis after dissolving gunpowder");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.PERDITIO, 0) == 5, "5 Perditio added from dissolved gunpowder");

            // Right-clicking with gunpowder triggers the catalyst and brews 2 fire dust!
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GUNPOWDER));
            context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "Right-clicking gunpowder must trigger catalyst");
            context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.FIRE_DUST)), "Fire dust delivered to player");
            context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Brewing consumes water");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 4, "4 surplus Ignis remain");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.PERDITIO, 0) == 5, "5 surplus Perditio remain");

            // Refill with Aqua wand (1 charge) and precipitate residue with dropped crystallization dust.
            ItemStack aquaWand = new ItemStack(ModItems.WANDS.get(EssenceType.AQUA));
            player.setStackInHand(Hand.MAIN_HAND, aquaWand);
            context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "Aqua wand must refill dry cauldron");
            context.assertTrue(aquaWand.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) == 99, "Aqua wand spends 1 charge");

            Box area = new Box(pos).expand(2.5);
            ItemEntity powder = item(context, 3.5, 4.0, 3.5, new ItemStack(ModItems.CRYSTALLIZATION_POWDER, 2));
            context.runAtTick(80, () -> {
                context.assertTrue(cauldron.essences().isEmpty(), "Crystallization powder clears all surplus");
                context.assertTrue(!powder.isRemoved() && powder.getStack().getCount() == 1, "Only one powder consumed");
                var crystals = context.getWorld().getEntitiesByClass(ItemEntity.class, area,
                    e -> e.isAlive() && ModItems.CRYSTALS.containsValue(e.getStack().getItem()));
                int total = 0;
                for (ItemEntity entity : crystals) {
                    Float stability = entity.getStack().get(ModDataComponents.ESSENCE_STABILITY);
                    context.assertTrue(stability != null && Math.abs(stability - CauldronBlockEntity.RESIDUE_STABILITY) < 0.0001f,
                        "Precipitated crystals must have 5% stability: " + stability);
                    total += entity.getStack().getCount();
                }
                context.assertTrue(total == 9, "Expected 4 Ignis + 5 Perditio = 9 crystals, got " + total);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rightClickDissolvesDirectlyShiftDissolvesStacksAndNeverStoresItems(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity survival = fill(context, cauldron);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));

        survival.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BEDROCK, 2));
        context.assertFalse(cauldron.useItem(survival, Hand.MAIN_HAND), "Unmapped item without essences must not be consumed");
        context.assertTrue(survival.getStackInHand(Hand.MAIN_HAND).getCount() == 2, "Unmapped item must remain in hand");
        context.assertFalse(cauldron.createNbt(context.getWorld().getRegistryManager()).contains("Items"), "No hidden item slots exist");

        survival.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COAL, 4));
        context.assertTrue(cauldron.useItem(survival, Hand.MAIN_HAND), "Right-clicking coal must dissolve it directly");
        context.assertTrue(survival.getStackInHand(Hand.MAIN_HAND).getCount() == 3, "Regular right-click consumes 1 item");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 3, "Right-clicked coal adds 3 Ignis");

        survival.setSneaking(true);
        context.assertTrue(AlchemyCauldronBlock.sneakUseWithItem(context.getWorld(), pos, survival, Hand.MAIN_HAND).isAccepted(),
            "Sneak right-click with non-block item must be handled");
        context.assertTrue(survival.getStackInHand(Hand.MAIN_HAND).isEmpty(), "Sneak right-click dissolves full stack");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 12, "All 4 coals dissolved (12 Ignis)");

        survival.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 4));
        context.assertTrue(AlchemyCauldronBlock.sneakUseWithItem(context.getWorld(), pos, survival, Hand.MAIN_HAND) == ActionResult.PASS,
            "Block items must pass through sneak-use for normal block placement");
        survival.setSneaking(false);

        PlayerEntity creative = context.createMockPlayer(GameMode.CREATIVE);
        creative.setSneaking(true);
        creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CRYSTALS.get(EssenceType.AQUA), 5));
        context.assertTrue(cauldron.useItem(creative, Hand.MAIN_HAND), "Creative stack dissolution must succeed");
        context.assertTrue(creative.getStackInHand(Hand.MAIN_HAND).getCount() == 5, "Creative stack must not shrink");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.AQUA, 0) == 5, "5 Aqua added");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void essenceCapLegacyMigrationAndSaveWork(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        fill(context, cauldron);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));

        var registry = context.getWorld().getRegistryManager();
        NbtCompound oldNbt = cauldron.createNbt(registry);
        DefaultedList<ItemStack> oldSlots = DefaultedList.ofSize(6, ItemStack.EMPTY);
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Legacy catalyst"));
        oldSlots.set(0, named);
        Inventories.writeNbt(oldNbt, oldSlots, registry);
        cauldron.readComponentlessNbt(oldNbt, registry);

        ItemEntity many = item(context, 3.5, 4.0, 3.5, new ItemStack(Items.DIAMOND, 20));
        ItemEntity outside = item(context, 4.3, 2.5, 3.5, new ItemStack(Items.COAL));
        outside.setNoGravity(true);

        context.runAtTick(30, () -> {
            Box area = new Box(pos).expand(2.5);
            var ejected = context.getWorld().getEntitiesByClass(ItemEntity.class, area,
                e -> e.isAlive() && e.getStack().isOf(Items.DIAMOND) && e.getStack().get(DataComponentTypes.CUSTOM_NAME) != null);
            context.assertTrue(ejected.size() == 1 && ejected.getFirst().getStack().getName().getString().equals("Legacy catalyst"),
                "Items from old <= 0.1.24 cauldron saves must be safely ejected");
            context.assertFalse(cauldron.createNbt(registry).contains("Items"), "Ejected legacy slots must be cleaned from NBT");
            context.assertTrue(cauldron.essences().getOrDefault(EssenceType.TERRA, 0) == 255, "17 diamonds dissolve up to 255 Terra");
            context.assertTrue(!many.isRemoved() && many.getStack().getCount() == 3, "Excess above 256 cap remains in world");
            context.assertTrue(!outside.isRemoved() && outside.getStack().getCount() == 1, "Outside items are ignored");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allEssenceCatalystRecipesBrewViaRightClickAndCrystallizeWorks(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.getInventory().selectedSlot = 8;

        for (var recipe : CauldronEssenceRecipes.all()) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            cauldron.fill(player, Hand.MAIN_HAND);
            supplyEssences(cauldron, recipe.cost());
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(recipe.catalyst(), 2));
            context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "Recipe must brew directly on catalyst right-click: " + recipe.catalyst());
            context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "Exactly 1 catalyst consumed for " + recipe.catalyst());
            context.assertTrue(player.getInventory().contains(new ItemStack(recipe.result())), "Expected result " + recipe.result());
            context.assertTrue(cauldron.essences().isEmpty(), "Exact essence cost leaves no residue for " + recipe.result());
        }

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 3));
        context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "1st seed dissolves (3 Herba, 1 Aqua)");
        context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "2nd seed dissolves (6 Herba, 2 Aqua)");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.HERBA, 0) == 6, "6 Herba accumulated");
        context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "3rd seed right-click triggers catalyst and brews herbal paste");
        context.assertTrue(cauldron.essences().getOrDefault(EssenceType.HERBA, 0) == 2, "2 Herba remain as residue");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CRYSTALLIZATION_POWDER, 2));
        context.assertFalse(cauldron.useItem(player, Hand.MAIN_HAND), "Dry cauldron cannot crystallize");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CRYSTALLIZATION_POWDER, 2));
        context.assertTrue(cauldron.useItem(player, Hand.MAIN_HAND), "Right-click crystallization precipitates residue");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "1 powder consumed");
        context.assertTrue(cauldron.essences().isEmpty(), "Residue cleared");
        context.complete();
    }
}
