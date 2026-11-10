package net.alkahest.test;

import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public class JarGameTests implements FabricGameTest {
    private static JarContentsComponent of(EssenceType type, int amount, float stability) {
        return new JarContentsComponent(List.of(new EssenceStack(type, amount, stability)));
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pourAndOverflow(TestContext context) {
        ItemStack a = new ItemStack(ModBlocks.JAR), b = new ItemStack(ModBlocks.JAR);
        JarItem.set(a, of(EssenceType.ORDO, 80, .25f)); JarItem.set(b, of(EssenceType.ORDO, 30, .75f));
        context.assertTrue(JarItem.pour(a,b), "Jar transfer should succeed");
        var result = JarItem.contents(b).contents().get(0);
        context.assertTrue(result.amount() == 100 && Math.abs(result.stability() - .40f) < .0001, "Only accepted 70 units affect weighted stability");
        context.assertTrue(JarItem.contents(a).total() == 0, "Source jar must remain and become empty");
        JarItem.set(a, of(EssenceType.IGNIS, 2, .4f));
        ItemStack empty = new ItemStack(ModBlocks.JAR);
        context.assertTrue(JarItem.pour(a,empty), "First pour");
        JarItem.set(a, of(EssenceType.AQUA, 3, .5f));
        context.assertTrue(JarItem.pour(a,empty) && JarItem.contents(empty).contents().size() == 2, "Different essences form a mixture");
        JarItem.set(a, of(EssenceType.VACUUM, 1, .5f));
        context.assertFalse(JarItem.pour(a,empty), "Normal jars reject vacuum");
        context.assertTrue(JarItem.contents(a).total() == 1 && JarItem.contents(empty).total() == 5, "Rejected transfer must not consume items");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void jarPersistsAndDropsItsContents(TestContext context) {
        BlockPos relative = new BlockPos(3, 2, 3), pos = context.getAbsolutePos(relative);
        context.setBlockState(relative, ModBlocks.JAR);
        var jar = (JarBlockEntity) context.getWorld().getBlockEntity(pos);
        var expected = of(EssenceType.ORDO, 42, .32f);
        jar.setContents(expected);
        var saved = jar.createNbt(context.getWorld().getRegistryManager());
        var restored = new JarBlockEntity(pos, jar.getCachedState());
        restored.readComponentlessNbt(saved, context.getWorld().getRegistryManager());
        context.assertTrue(restored.contents().equals(expected), "World save and load preserve contents");
        ItemStack placed = new ItemStack(ModBlocks.JAR); JarItem.set(placed, expected);
        restored.readComponents(placed);
        context.assertTrue(restored.contents().equals(expected), "Placement applies item component");
        context.getWorld().breakBlock(pos, true);
        context.runAtTick(5, () -> {
            var entities = context.getWorld().getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(pos).expand(2), e -> e.getStack().isOf(ModBlocks.JAR.asItem()));
            context.assertTrue(entities.size() == 1 && JarItem.contents(entities.get(0).getStack()).equals(expected), "Breaking the jar must preserve contents on the drop");
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void playerPlacementAndFilling(TestContext context) {
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        BlockPos base = context.getAbsolutePos(new BlockPos(4, 1, 4));
        context.getWorld().setBlockState(base, net.minecraft.block.Blocks.STONE.getDefaultState());
        ItemStack placed = new ItemStack(ModBlocks.JAR);
        JarItem.set(placed, of(EssenceType.ORDO, 9, .3f));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, placed);
        var hitBase = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(base), net.minecraft.util.math.Direction.UP, base, false);
        var place = new net.minecraft.item.ItemPlacementContext(player, net.minecraft.util.Hand.MAIN_HAND, placed, hitBase);
        context.assertTrue(((JarItem) ModBlocks.JAR.asItem()).place(place).isAccepted(), "Normal block-item placement should work");
        BlockPos pos = base.up();
        var jar = (JarBlockEntity) context.getWorld().getBlockEntity(pos);
        context.assertTrue(jar != null && jar.contents().total() == 9, "Placed jar should retain its item component");
        ItemStack crystal = new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS), 2);
        crystal.set(ModDataComponents.ESSENCE_STABILITY, .2f);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, crystal);
        var hitJar = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        context.getWorld().getBlockState(pos).onUseWithItem(crystal, context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hitJar);
        context.assertTrue(crystal.getCount() == 1 && jar.contents().total() == 10 && jar.contents().contents().size() == 2, "Using a crystal adds one essence and preserves the rest");
        ItemStack donor = new ItemStack(ModBlocks.JAR); JarItem.set(donor, of(EssenceType.AQUA, 5, .5f));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, donor);
        context.getWorld().getBlockState(pos).onUseWithItem(donor, context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hitJar);
        context.assertTrue(jar.contents().total() == 15 && JarItem.contents(donor).total() == 0, "Using a jar on a placed jar pours into it");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void playerInventoryPour(TestContext context) {
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var receiver = new ItemStack(ModBlocks.JAR); JarItem.set(receiver, of(EssenceType.ORDO, 10, .5f));
        var donor = new ItemStack(ModBlocks.JAR); JarItem.set(donor, of(EssenceType.IGNIS, 5, .5f));
        player.getInventory().setStack(0, receiver);
        var slot = new net.minecraft.screen.slot.Slot(player.getInventory(), 0, 0, 0);
        context.assertFalse(donor.getItem().onStackClicked(donor, slot, net.minecraft.util.ClickType.LEFT, player), "Left click must not pour");
        context.assertTrue(donor.getItem().onStackClicked(donor, slot, net.minecraft.util.ClickType.RIGHT, player), "Right-click should pour");
        context.assertTrue(JarItem.contents(receiver).total() == 15 && JarItem.contents(donor).total() == 0, "Inventory pour must retain jars and contents");
        context.complete();
    }

}
