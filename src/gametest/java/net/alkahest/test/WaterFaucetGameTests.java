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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

public class WaterFaucetGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=160)
    public void fillsRepeatedlyWithoutConsumingFocusAndRespectsRedstone(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_CAULDRON);
        c.setBlockState(3,3,3,ModBlocks.WATER_FAUCET);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,3,3));
        var faucet=(WaterFaucetBlockEntity)c.getWorld().getBlockEntity(pos);
        var cauldron=(CauldronBlockEntity)c.getWorld().getBlockEntity(pos.down());
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var wrong=new ItemStack(Items.DIAMOND); player.setStackInHand(Hand.MAIN_HAND,wrong);
        faucet.insert(player,Hand.MAIN_HAND);
        c.assertTrue(faucet.stacks().get(0).isEmpty() && wrong.getCount()==1,"Rejects unrelated items");
        var focus=new ItemStack(ModItems.CRYSTALS.get(EssenceType.AQUA),2);
        player.setStackInHand(Hand.MAIN_HAND,focus); faucet.insert(player,Hand.MAIN_HAND);
        c.assertTrue(focus.getCount()==1 && faucet.stacks().get(0).getCount()==1 && c.getWorld().getBlockState(pos).get(WaterFaucetBlock.INSTALLED),"Installs exactly one focus");
        var saved=faucet.createNbt(c.getWorld().getRegistryManager());
        var restored=new WaterFaucetBlockEntity(pos,faucet.getCachedState());
        restored.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        c.assertTrue(restored.stacks().get(0).isOf(ModItems.CRYSTALS.get(EssenceType.AQUA)),"Focus persists in NBT");
        c.runAtTick(25,()->{
            c.assertTrue(cauldron.hasWater() && faucet.stacks().get(0).getCount()==1,"Automatic water without consuming the focus");
            var nbt=cauldron.createNbt(c.getWorld().getRegistryManager()); nbt.putBoolean("Water",false);
            cauldron.readComponentlessNbt(nbt,c.getWorld().getRegistryManager());
            c.setBlockState(4,3,3,Blocks.REDSTONE_BLOCK);
        });
        c.runAtTick(55,()->{
            c.assertTrue(!cauldron.hasWater(),"Redstone pauses filling");
            c.setBlockState(4,3,3,Blocks.AIR);
        });
        c.runAtTick(85,()->{
            c.assertTrue(cauldron.hasWater() && faucet.stacks().get(0).getCount()==1,"Fills again after redstone removed");
            var nbt=cauldron.createNbt(c.getWorld().getRegistryManager()); nbt.putBoolean("DirtyWater",true);
            cauldron.readComponentlessNbt(nbt,c.getWorld().getRegistryManager());
        });
        c.runAtTick(115,()->{
            c.assertTrue(cauldron.isDirtyWater(),"Does not clean or replace dirty water");
            faucet.take(player);
            c.assertTrue(faucet.stacks().get(0).isEmpty() && !c.getWorld().getBlockState(pos).get(WaterFaucetBlock.INSTALLED),"Focus can be removed and appearance resets");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=80)
    public void placementOnRimAndMixturePreservation(TestContext c) {
        c.setBlockState(3,1,3,Blocks.MAGMA_BLOCK);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_CAULDRON);
        BlockPos cauldronPos=c.getAbsolutePos(new BlockPos(3,2,3));
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var blockItem=new ItemStack(ModBlocks.WATER_FAUCET);
        player.setStackInHand(Hand.MAIN_HAND,blockItem);
        var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(cauldronPos).add(0,0.5,0),net.minecraft.util.math.Direction.UP,cauldronPos,false);
        c.getWorld().getBlockState(cauldronPos).onUseWithItem(blockItem,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(c.getWorld().getBlockState(cauldronPos.up()).isOf(ModBlocks.WATER_FAUCET),"Faucet places directly on cauldron rim");
        var faucet=(WaterFaucetBlockEntity)c.getWorld().getBlockEntity(cauldronPos.up());
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.WANDS.get(EssenceType.AQUA)));
        faucet.insert(player,Hand.MAIN_HAND);
        var cauldron=(CauldronBlockEntity)c.getWorld().getBlockEntity(cauldronPos);
        cauldron.fillFromFaucet();
        c.assertTrue(cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS))),"Dissolve test essence");
        var before=cauldron.essences();
        c.runAtTick(30,()->{
            c.assertTrue(cauldron.hasWater() && cauldron.essences().equals(before),"Faucet does not replace a filled mixture");
            c.assertTrue(faucet.stacks().get(0).isOf(ModItems.WANDS.get(EssenceType.AQUA)),"Water wand remains installed");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=60)
    public void waterWandIsPreservedWhenBroken(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_CAULDRON);
        c.setBlockState(3,3,3,ModBlocks.WATER_FAUCET);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,3,3));
        var faucet=(WaterFaucetBlockEntity)c.getWorld().getBlockEntity(pos);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var wand=new ItemStack(ModItems.WANDS.get(EssenceType.AQUA));
        wand.set(DataComponentTypes.CUSTOM_NAME,Text.literal("My water wand"));
        player.setStackInHand(Hand.MAIN_HAND,wand); faucet.insert(player,Hand.MAIN_HAND);
        var restored=new WaterFaucetBlockEntity(pos,faucet.getCachedState());
        restored.readComponentlessNbt(faucet.createNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(restored.stacks().get(0).getName().getString().equals("My water wand"),"Wand components persist");
        c.getWorld().breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(pos).expand(3),e->e.getStack().isOf(ModItems.WANDS.get(EssenceType.AQUA)));
            c.assertTrue(drops.size()==1 && drops.getFirst().getStack().getName().getString().equals("My water wand"),"Breaking returns exactly the original wand");
            c.complete();
        });
    }
}
