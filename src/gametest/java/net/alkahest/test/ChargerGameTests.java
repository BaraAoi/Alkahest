package net.alkahest.test;

import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.List;

public class ChargerGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void preciseChargeAndRejections(TestContext context) {
        context.setBlockState(3, 3, 3, ModBlocks.JAR);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 3, 3));
        var jar = (JarBlockEntity) context.getWorld().getBlockEntity(pos);
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 2, .25f), new EssenceStack(EssenceType.AQUA, 3, .4f))));
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        var wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        wand.set(ModDataComponents.ESSENCE_CHARGE, 85); player.getInventory().setStack(0, wand);
        context.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, player) == EssenceType.ORDO, "Matching wand must charge");
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 95 && jar.contents().total() == 4, "One unit restores ten");
        EssenceChargerBlockEntity.chargeFirst(jar, player);
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 100 && jar.contents().total() == 3, "Charge is capped and consumes one unit");
        context.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, player) == null && jar.contents().total() == 3, "Full wand must not consume essence");
        wand.set(ModDataComponents.ESSENCE_CHARGE, 50);
        context.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, player) == null && jar.contents().total() == 3, "Wrong essence must not be consumed");
        var aqua = jar.contents().contents().get(0);
        context.assertTrue(aqua.type() == EssenceType.AQUA && Math.abs(aqua.stability() - .4f) < .001, "Other essence and stability are preserved");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void actualPlacementAndWorldCharging(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.setBlockState(3, 2, 3, ModBlocks.ESSENCE_CHARGER);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
        ItemStack jarItem = new ItemStack(ModBlocks.JAR);
        JarItem.set(jarItem, new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 3, .5f))));
        player.setStackInHand(Hand.MAIN_HAND, jarItem);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.NORTH, pos, false);
        context.getWorld().getBlockState(pos).onUseWithItem(jarItem, context.getWorld(), player, Hand.MAIN_HAND, hit);
        var placed = context.getWorld().getBlockEntity(pos.up());
        context.assertTrue(placed instanceof JarBlockEntity, "Right click with jar places it above charger");
        context.assertTrue(((JarBlockEntity)placed).contents().total() == 3, "Placed jar retains its contents");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        wand.set(ModDataComponents.ESSENCE_CHARGE, 60); player.getInventory().setStack(0, wand);
        context.runAtTick(25, () -> {
            context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) >= 70, "World ticker finds nearby player and charges wand");
            context.assertTrue(((JarBlockEntity)placed).contents().total() < 3, "Charging consumes matching essence");
            int charge = wand.get(ModDataComponents.ESSENCE_CHARGE);
            int remaining = ((JarBlockEntity)placed).contents().total();
            player.setPosition(pos.getX() + 48.5, pos.getY(), pos.getZ());
            context.runAtTick(55, () -> {
                context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == charge && ((JarBlockEntity)placed).contents().total() == remaining, "Player beyond the cube must not charge or consume essence");
                context.getWorld().breakBlock(pos, true);
                context.assertTrue(context.getWorld().getBlockEntity(pos.up()) == placed, "Breaking charger must not destroy or duplicate its jar");
                context.complete();
            });
        });
    }
}
