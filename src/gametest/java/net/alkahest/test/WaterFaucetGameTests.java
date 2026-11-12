package net.alkahest.test;

import net.alkahest.block.custom.WaterFaucetBlock;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.WaterFaucetBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class WaterFaucetGameTests implements FabricGameTest {
    private static BlockHitResult hit(BlockPos p) { return new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false); }
    private static void empty(CauldronBlockEntity c, TestContext test) {
        var nbt=c.createNbt(test.getWorld().getRegistryManager()); nbt.putBoolean("Water",false);
        c.readComponentlessNbt(nbt,test.getWorld().getRegistryManager());
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=180)
    public void builtInFocusToggleAndRedstone(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_CAULDRON); c.setBlockState(3,3,3,ModBlocks.WATER_FAUCET);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,3,3));
        var w=c.getWorld(); var cauldron=(CauldronBlockEntity)w.getBlockEntity(pos.down());
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        c.runAtTick(25,()->{
            c.assertTrue(cauldron.hasWater(),"Built-in crystal fills without any inserted focus");
            empty(cauldron,c); w.getBlockState(pos).onUse(w,player,hit(pos));
            c.assertTrue(!w.getBlockState(pos).get(WaterFaucetBlock.ENABLED),"Right click closes");
        });
        c.runAtTick(55,()->{
            c.assertTrue(!cauldron.hasWater(),"Closed faucet does not fill");
            w.getBlockState(pos).onUse(w,player,hit(pos)); c.setBlockState(4,3,3,Blocks.REDSTONE_BLOCK);
        });
        c.runAtTick(85,()->{
            c.assertTrue(!cauldron.hasWater(),"Redstone pauses an open faucet"); c.setBlockState(4,3,3,Blocks.AIR);
        });
        c.runAtTick(115,()->{
            c.assertTrue(cauldron.hasWater(),"Open faucet refills after redstone removed");
            var nbt=cauldron.createNbt(w.getRegistryManager()); nbt.putBoolean("DirtyWater",true);
            cauldron.readComponentlessNbt(nbt,w.getRegistryManager());
        });
        c.runAtTick(145,()->{ c.assertTrue(cauldron.isDirtyWater(),"Dirty water is not replaced"); c.complete(); });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bucketsRespectStateAndInventory(TestContext c) {
        c.setBlockState(3,3,3,ModBlocks.WATER_FAUCET); BlockPos pos=c.getAbsolutePos(new BlockPos(3,3,3));
        var w=c.getWorld(); var player=c.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BUCKET,2));
        var held=player.getMainHandStack(); w.getBlockState(pos).onUseWithItem(held,w,player,Hand.MAIN_HAND,hit(pos));
        c.assertTrue(player.getMainHandStack().isOf(Items.BUCKET) && player.getMainHandStack().getCount()==1,"Consumes one empty bucket from stack");
        c.assertTrue(player.getInventory().contains(new ItemStack(Items.WATER_BUCKET)),"Filled bucket delivered to inventory");
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY); w.getBlockState(pos).onUse(w,player,hit(pos));
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BUCKET)); held=player.getMainHandStack();
        w.getBlockState(pos).onUseWithItem(held,w,player,Hand.MAIN_HAND,hit(pos));
        c.assertTrue(player.getMainHandStack().isOf(Items.BUCKET),"Closed faucet does not fill a bucket");
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY); w.getBlockState(pos).onUse(w,player,hit(pos));
        c.setBlockState(4,3,3,Blocks.REDSTONE_BLOCK); player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BUCKET));
        w.getBlockState(pos).onUseWithItem(player.getMainHandStack(),w,player,Hand.MAIN_HAND,hit(pos));
        c.assertTrue(player.getMainHandStack().isOf(Items.BUCKET),"Redstone also prevents filling buckets");
        c.setBlockState(4,3,3,Blocks.AIR);
        w.getBlockState(pos).onUseWithItem(player.getMainHandStack(),w,player,Hand.MAIN_HAND,hit(pos));
        c.assertTrue(player.getMainHandStack().isOf(Items.WATER_BUCKET),"Single bucket is exchanged even without a cauldron");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=80)
    public void placementOnRimAndMixturePreservation(TestContext c) {
        c.setBlockState(3,1,3,Blocks.MAGMA_BLOCK); c.setBlockState(3,2,3,ModBlocks.ALCHEMY_CAULDRON);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3)); var player=c.createMockPlayer(GameMode.SURVIVAL);
        var stack=new ItemStack(ModBlocks.WATER_FAUCET); player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(pos).add(0,0.5,0),Direction.UP,pos,false);
        c.getWorld().getBlockState(pos).onUseWithItem(stack,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(c.getWorld().getBlockState(pos.up()).isOf(ModBlocks.WATER_FAUCET),"Places directly on rim");
        var cauldron=(CauldronBlockEntity)c.getWorld().getBlockEntity(pos); cauldron.fillFromFaucet();
        c.assertTrue(cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS))),"Dissolve test essence");
        var before=cauldron.essences();
        c.runAtTick(30,()->{ c.assertTrue(cauldron.hasWater() && cauldron.essences().equals(before),"Faucet does not replace a mixture"); c.complete(); });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=60)
    public void oldWandPersistsAndIsReturned(TestContext c) {
        c.setBlockState(3,3,3,ModBlocks.WATER_FAUCET); BlockPos pos=c.getAbsolutePos(new BlockPos(3,3,3));
        var faucet=(WaterFaucetBlockEntity)c.getWorld().getBlockEntity(pos);
        var wand=new ItemStack(ModItems.WANDS.get(EssenceType.AQUA)); wand.set(DataComponentTypes.CUSTOM_NAME,Text.literal("My water wand"));
        faucet.stacks().set(0,wand);
        var restored=new WaterFaucetBlockEntity(pos,faucet.getCachedState());
        restored.readComponentlessNbt(faucet.createNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(restored.stacks().get(0).getName().getString().equals("My water wand"),"Old saved focus and components persist");
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setSneaking(true);
        c.getWorld().getBlockState(pos).onUse(c.getWorld(),player,hit(pos));
        c.assertTrue(faucet.stacks().get(0).isEmpty() && player.getInventory().contains(new ItemStack(ModItems.WANDS.get(EssenceType.AQUA))),"Shift empty hand returns old focus");
        var replacement=new ItemStack(ModItems.WANDS.get(EssenceType.AQUA)); replacement.set(DataComponentTypes.CUSTOM_NAME,Text.literal("My water wand"));
        faucet.stacks().set(0,replacement); c.getWorld().breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(pos).expand(3),e->e.getStack().isOf(ModItems.WANDS.get(EssenceType.AQUA)));
            c.assertTrue(drops.size()==1 && drops.getFirst().getStack().getName().getString().equals("My water wand"),"Breaking returns exactly the old wand"); c.complete();
        });
    }
}
