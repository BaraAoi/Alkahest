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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void screenInputQuickMoveAndCancellation(TestContext context) {
        context.setBlockState(3,2,3,ModBlocks.DISTILLERY);
        BlockPos pos=context.getAbsolutePos(new BlockPos(3,2,3));
        var distillery=(DistilleryBlockEntity)context.getWorld().getBlockEntity(pos);
        var player=context.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(pos.getX()+2,pos.getY(),pos.getZ());
        var handler=(net.alkahest.screen.DistilleryScreenHandler)distillery.createMenu(1,player.getInventory(),player);
        context.assertTrue(handler.canUse(player), "Nearby player can use the real block inventory");
        context.assertFalse(handler.getSlot(0).canInsert(new ItemStack(Items.STONE)), "Inorganic input is rejected");
        context.assertTrue(handler.getSlot(0).canInsert(new ItemStack(Items.APPLE)) && handler.getSlot(0).getMaxItemCount()==1, "Only one valid input per slot");
        context.assertFalse(handler.getSlot(1).canInsert(new ItemStack(Items.APPLE)), "Output must never accept items");
        player.getInventory().setStack(9,new ItemStack(Items.APPLE,8));
        context.assertFalse(handler.quickMove(player,2).isEmpty(), "Shift-click from player inventory inserts input");
        context.assertTrue(distillery.getStack(0).getCount()==1 && player.getInventory().getStack(9).getCount()==7, "Only one ingredient moves without losing the rest");
        context.assertTrue(handler.quickMove(player,2).isEmpty(), "An occupied input cannot accept more via shift-click");
        context.assertTrue(distillery.getStack(0).getCount()==1 && player.getInventory().getStack(9).getCount()==7, "Repeated shift-click cannot overfill input");
        for(int i=0;i<37;i++) DistilleryBlockEntity.tick(context.getWorld(),pos,distillery);
        context.assertTrue(handler.progress()==37 && handler.maxProgress()==1200, "Screen properties expose live progress and duration");
        context.assertFalse(handler.quickMove(player,0).isEmpty(), "Shift-click removes the input");
        context.assertTrue(distillery.getStack(0).isEmpty() && handler.progress()==0, "Removing input resets progress");
        player.getInventory().setStack(9,new ItemStack(Items.STONE,2));
        context.assertTrue(handler.quickMove(player,2).isEmpty() && player.getInventory().getStack(9).getCount()==2, "Inorganic shift-click leaves inventory unchanged");
        distillery.setStack(1,new ItemStack(ModItems.ASH,3));
        context.assertFalse(handler.quickMove(player,1).isEmpty(), "Shift-click extracts output");
        context.assertTrue(distillery.getStack(1).isEmpty() && player.getInventory().contains(new ItemStack(ModItems.ASH,3)), "Output is moved intact");
        player.setPosition(pos.getX()+20,pos.getY(),pos.getZ());
        context.assertFalse(handler.canUse(player), "Distant player cannot keep using screen");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hopperRulesAndExistingStacksAreSafe(TestContext context) {
        context.setBlockState(3,2,3,ModBlocks.DISTILLERY);
        BlockPos pos=context.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)context.getWorld().getBlockEntity(pos);
        context.assertTrue(d.canInsert(0,new ItemStack(Items.APPLE),net.minecraft.util.math.Direction.UP), "Hopper above may insert organic input");
        context.assertFalse(d.canInsert(0,new ItemStack(Items.STONE),net.minecraft.util.math.Direction.UP), "Hopper must reject stone");
        context.assertFalse(d.canInsert(1,new ItemStack(Items.APPLE),net.minecraft.util.math.Direction.DOWN), "Hopper cannot insert output");
        context.assertFalse(d.canExtract(0,new ItemStack(Items.APPLE),net.minecraft.util.math.Direction.DOWN), "Hopper cannot remove input");
        d.setStack(0,new ItemStack(Items.APPLE,3));
        context.assertFalse(d.canInsert(0,new ItemStack(Items.APPLE),net.minecraft.util.math.Direction.UP), "Hopper cannot add to occupied input");
        var nbt=d.createNbt(context.getWorld().getRegistryManager()); nbt.putInt("Progress",1199);
        d.readComponentlessNbt(nbt,context.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(context.getWorld(),pos,d);
        context.assertTrue(d.getStack(0).getCount()==2 && !d.getStack(1).isEmpty(), "A larger legacy or external input consumes one item, not the entire stack");
        context.assertTrue(d.canExtract(1,d.getStack(1),net.minecraft.util.math.Direction.DOWN), "Hopper can extract output");
        context.complete();
    }

}
