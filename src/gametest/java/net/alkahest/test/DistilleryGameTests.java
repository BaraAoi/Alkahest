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
        c.assertTrue(h.status()==0, "Empty machine reports idle to screen");
        c.assertTrue(h.getSlot(0).x==80 && h.getSlot(0).y==31, "Input hitbox matches the decorated frame");
        for(int i=1;i<8;i++) c.assertTrue(h.getSlot(i).x==26+(i-1)*18 && h.getSlot(i).y==96, "Output hitbox matches its frame");
        c.assertTrue(h.getSlot(8).x==8 && h.getSlot(8).y==132 && h.getSlot(35).y==190, "Inventory and hotbar match their frames");
        var clientProperties=new net.minecraft.screen.ArrayPropertyDelegate(3);
        var mirror=new DistilleryScreenHandler(2,p.getInventory(),new net.minecraft.inventory.SimpleInventory(DistilleryBlockEntity.SIZE),clientProperties);
        clientProperties.set(0,600); clientProperties.set(1,1200); clientProperties.set(2,2);
        c.assertTrue(mirror.progress()==600 && mirror.maxProgress()==1200 && mirror.status()==2, "Client property delegate exposes synced progress and state");
        c.assertFalse(h.getSlot(0).canInsert(new ItemStack(Items.STONE)), "Stone rejected");
        c.assertTrue(h.getSlot(0).canInsert(new ItemStack(Items.APPLE)) && h.getSlot(0).getMaxItemCount()>1, "Organic input accepts a stack");
        for(int i=1;i<8;i++) c.assertFalse(h.getSlot(i).canInsert(new ItemStack(Items.APPLE)), "Outputs reject insertion");
        p.getInventory().setStack(9,new ItemStack(Items.APPLE,8));
        c.assertFalse(h.quickMove(p,8).isEmpty(), "Shift-click loads the input stack");
        c.assertTrue(d.getStack(0).getCount()==8 && p.getInventory().getStack(9).isEmpty(), "All eight moved without loss");
        c.assertTrue(h.status()==1, "Loaded machine reports working to screen");
        for(int i=0;i<37;i++) DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(h.progress()==37 && h.maxProgress()==1200, "Progress visible to screen");
        c.assertFalse(h.quickMove(p,0).isEmpty(), "Shift-click can take input back");
        c.assertTrue(d.getStack(0).isEmpty() && h.progress()==0 && h.status()==0, "Taking input cancels progress and returns to idle");
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
        var p=c.createMockPlayer(GameMode.SURVIVAL);
        var screen=(DistilleryScreenHandler)d.createMenu(1,p.getInventory(),p);
        d.setStack(0,new ItemStack(Items.APPLE,2));
        for(int i=1;i<8;i++) d.setStack(i,new ItemStack(Items.STONE,64));
        var nbt=d.createNbt(c.getWorld().getRegistryManager()); nbt.putInt("Progress",1199); d.readComponentlessNbt(nbt,c.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(0).getCount()==1 && !d.getStack(8).isEmpty(), "Blocked product is stored, not lost or rerolled");
        c.assertTrue(screen.status()==2, "Blocked output is reported to screen");
        ItemStack pending=d.getStack(8).copy();
        var saved=d.createNbt(c.getWorld().getRegistryManager());
        var restored=new DistilleryBlockEntity(pos,d.getCachedState()); restored.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        c.assertTrue(ItemStack.areEqual(pending,restored.getStack(8)), "Pending result survives save/load");
        DistilleryBlockEntity.tick(c.getWorld(),pos,d); c.assertTrue(ItemStack.areEqual(pending,d.getStack(8)) && d.getStack(0).getCount()==1, "Blocked machine waits without consuming or rerolling");
        d.removeStack(3); DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.getStack(8).isEmpty() && ItemStack.areEqual(pending,d.getStack(3)), "Unblocking deposits the same saved result");
        c.assertTrue(screen.status()==1, "Screen returns to working after unblocking");
        c.assertFalse(d.canExtract(8,pending,Direction.DOWN), "Hoppers cannot access pending slot");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=80)
    public void actualHoppersReplaceSpentCatalystAndCollectOutput(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); var world=c.getWorld();
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)world.getBlockEntity(pos);
        BlockPos side=pos.west(), bottom=pos.down();
        world.setBlockState(side,net.minecraft.block.Blocks.HOPPER.getDefaultState().with(net.minecraft.block.HopperBlock.FACING,Direction.EAST));
        world.setBlockState(bottom,net.minecraft.block.Blocks.HOPPER.getDefaultState());
        var feeder=(net.minecraft.block.entity.HopperBlockEntity)world.getBlockEntity(side);
        var collector=(net.minecraft.block.entity.HopperBlockEntity)world.getBlockEntity(bottom);
        d.setStack(9,new ItemStack(Items.GLASS_BOTTLE));
        feeder.setStack(0,new ItemStack(ModItems.ALKAHEST_VIAL));
        d.setStack(1,new ItemStack(ModItems.ASH));
        c.runAtTick(35,()->{
            c.assertTrue(d.getStack(9).isOf(ModItems.ALKAHEST_VIAL), "Side hopper installs replacement catalyst once bottle is removed");
            c.assertTrue(collector.getStack(0).isOf(ModItems.ASH) && collector.getStack(1).isOf(Items.GLASS_BOTTLE), "Bottom hopper collects output and spent bottle");
            c.assertFalse(d.canExtract(9,d.getStack(9),Direction.DOWN), "Active catalyst cannot be extracted");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=100)
    public void sideHopperSwitchesModeDuringRawCycleWithoutPrematureOutput(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); var world=c.getWorld();
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)world.getBlockEntity(pos);
        d.setStack(0,new ItemStack(Items.APPLE,3));
        var nbt=d.createNbt(world.getRegistryManager()); nbt.putInt("Progress",900);
        d.readComponentlessNbt(nbt,world.getRegistryManager());
        BlockPos side=pos.west();
        world.setBlockState(side,net.minecraft.block.Blocks.HOPPER.getDefaultState().with(net.minecraft.block.HopperBlock.FACING,Direction.EAST));
        var hopper=(net.minecraft.block.entity.HopperBlockEntity)world.getBlockEntity(side);
        hopper.setStack(0,new ItemStack(ModItems.ALKAHEST_VIAL));
        var p=c.createMockPlayer(GameMode.SURVIVAL);
        var screen=(DistilleryScreenHandler)d.createMenu(1,p.getInventory(),p);
        c.runAtTick(30,()->{
            c.assertTrue(d.hasAlkahest() && hopper.isEmpty(),"Real hopper can install catalyst during continuous raw processing");
            c.assertTrue(d.getStack(0).getCount()==3 && d.getStack(1).isEmpty(),"No instant product or consumption when old progress exceeded the solvent duration");
            c.assertTrue(screen.progress() > 0 && screen.progress() < 40 && screen.maxProgress()==100,"New mode started its own cycle");
            c.assertTrue(net.alkahest.item.custom.AlkahestVialItem.charges(d.getStack(DistilleryBlockEntity.CATALYST))==32,"No catalyst charge was spent early");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void changingModesRestartsProgressEvenForOrganicInputs(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); var world=c.getWorld();
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3)); var d=(DistilleryBlockEntity)world.getBlockEntity(pos);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var screen=(DistilleryScreenHandler)d.createMenu(1,player.getInventory(),player);
        d.setStack(0,new ItemStack(Items.APPLE,4));
        for(int i=0;i<300;i++) DistilleryBlockEntity.tick(world,pos,d);
        c.assertTrue(screen.progress()==300,"Raw cycle has accumulated progress");
        d.setStack(DistilleryBlockEntity.CATALYST,new ItemStack(ModItems.ALKAHEST_VIAL));
        c.assertTrue(screen.progress()==0 && screen.maxProgress()==100,"Installing catalyst resets raw progress");
        for(int i=0;i<99;i++) DistilleryBlockEntity.tick(world,pos,d);
        c.assertTrue(d.getStack(0).getCount()==4 && screen.progress()==99,"A full solvent cycle is required");
        DistilleryBlockEntity.tick(world,pos,d);
        c.assertTrue(d.getStack(0).getCount()==3 && net.alkahest.item.custom.AlkahestVialItem.charges(d.getStack(9))==31,"Exactly one item and one charge are spent");
        for(int i=0;i<40;i++) DistilleryBlockEntity.tick(world,pos,d);
        c.assertTrue(screen.progress()==40,"Next solvent cycle started");
        c.assertFalse(screen.quickMove(player,DistilleryScreenHandler.CATALYST_SCREEN_SLOT).isEmpty(),"Player shift-clicks catalyst out");
        c.assertTrue(!d.hasAlkahest() && screen.progress()==0 && screen.maxProgress()==1200,"Shift-click mode change resets progress");
        for(int i=0;i<40;i++) DistilleryBlockEntity.tick(world,pos,d);
        d.setStack(9,new ItemStack(ModItems.ALKAHEST_VIAL));
        c.assertTrue(screen.progress()==0,"Manual replacement also resets raw progress");
        for(int i=0;i<40;i++) DistilleryBlockEntity.tick(world,pos,d);
        d.removeStack(9,1);
        c.assertTrue(screen.progress()==0 && !d.hasAlkahest(),"Inventory removal resets solvent progress");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void changingModeNeverDestroysAlreadyQueuedOutput(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); var world=c.getWorld();
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3)); var d=(DistilleryBlockEntity)world.getBlockEntity(pos);
        d.setStack(0,new ItemStack(Items.APPLE,2));
        for(int i=1;i<8;i++) d.setStack(i,new ItemStack(Items.STONE,64));
        var nbt=d.createNbt(world.getRegistryManager()); nbt.putInt("Progress",1199);
        d.readComponentlessNbt(nbt,world.getRegistryManager());
        DistilleryBlockEntity.tick(world,pos,d); var queued=d.getStack(DistilleryBlockEntity.PENDING).copy();
        c.assertTrue(!queued.isEmpty() && d.getStack(0).getCount()==1,"A completed raw batch is queued when output is full");
        d.setStack(DistilleryBlockEntity.CATALYST,new ItemStack(ModItems.ALKAHEST_VIAL));
        for(int i=0;i<150;i++) DistilleryBlockEntity.tick(world,pos,d);
        c.assertTrue(ItemStack.areEqual(queued,d.getStack(DistilleryBlockEntity.PENDING)) && d.getStack(0).getCount()==1,
            "Switching modes does not consume input, reroll or lose blocked output");
        c.assertTrue(net.alkahest.item.custom.AlkahestVialItem.charges(d.getStack(9))==32,"The pending old batch does not cost a catalyst charge");
        d.removeStack(1); DistilleryBlockEntity.tick(world,pos,d);
        c.assertTrue(ItemStack.areEqual(queued,d.getStack(1)) && d.getStack(8).isEmpty(),"Original batch is delivered before the new cycle");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hopperRulesAndMerge(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY); BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        c.assertTrue(d.canInsert(0,new ItemStack(Items.APPLE),Direction.UP), "Hopper above can insert");
        c.assertFalse(d.canInsert(0,new ItemStack(Items.STONE),Direction.UP), "Hopper rejects stone");
        c.assertFalse(d.canInsert(0,new ItemStack(Items.APPLE),Direction.NORTH), "Side cannot insert input");
        c.assertTrue(d.canInsert(DistilleryBlockEntity.CATALYST,new ItemStack(ModItems.ALKAHEST_VIAL),Direction.NORTH), "Side accepts charged catalyst while idle");
        c.assertFalse(d.canInsert(DistilleryBlockEntity.CATALYST,new ItemStack(ModItems.ALKAHEST_VIAL),Direction.DOWN), "Bottom cannot insert catalyst");
        d.setStack(0,new ItemStack(Items.APPLE)); DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(d.canInsert(DistilleryBlockEntity.CATALYST,new ItemStack(ModItems.ALKAHEST_VIAL),Direction.NORTH), "Side can install catalyst during a raw cycle without starvation");
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
    public void visualStateTracksOperationWithoutLosingInventory(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var d=(DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        var working=net.alkahest.block.custom.DistilleryBlock.WORKING;
        c.assertFalse(c.getWorld().getBlockState(pos).get(working), "A new or pre-property distillery defaults to idle");
        c.assertTrue(c.getWorld().getBlockState(pos).getLuminance()==0, "Idle machine does not glow");
        d.setStack(0,new ItemStack(Items.APPLE,3));
        // An older save has the same slot indices and progress, regardless of the new visual state.
        var saved=d.createNbt(c.getWorld().getRegistryManager()); saved.putInt("Progress",117);
        d.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(c.getWorld().getBlockState(pos).get(working), "Machine becomes active while processing");
        c.assertTrue(c.getWorld().getBlockState(pos).getLuminance()==5, "Working vessel emits subtle light");
        c.assertTrue(c.getWorld().getBlockEntity(pos)==d && d.getStack(0).getCount()==3 && d.createNbt(c.getWorld().getRegistryManager()).getInt("Progress")==118, "Visual transition preserves block entity, input and saved progress");
        for(int i=1;i<8;i++) d.setStack(i,new ItemStack(Items.STONE,64));
        d.setStack(DistilleryBlockEntity.PENDING,new ItemStack(ModItems.ASH));
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertFalse(c.getWorld().getBlockState(pos).get(working), "Blocked output stops the animation");
        c.assertTrue(c.getWorld().getBlockEntity(pos)==d && d.getStack(0).getCount()==3 && !d.getStack(8).isEmpty(), "Blocked state change does not drop or consume inventory");
        d.removeStack(1); DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertTrue(c.getWorld().getBlockState(pos).get(working) && d.getStack(1).isOf(ModItems.ASH), "Releasing output restarts visuals and deposits the pending item");
        d.removeStack(0); DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertFalse(c.getWorld().getBlockState(pos).get(working), "Removing input stops the animation");
        c.assertTrue(c.getWorld().getBlockEntity(pos)==d && d.getStack(1).isOf(ModItems.ASH), "Stopping keeps the output");
        d.setStack(1,new ItemStack(Items.STONE,64));
        d.setStack(0,new ItemStack(Items.APPLE));
        saved=d.createNbt(c.getWorld().getRegistryManager()); saved.putInt("Progress",1199);
        d.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        DistilleryBlockEntity.tick(c.getWorld(),pos,d);
        c.assertFalse(c.getWorld().getBlockState(pos).get(working), "Completing the last item immediately stops the visuals");
        c.assertTrue(d.getStack(0).isEmpty() && !d.getStack(8).isEmpty(), "Full outputs preserve the last completed product");
        var itemBox=new net.minecraft.util.math.Box(pos).expand(.8);
        c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,itemBox,e -> true).isEmpty(), "Visual-only state changes must not scatter items");
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
