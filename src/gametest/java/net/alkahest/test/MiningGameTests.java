package net.alkahest.test;

import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

public class MiningGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void chargerCanBeTargetedAndMinedInSurvival(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.JAR); c.setBlockState(3,3,3,ModBlocks.ESSENCE_CHARGER);
        BlockPos p=c.getAbsolutePos(new BlockPos(3,3,3));
        var hit=c.getWorld().raycast(new RaycastContext(new Vec3d(p.getX()-1,p.getY()+.2,p.getZ()+.5),new Vec3d(p.getX()+.5,p.getY()+.2,p.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,c.createMockPlayer(GameMode.SURVIVAL)));
        c.assertTrue(hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(p),"Visible charger must be targetable, not hidden inside jar");
        var player=c.createMockPlayer(GameMode.SURVIVAL); var state=c.getWorld().getBlockState(p);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        c.assertTrue(player.getMainHandStack().isSuitableFor(state) && player.canHarvest(state),"An iron pickaxe must harvest the charger");
        float speed=state.calcBlockBreakingDelta(player,c.getWorld(),p);
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        c.assertTrue(speed>state.calcBlockBreakingDelta(player,c.getWorld(),p),"Pickaxe must mine faster than a bare hand");
        var drops=Block.getDroppedStacks(state,c.getWorld(),p,c.getWorld().getBlockEntity(p),player,new ItemStack(Items.IRON_PICKAXE));
        c.assertTrue(drops.stream().anyMatch(s->s.isOf(ModBlocks.ESSENCE_CHARGER.asItem())),"Charger loot table must return the block");
        c.complete();
    }
}
