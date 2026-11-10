package net.alkahest.test;

import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.alkahest.screen.DistilleryScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.List;
import java.util.Set;
import static net.alkahest.essence.EssenceType.*;

public class DistilleryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void primalCandidates(TestContext c) {
        c.assertTrue(Set.copyOf(DistilleryBlockEntity.primaryCandidates(ItemEssenceMap.get(new ItemStack(Items.APPLE)))).equals(Set.of(TERRA, AQUA, ORDO)), "Apple must resolve Herba and direct primaries");
        c.assertTrue(Set.copyOf(DistilleryBlockEntity.primaryCandidates(ItemEssenceMap.get(new ItemStack(Items.GLOW_BERRIES)))).equals(Set.of(TERRA,AQUA,IGNIS,AER,ORDO)), "Glow berries resolve Lux, Potentia and Herba");
        var nested = List.of(new EssenceStack(SPIRITUS,1,1),new EssenceStack(VACUUM,1,1));
        c.assertTrue(Set.copyOf(DistilleryBlockEntity.primaryCandidates(nested)).equals(Set.of(PERDITIO,AQUA,ORDO)), "Spiritus recursively resolves Mortuus; Vacuum is not a primal");
        for (var t : EssenceType.values()) for (var base : DistilleryBlockEntity.primaryCandidates(List.of(new EssenceStack(t,1,1))))
            c.assertTrue(base.tier()==EssenceType.Tier.PRIMA, "All resolved essences must be primary");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void stackedInputAndProcessing(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        var p=c.createMockPlayer(GameMode.SURVIVAL); p.setPosition(pos.getX()+1,pos.getY(),pos.getZ());
        var h=(DistilleryScreenHandler)d.createMenu(1,p.getInventory(),p);
        c.assertTrue(h.canUse(p), "Nearby player can use interface");
        c.assertFalse(h.getSlot(0).canInsert(new ItemStack(Items.STONE)), "Stone rejected");
        c.assertTrue(h.getSlot(0).canInsert(new ItemStack(Items.APPLE)) && h.getSlot(0).getMaxItemCount()>1, "Organic input accepts a stack");
        for(int i=1;i<8;i++) c.assertFalse(h.getSlot(i).canInsert(new ItemStack(Items.APPLE)), "Outputs reject insertion");
        p.getInventory().setStack(9,new ItemStack(Items.APPLE,8));
        c.assertFalse(h.quickMove(p,8).isEmpty(), "Shift-click loads the input stack");
        c.assertTrue(d.getStack(0).getCount()==8 && p.getInventory().getStack(9).isEmpty(), "All eight moved without loss");
        for(int i=0;i<37;i++) DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(h.progress()==37 && h.maxProgress()==1200, "Progress visible to screen");
        c.assertFalse(h.quickMove(p,0).isEmpty(), "Shift-click can take input back");
        c.assertTrue(d.getStack(0).isEmpty() && h.progress()==0, "Taking input cancels progress");
        d.setStack(0,new ItemStack(Items.APPLE,3));
        var nbt=d.createNbt(c.getWorld().getRegistryManager()); nbt.putInt("Progress",1199);
        d.readComponentlessNbt(nbt,c.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(0).getCount()==2 && !d.getStack(1).isEmpty(), "First minute consumes one item");
        ItemStack out=d.getStack(1);
        if (!out.isOf(ModItems.ASH)) {
            c.assertTrue(List.of(TERRA,AQUA,ORDO).stream().anyMatch(t -> out.isOf(ModItems.CRYSTALS.get(t))), "Only apple's primal components may be produced");
            float stability=out.get(ModDataComponents.ESSENCE_STABILITY);
            c.assertTrue(stability>=.15f && stability<=.30f, "Crystal stability remains low");
        }
        nbt=d.createNbt(c.getWorld().getRegistryManager()); nbt.putInt("Progress",1199); d.readComponentlessNbt(nbt,c.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(0).getCount()==1, "Next item continues without taking the previous output");
        p.setPosition(pos.getX()+20,pos.getY(),pos.getZ()); c.assertFalse(h.canUse(p), "Remote player cannot use interface");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedOutputPersistsAndRecovers(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        d.setStack(0,new ItemStack(Items.APPLE,2));
        for(int i=1;i<8;i++) d.setStack(i,new ItemStack(Items.STONE,64));
        var nbt=d.createNbt(c.getWorld().getRegistryManager()); nbt.putInt("Progress",1199); d.readComponentlessNbt(nbt,c.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(0).getCount()==1 && !d.getStack(8).isEmpty(), "Blocked product is stored, not lost or rerolled");
        ItemStack pending=d.getStack(8).copy();
        var saved=d.createNbt(c.getWorld().getRegistryManager());
        var restored=new DistilleryBlockEntity(pos,d.getCachedState()); restored.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        c.assertTrue(ItemStack.areEqual(pending,restored.getStack(8)), "Pending result survives save/load");
        DistilleryBlockEntity.tick(c.getWorld(),pos,d); c.assertTrue(ItemStack.areEqual(pending,d.getStack(8)) && d.getStack(0).getCount()==1, "Blocked machine waits without consuming or rerolling");
        d.removeStack(3); DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(8).isEmpty() && ItemStack.areEqual(pending,d.getStack(3)), "Unblocking deposits the same saved result");
        c.assertFalse(d.canExtract(8,pending,Direction.DOWN), "Hoppers cannot access pending slot");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hopperRulesAndMerge(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        c.assertTrue(d.canInsert(0,new ItemStack(Items.APPLE),Direction.UP), "Hopper above can insert");
        c.assertFalse(d.canInsert(0,new ItemStack(Items.STONE),Direction.UP), "Hopper rejects stone");
        c.assertFalse(d.canInsert(0,new ItemStack(Items.APPLE),Direction.NORTH), "Side cannot insert");
        d.setStack(0,new ItemStack(Items.APPLE,3));
        c.assertTrue(d.canInsert(0,new ItemStack(Items.APPLE),Direction.UP), "Hopper can add to input stack");
        c.assertFalse(d.canExtract(0,new ItemStack(Items.APPLE),Direction.DOWN), "Hopper cannot extract input");
        c.assertTrue(d.canExtract(7,new ItemStack(ModItems.ASH),Direction.DOWN), "Last output can be extracted");
        ItemStack oldCrystal=new ItemStack(ModItems.CRYSTALS.get(AQUA),3); oldCrystal.set(ModDataComponents.ESSENCE_STABILITY,.18f);
        ItemStack newCrystal=new ItemStack(ModItems.CRYSTALS.get(AQUA),2); newCrystal.set(ModDataComponents.ESSENCE_STABILITY,.28f);
        d.setStack(1,oldCrystal); d.setStack(8,newCrystal); DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(1).getCount()==5 && d.getStack(8).isEmpty() && Math.abs(d.getStack(1).get(ModDataComponents.ESSENCE_STABILITY)-.22f)<.0001f, "Merged crystals retain weighted mean stability");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void compositeCrystalsHaveWorkingRecipes(TestContext c) {
        for (var type : EssenceType.values()) {
            if (type.tier()!=EssenceType.Tier.COMPOSITA) continue;
            var a=new ItemStack(ModItems.CRYSTALS.get(type.parents().get(0)));
            var b=new ItemStack(ModItems.CRYSTALS.get(type.parents().get(1)));
            a.set(ModDataComponents.ESSENCE_STABILITY,.23f); b.set(ModDataComponents.ESSENCE_STABILITY,.28f);
            var input=net.minecraft.recipe.input.CraftingRecipeInput.create(2,1,List.of(a,b));
            var recipe=c.getWorld().getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,c.getWorld());
            c.assertTrue(recipe.isPresent(), "Composite crystal recipe loaded: " + type);
            var result=recipe.get().value().craft(input,c.getWorld().getRegistryManager());
            c.assertTrue(result.isOf(ModItems.CRYSTALS.get(type)) && result.getCount()==1, "Correct composite crystal: " + type);
            c.assertTrue(result.get(ModDataComponents.ESSENCE_STABILITY)==.15f, "Crafting does not grant full stability");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allInteractionsOpenScreen(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var player=c.createMockCreativeServerPlayerInWorld(); player.setPosition(pos.getX()+1,pos.getY(),pos.getZ());
        var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
        var held=new ItemStack(Items.APPLE,5); player.setStackInHand(Hand.MAIN_HAND,held);
        c.getWorld().getBlockState(pos).onUseWithItem(held,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(player.currentScreenHandler instanceof DistilleryScreenHandler && held.getCount()==5, "Item click opens screen without consuming held item");
        player.closeHandledScreen(); player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        c.getWorld().getBlockState(pos).onUse(c.getWorld(),player,hit);
        c.assertTrue(player.currentScreenHandler instanceof DistilleryScreenHandler, "Empty click opens screen");
        player.closeHandledScreen(); player.setSneaking(true); player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.APPLE));
        var action=UseBlockCallback.EVENT.invoker().interact(player,c.getWorld(),Hand.MAIN_HAND,hit);
        c.assertTrue(action.isAccepted() && player.currentScreenHandler instanceof DistilleryScreenHandler, "Sneaking with an item also opens screen via callback");
        c.complete();
    }
}
