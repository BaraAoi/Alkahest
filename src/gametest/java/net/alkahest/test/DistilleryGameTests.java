package net.alkahest.test;

import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public class DistilleryGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rawDistillationPersistsAndProducesOutput(TestContext context) {
        context.setBlockState(3, 2, 3, ModBlocks.DISTILLERY);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        var distillery = (DistilleryBlockEntity) context.getWorld().getBlockEntity(pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
        distillery.insert(player, Hand.MAIN_HAND);
        context.assertTrue(distillery.stacks().get(0).isEmpty(), "Inorganic stone must be rejected");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).isOf(Items.STONE), "Rejected input remains with player");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        distillery.insert(player, Hand.MAIN_HAND);
        context.assertTrue(distillery.stacks().get(0).isOf(Items.APPLE), "Organic apple must be accepted");
        context.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getCount() == 1, "Exactly one input is consumed");
        var registry = context.getWorld().getRegistryManager();
        var saved = distillery.createNbt(registry);
        saved.putInt("Progress", 1199);
        distillery.readComponentlessNbt(saved, registry);
        context.assertTrue(distillery.createNbt(registry).getInt("Progress") == 1199, "Progress must survive NBT round trip");
        DistilleryBlockEntity.tick(context.getWorld(), pos, distillery);
        context.assertTrue(distillery.stacks().get(0).isEmpty(), "Input must be consumed at completion");
        ItemStack result = distillery.stacks().get(1).copy();
        context.assertTrue(!result.isEmpty(), "Distillation must produce a result");
        boolean ash = result.isOf(ModItems.ASH);
        boolean crystal = ModItems.CRYSTALS.containsValue(result.getItem());
        context.assertTrue(ash || crystal, "Raw distillation produces only ash or a crystal");
        if (crystal) {
            Float stability = result.get(ModDataComponents.ESSENCE_STABILITY);
            context.assertTrue(result.getCount() >= 1 && result.getCount() <= 3, "Crystal output must be 1–3");
            context.assertTrue(stability != null && stability >= .15f && stability <= .30f, "Crystal stability must be in the specified range");
        }
        distillery.collect(player);
        context.assertTrue(distillery.stacks().get(1).isEmpty(), "Collecting must clear the output");
        context.assertTrue(player.getInventory().contains(result), "The result must reach the player");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void organicPotentiaSourceIsDistillable(TestContext context) {
        context.setBlockState(3, 2, 3, ModBlocks.DISTILLERY);
        var distillery = (DistilleryBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(new BlockPos(3, 2, 3)));
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLOW_BERRIES));
        distillery.insert(player, Hand.MAIN_HAND);
        context.assertTrue(!distillery.stacks().get(0).isEmpty(), "Glow berries must be accepted as organic input");
        context.assertTrue(net.alkahest.essence.ItemEssenceMap.get(distillery.stacks().get(0)).stream().anyMatch(e -> e.type() == net.alkahest.essence.EssenceType.POTENTIA), "Input must be able to produce Potentia");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rawChickenCanYieldAer(TestContext context) {
        context.setBlockState(3, 2, 3, ModBlocks.DISTILLERY);
        var distillery = (DistilleryBlockEntity)context.getWorld().getBlockEntity(context.getAbsolutePos(new BlockPos(3,2,3)));
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CHICKEN));
        distillery.insert(player, Hand.MAIN_HAND);
        context.assertTrue(!distillery.stacks().get(0).isEmpty(), "Raw chicken is accepted as organic matter");
        context.assertTrue(net.alkahest.essence.ItemEssenceMap.get(distillery.stacks().get(0)).stream().anyMatch(e -> e.type() == net.alkahest.essence.EssenceType.AER), "Aer is a possible crystal output");
        context.complete();
    }

}
