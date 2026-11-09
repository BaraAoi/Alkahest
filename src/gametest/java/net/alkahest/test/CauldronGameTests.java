package net.alkahest.test;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.registry.ModBlocks;
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

}
