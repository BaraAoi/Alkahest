package net.alkahest.test;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModBlocks;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void fallingIngredientsCanBeCooked(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = fill(context, cauldron);
        ItemEntity coal = item(context, 3.5, 4.4, 3.5, new ItemStack(Items.COAL));
        ItemEntity bottle = item(context, 3.5, 4.8, 3.5, new ItemStack(Items.GLASS_BOTTLE));
        context.runAtTick(45, () -> {
            context.assertTrue(coal.isRemoved() && bottle.isRemoved(), "Falling ingredients should be taken into the cauldron");
            context.assertTrue(cauldron.stacks().stream().filter(s -> !s.isEmpty()).count() == 2, "Two ingredients should be stored");
            context.assertTrue(cauldron.activate(player), "Ingredients dropped into a heated cauldron should cook");
            context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.PURIFIED_WATER)), "Recipe output must reach player");
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void stacksAreLimitedAndOutsideItemsIgnored(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        fill(context, cauldron);
        ItemEntity many = item(context, 3.5, 4.0, 3.5, new ItemStack(Items.DIAMOND, 9));
        ItemEntity outside = item(context, 4.3, 2.5, 3.5, new ItemStack(Items.EMERALD));
        outside.setNoGravity(true);
        context.runAtTick(30, () -> {
            context.assertTrue(cauldron.stacks().stream().filter(s -> s.isOf(Items.DIAMOND) && s.getCount() == 1).count() == 6, "Exactly six slots should fill");
            context.assertTrue(!many.isRemoved() && many.getStack().getCount() == 3, "Excess items should remain in the world");
            context.assertTrue(!outside.isRemoved() && outside.getStack().getCount() == 1, "Items outside the opening must not be taken");
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void dryCauldronDoesNotConsumeItems(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        ItemEntity item = item(context, 3.5, 3.5, 3.5, new ItemStack(Items.COAL, 2));
        context.runAtTick(25, () -> {
            context.assertTrue(!item.isRemoved() && item.getStack().getCount() == 2, "Dry cauldron must not take items");
            context.assertTrue(cauldron.stacks().stream().allMatch(ItemStack::isEmpty), "Dry cauldron should remain empty");
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void droppedItemComponentsSurviveSave(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        fill(context, cauldron);
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Precious sample"));
        item(context, 3.5, 4.0, 3.5, named);
        context.runAtTick(25, () -> {
            var registry = context.getWorld().getRegistryManager();
            var saved = cauldron.createNbt(registry);
            CauldronBlockEntity restored = new CauldronBlockEntity(cauldron.getPos(), cauldron.getCachedState());
            restored.readComponentlessNbt(saved, registry);
            ItemStack result = restored.stacks().get(0);
            context.assertTrue(result.isOf(Items.DIAMOND), "Dropped ingredient must persist in block entity NBT");
            context.assertTrue(result.get(DataComponentTypes.CUSTOM_NAME) != null && result.getName().getString().equals("Precious sample"), "Item components must survive insertion and saving");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fireDustRecipesRequireWaterAndHeat(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        cauldron.stacks().set(0, new ItemStack(Items.GUNPOWDER));
        cauldron.stacks().set(1, new ItemStack(Items.REDSTONE));
        context.assertFalse(cauldron.activate(player), "Dry cauldron must not consume recipe inputs");
        context.assertTrue(!cauldron.stacks().get(0).isEmpty(), "Inputs must remain after dry activation");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        context.setBlockState(3, 1, 3, Blocks.STONE);
        context.assertFalse(cauldron.activate(player), "Unheated cauldron must not cook");
        context.assertTrue(!cauldron.stacks().get(1).isEmpty(), "Inputs must remain without heat");
        context.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        context.assertTrue(cauldron.activate(player), "Gunpowder and redstone must cook");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.FIRE_DUST, 2)), "Result should be fire dust");
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Cooking consumes water");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        cauldron.stacks().set(0, new ItemStack(Items.REDSTONE));
        cauldron.stacks().set(1, new ItemStack(Items.BLAZE_POWDER));
        context.assertTrue(cauldron.activate(player), "Original blaze-powder recipe must still work");
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ModItems.FIRE_DUST)) count += stack.getCount();
        }
        context.assertTrue(count == 4, "Both recipes should yield two each: " + count);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void monocleCanBeCraftedWithoutResearch(TestContext context) {
        CauldronBlockEntity cauldron = cauldron(context);
        PlayerEntity player = fill(context, cauldron);
        player.getInventory().selectedSlot = 8; // Keep test inputs away from the first free output slot.
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_PANE, 2));
        cauldron.add(player, Hand.MAIN_HAND);
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "One pane must be inserted");
        ItemStack named = new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO));
        named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Ordo sample"));
        player.setStackInHand(Hand.MAIN_HAND, named);
        cauldron.add(player, Hand.MAIN_HAND);
        context.assertTrue(cauldron.activate(player), "Pane and Ordo must produce a monocle before research");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.ALCHEMISTS_MONOCLE)), "Monocle reaches inventory");
        context.assertTrue(cauldron.stacks().stream().allMatch(ItemStack::isEmpty), "Ingredients consumed");
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Water consumed");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT)); cauldron.add(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_PANE)); cauldron.add(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO))); cauldron.add(player, Hand.MAIN_HAND);
        context.assertTrue(cauldron.activate(player), "Original gold ingot recipe must also work");
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) if (player.getInventory().getStack(i).isOf(ModItems.ALCHEMISTS_MONOCLE)) count++;
        context.assertTrue(count == 2, "Both recipes must produce one monocle each");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void advancedReagentsAreCraftableBeforeResearch(TestContext context) {
        CauldronBlockEntity cauldron=cauldron(context);
        PlayerEntity player=fill(context,cauldron);
        cauldron.stacks().set(0,new ItemStack(Items.GLASS_BOTTLE));
        cauldron.stacks().set(1,new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS)));
        cauldron.stacks().set(2,new ItemStack(ModItems.CRYSTALS.get(EssenceType.AER)));
        context.assertTrue(cauldron.activate(player), "Lightning bottle must brew without research");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.LIGHTNING_IN_A_BOTTLE)), "Lightning result is delivered");
        context.assertTrue(cauldron.stacks().stream().allMatch(ItemStack::isEmpty), "Recipe consumes exactly its ingredients");
        BlockPos pos=context.getAbsolutePos(new BlockPos(3,2,3));
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Brewing consumes water");
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET)); cauldron.fill(player,Hand.MAIN_HAND);
        cauldron.stacks().set(0,new ItemStack(Items.GLASS_BOTTLE));
        cauldron.stacks().set(1,new ItemStack(ModItems.CAUSTIC_SLIME));
        cauldron.stacks().set(2,new ItemStack(ModItems.CRYSTALS.get(EssenceType.MORTUUS)));
        cauldron.stacks().set(3,new ItemStack(Items.STONE));
        context.assertFalse(cauldron.activate(player), "Extra ingredient is not a recipe");
        context.assertTrue(!cauldron.stacks().get(0).isEmpty() && context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Rejected combination preserves ingredients and water");
        cauldron.stacks().set(3,ItemStack.EMPTY);
        context.assertTrue(cauldron.activate(player), "Miasma must brew without research");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.MIASMA)), "Miasma reaches inventory");
        context.complete();
    }

}
