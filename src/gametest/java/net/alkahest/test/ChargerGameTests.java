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
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placementAndNearbySelection(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.setBlockState(3, 2, 3, ModBlocks.ESSENCE_CHARGER);
        // A mock player is not tracked by the world; inject it into the selection scan.
        var player = context.createMockPlayer(GameMode.CREATIVE);
        player.setPosition(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
        ItemStack jarItem = new ItemStack(ModBlocks.JAR);
        JarItem.set(jarItem, new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 3, .5f))));
        player.setStackInHand(Hand.MAIN_HAND, jarItem);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.NORTH, pos, false);
        context.getWorld().getBlockState(pos).onUseWithItem(jarItem, context.getWorld(), player, Hand.MAIN_HAND, hit);
        var placed = context.getWorld().getBlockEntity(pos.up());
        context.assertTrue(placed instanceof JarBlockEntity, "Right click with jar places it above charger");
        var jar = (JarBlockEntity) placed;
        context.assertTrue(jar.contents().total() == 3, "Placed jar retains contents");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        wand.set(ModDataComponents.ESSENCE_CHARGE, 60); player.getInventory().setStack(0, wand);
        var result = EssenceChargerBlockEntity.chargeNearby(jar, pos, List.of(player));
        context.assertTrue(result != null && result.player() == player && result.essence() == EssenceType.ORDO, "Nearby scan selects a matching player and type");
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 70 && jar.contents().total() == 2, "Scan charges a wand and consumes one unit");
        player.setPosition(pos.getX() + 48.5, pos.getY(), pos.getZ());
        context.assertTrue(EssenceChargerBlockEntity.chargeNearby(jar, pos, List.of(player)) == null, "Player beyond the cube is ignored");
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 70 && jar.contents().total() == 2, "Out of range must not consume essence");
        context.getWorld().breakBlock(pos, true);
        context.assertTrue(context.getWorld().getBlockEntity(pos.up()) == placed, "Breaking charger must not destroy or duplicate its jar");
        context.complete();
    }

}
