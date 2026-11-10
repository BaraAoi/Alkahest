package net.alkahest.test;

import net.alkahest.block.custom.EssenceSeparatorBlock;
import net.alkahest.block.entity.EssenceSeparatorBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import java.util.List;

public class SeparatorGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void splitConservesEssenceStabilityAndContainers(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ESSENCE_SEPARATOR);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var mixture = new ItemStack(ModBlocks.JAR);
        mixture.set(DataComponentTypes.CUSTOM_NAME,Text.literal("My mixture"));
        JarItem.set(mixture,new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA,3,.2f),new EssenceStack(EssenceType.ORDO,5,.7f))));
        player.setStackInHand(Hand.MAIN_HAND,mixture);
        var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
        c.getWorld().getBlockState(pos).onUseWithItem(mixture,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var separator=(EssenceSeparatorBlockEntity)c.getWorld().getBlockEntity(pos);
        c.assertTrue(player.getStackInHand(Hand.MAIN_HAND).isEmpty() && JarItem.contents(separator.stacks().get(0)).total()==8,"A filled jar enters the machine exactly once");
        c.assertTrue(c.getWorld().getBlockState(pos).get(EssenceSeparatorBlock.OCCUPIED),"Machine becomes occupied");
        ItemStack unrelated = new ItemStack(Items.STICK);
        player.setStackInHand(Hand.MAIN_HAND,unrelated);
        c.assertTrue(c.getWorld().getBlockState(pos).onUseWithItem(unrelated,c.getWorld(),player,Hand.MAIN_HAND,hit).isAccepted(), "Unrelated held items must not trigger the empty-hand action");
        c.assertTrue(JarItem.contents(separator.stacks().get(0)).total()==8, "Unrelated held item cannot remove the stored jar");
        var wrong = new ItemStack(ModBlocks.JAR); JarItem.set(wrong,new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS,1,.4f))));
        player.setStackInHand(Hand.MAIN_HAND,wrong);
        c.getWorld().getBlockState(pos).onUseWithItem(wrong,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(JarItem.contents(wrong).total()==1 && JarItem.contents(separator.stacks().get(0)).total()==8,"Filled output jar is rejected without loss");
        var output1=new ItemStack(ModBlocks.JAR); player.setStackInHand(Hand.MAIN_HAND,output1);
        c.getWorld().getBlockState(pos).onUseWithItem(output1,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var first=JarItem.contents(output1).contents().getFirst();
        c.assertTrue(first.type()==EssenceType.AQUA && first.amount()==3 && first.stability()==.2f,"First fraction retains its exact amount and stability");
        c.assertTrue(JarItem.contents(separator.stacks().get(0)).total()==5,"The source keeps other fractions");
        var saved=separator.createNbt(c.getWorld().getRegistryManager());
        var restored=new EssenceSeparatorBlockEntity(pos,separator.getCachedState()); restored.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        c.assertTrue(JarItem.contents(restored.stacks().get(0)).total()==5 && restored.stacks().get(0).getName().getString().equals("My mixture"),"Stored source item and its name survive save/load");
        var output2=new ItemStack(ModBlocks.JAR); player.setStackInHand(Hand.MAIN_HAND,output2);
        c.getWorld().getBlockState(pos).onUseWithItem(output2,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var second=JarItem.contents(output2).contents().getFirst();
        c.assertTrue(second.type()==EssenceType.ORDO && second.amount()==5 && second.stability()==.7f,"Second fraction remains intact");
        c.assertTrue(JarItem.contents(separator.stacks().get(0)).total()==0,"Original jar is still present and becomes empty");
        player.getInventory().setStack(8,output1.copy()); player.getInventory().setStack(7,output2.copy());
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        c.getWorld().getBlockState(pos).onUse(c.getWorld(),player,hit);
        c.assertTrue(separator.stacks().get(0).isEmpty() && !c.getWorld().getBlockState(pos).get(EssenceSeparatorBlock.OCCUPIED),"Empty hand returns the original and resets appearance");
        boolean found=false;
        for(int i=0;i<player.getInventory().size();i++) {
            var stack=player.getInventory().getStack(i);
            if (stack.isOf(ModBlocks.JAR.asItem()) && stack.getName().getString().equals("My mixture") && JarItem.contents(stack).total()==0) found=true;
        }
        c.assertTrue(found,"Original named jar must be returned to the player");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=60)
    public void breakingReturnsStoredJar(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ESSENCE_SEPARATOR); BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var separator=(EssenceSeparatorBlockEntity)c.getWorld().getBlockEntity(pos);
        var player=c.createMockPlayer(GameMode.SURVIVAL); var jar=new ItemStack(ModBlocks.JAR);
        JarItem.set(jar,new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS,9,.3f))));
        player.setStackInHand(Hand.MAIN_HAND,jar); separator.useJar(player,Hand.MAIN_HAND);
        c.getWorld().breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(pos).expand(2),e->e.getStack().isOf(ModBlocks.JAR.asItem()));
            c.assertTrue(drops.size()==1 && JarItem.contents(drops.getFirst().getStack()).total()==9,"Breaking returns exactly one stored jar with all essence");
            c.complete();
        });
    }
}
