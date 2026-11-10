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
        context.assertFalse(JarItem.pour(a,empty), "Different essences cannot be combined");
        context.assertTrue(JarItem.contents(a).total() == 3 && JarItem.contents(empty).total() == 2, "Rejected transfer preserves both jars");
        JarItem.set(a, of(EssenceType.VACUUM, 1, .5f));
        context.assertFalse(JarItem.pour(a,empty), "Normal jars reject vacuum");
        context.assertTrue(JarItem.contents(a).total() == 1 && JarItem.contents(empty).total() == 2, "Rejected transfer must not consume items");
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
        context.assertTrue(crystal.getCount() == 2 && jar.contents().total() == 9, "Incompatible crystal must not be consumed");
        ItemStack matching = new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO), 2);
        matching.set(ModDataComponents.ESSENCE_STABILITY, .2f);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, matching);
        context.getWorld().getBlockState(pos).onUseWithItem(matching, context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hitJar);
        context.assertTrue(matching.getCount() == 1 && jar.contents().total() == 10 && jar.contents().contents().size() == 1, "Matching crystal is accepted");
        ItemStack donor = new ItemStack(ModBlocks.JAR); JarItem.set(donor, of(EssenceType.AQUA, 5, .5f));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, donor);
        context.getWorld().getBlockState(pos).onUseWithItem(donor, context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hitJar);
        context.assertTrue(jar.contents().total() == 10 && JarItem.contents(donor).total() == 5, "Incompatible placed-jar pour must not consume anything");
        JarItem.set(donor, of(EssenceType.ORDO, 5, .5f));
        context.getWorld().getBlockState(pos).onUseWithItem(donor, context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hitJar);
        context.assertTrue(jar.contents().total() == 15 && JarItem.contents(donor).total() == 0, "Matching jar can pour into placed jar");
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
        context.assertTrue(donor.getItem().onStackClicked(donor, slot, net.minecraft.util.ClickType.RIGHT, player), "Mismatched right-click is handled without swapping");
        context.assertTrue(JarItem.contents(receiver).total() == 10 && JarItem.contents(donor).total() == 5, "Mismatched inventory pour changes neither jar");
        context.assertTrue(receiver.getItem().onClicked(receiver, donor, slot, net.minecraft.util.ClickType.RIGHT, player, net.minecraft.inventory.StackReference.EMPTY), "Reverse inventory callback also refuses mismatched input");
        context.assertTrue(JarItem.contents(receiver).total() == 10 && JarItem.contents(donor).total() == 5, "Reverse callback changes neither jar");
        JarItem.set(donor, of(EssenceType.ORDO, 5, .5f));
        context.assertTrue(donor.getItem().onStackClicked(donor, slot, net.minecraft.util.ClickType.RIGHT, player), "Matching right-click should pour");
        context.assertTrue(JarItem.contents(receiver).total() == 15 && JarItem.contents(donor).total() == 0, "Matching inventory pour retains both jars");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void appearanceSyncAndLegacyMigration(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.setBlockState(3, 2, 3, ModBlocks.JAR);
        var jar = (JarBlockEntity) context.getWorld().getBlockEntity(pos);
        jar.setContents(of(EssenceType.ORDO, 11, .5f));
        context.assertTrue(context.getWorld().getBlockState(pos).get(net.alkahest.block.custom.JarBlock.LEVEL) == 2, "11 units should show second level");
        var nbt = jar.toInitialChunkDataNbt(context.getWorld().getRegistryManager());
        var clientCopy = new JarBlockEntity(pos, jar.getCachedState());
        clientCopy.readComponentlessNbt(nbt, context.getWorld().getRegistryManager());
        context.assertTrue(clientCopy.contents().equals(jar.contents()), "Initial chunk NBT must sync contents");
        jar.setContents(of(EssenceType.AQUA, 11, .5f));
        var packet = jar.toUpdatePacket();
        context.assertTrue(packet != null && packet.getNbt() != null, "Color-only change has an update packet");
        clientCopy.readComponentlessNbt(packet.getNbt(), context.getWorld().getRegistryManager());
        context.assertTrue(clientCopy.contents().equals(jar.contents()), "Update packet must sync changes even at same level");
        // Simulate the default level of a jar saved in 0.1.12 before this property existed.
        context.getWorld().setBlockState(pos, context.getWorld().getBlockState(pos).with(net.alkahest.block.custom.JarBlock.LEVEL, 0));
        context.runAtTick(5, () -> {
            context.assertTrue(context.getWorld().getBlockState(pos).get(net.alkahest.block.custom.JarBlock.LEVEL) == 2, "Old jars must acquire their display level on load");
            var kept = (JarBlockEntity) context.getWorld().getBlockEntity(pos);
            context.assertTrue(kept.contents().total() == 11, "Migration must retain old contents");
            kept.setContents(JarContentsComponent.EMPTY);
            context.assertTrue(context.getWorld().getBlockState(pos).get(net.alkahest.block.custom.JarBlock.LEVEL) == 0, "Empty jar has no visible fill");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void oldMixedJarIsPreservedAndNotRefilled(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(3,2,3));
        context.setBlockState(3,2,3,ModBlocks.JAR);
        var jar = (JarBlockEntity)context.getWorld().getBlockEntity(pos);
        var old = new net.alkahest.essence.JarContentsComponent(List.of(new net.alkahest.essence.EssenceStack(EssenceType.ORDO,3,.3f),new net.alkahest.essence.EssenceStack(EssenceType.AQUA,4,.7f)));
        jar.setContents(old);
        var saved=jar.createNbt(context.getWorld().getRegistryManager());
        var restored=new JarBlockEntity(pos,jar.getCachedState()); restored.readComponentlessNbt(saved,context.getWorld().getRegistryManager());
        context.assertTrue(restored.contents().equals(old), "Legacy mixed NBT loads unchanged");
        var player=context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var crystal=new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO),2);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,crystal);
        var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos),net.minecraft.util.math.Direction.UP,pos,false);
        context.getWorld().getBlockState(pos).onUseWithItem(crystal,context.getWorld(),player,net.minecraft.util.Hand.MAIN_HAND,hit);
        context.assertTrue(crystal.getCount()==2 && jar.contents().equals(old), "Legacy mixtures cannot be refilled and nothing is lost");
        ItemStack oldItem=new ItemStack(ModBlocks.JAR); JarItem.set(oldItem,old);
        ItemStack empty=new ItemStack(ModBlocks.JAR);
        context.assertFalse(JarItem.pour(oldItem,empty), "Pouring a legacy mixture cannot create a second mixed jar");
        context.assertTrue(JarItem.contents(oldItem).equals(old) && JarItem.contents(empty).total()==0,"Refused legacy pour preserves contents");
        context.getWorld().breakBlock(pos,true);
        context.runAtTick(5,()->{
            var drops=context.getWorld().getEntitiesByClass(ItemEntity.class,new net.minecraft.util.math.Box(pos).expand(2),e->e.getStack().isOf(ModBlocks.JAR.asItem()));
            context.assertTrue(drops.size()==1 && JarItem.contents(drops.getFirst().getStack()).equals(old),"Breaking preserves legacy mixture for the separator");
            context.complete();
        });
    }

}
