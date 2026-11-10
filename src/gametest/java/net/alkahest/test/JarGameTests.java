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
        context.assertTrue(result.amount() == 100 && Math.abs(result.stability() - .65f) < .0001, "Only accepted 70 units affect weighted stability");
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
}
