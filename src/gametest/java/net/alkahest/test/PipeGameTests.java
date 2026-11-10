package net.alkahest.test;

import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.List;

public class PipeGameTests implements FabricGameTest {
    private static JarContentsComponent of(EssenceType t,int n,float s) { return new JarContentsComponent(List.of(new EssenceStack(t,n,s))); }

    /** bellows - pipe - jar: flasks give, jars take, nothing has to be configured. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bellowsFeedJarsAutomatically(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        bellows.accept(EssenceType.HERBA,8,.30f);
        int moved=AlchemyPipeBlockEntity.pulse(c.getWorld(),p);
        c.assertTrue(moved==AlchemyPipeBlockEntity.UNITS_PER_PULSE,"A pulse moves the faster burst of units");
        c.assertTrue(jar.contents().total()==moved,"The jar receives what the flask gave");
        c.assertTrue(bellows.amount()+moved==8,"Essence is conserved");
        c.assertTrue(jar.contents().contents().getFirst().type()==EssenceType.HERBA,"The jar keeps the flask's type");
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p);
        c.assertTrue(pipe.active() && pipe.flowing()==EssenceType.HERBA,"A working pipe reports the flowing essence");
        c.assertTrue(c.getWorld().getBlockState(p).get(AlchemyPipeBlock.ACTIVE),"The animation state switches on while working");
        c.complete();
    }

    /** Jars only take essence in: they never hand it to a neighbour, so nothing ping-pongs. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void jarsNeverGiveEssenceAway(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var full=(JarBlockEntity)c.getWorld().getBlockEntity(p.west());
        var empty=(JarBlockEntity)c.getWorld().getBlockEntity(p.east());
        full.setContents(of(EssenceType.AQUA,20,.4f));
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0,"A jar is not a source for the network");
        c.assertTrue(full.contents().total()==20 && empty.contents().total()==0,"Nothing moves between jars");
        c.assertFalse(c.getWorld().getBlockState(p).get(AlchemyPipeBlock.ACTIVE),"An idle network is not animated");
        c.complete();
    }

    /** Two flasks with different essences fill two jars, one type each. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void twoFlasksFillTwoJarsWithDifferentTypes(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(2,2,2,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,2,ModBlocks.JAR);
        c.setBlockState(4,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var first=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.north());
        var second=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var left=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        var right=(JarBlockEntity)c.getWorld().getBlockEntity(p.north().east());
        first.accept(EssenceType.IGNIS,10,.3f);
        second.accept(EssenceType.AQUA,10,.5f);
        AlchemyPipeBlockEntity.pulse(c.getWorld(),p);
        c.assertTrue(left.contents().contents().size()==1 && right.contents().contents().size()==1,"Each jar gets exactly one type");
        c.assertTrue(left.contents().contents().getFirst().type()!=right.contents().contents().getFirst().type(),
            "Different essences land in different jars");
        var types=List.of(left.contents().contents().getFirst().type(), right.contents().contents().getFirst().type());
        c.assertTrue(types.contains(EssenceType.IGNIS) && types.contains(EssenceType.AQUA),"Both flask types were delivered");
        c.assertTrue(left.contents().total()+right.contents().total()==2*AlchemyPipeBlockEntity.UNITS_PER_PULSE,
            "Each flask supplied one burst");
        c.assertTrue(first.amount()==10-AlchemyPipeBlockEntity.UNITS_PER_PULSE
            && second.amount()==10-AlchemyPipeBlockEntity.UNITS_PER_PULSE,"Both flasks were drained once");
        c.complete();
    }

    /** A jar of another type or a full jar simply stops the branch without any loss. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullOrMismatchedJarsKeepEssenceSafe(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east());
        bellows.accept(EssenceType.IGNIS,5,.3f);
        jar.setContents(of(EssenceType.AQUA,2,.5f));
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0,"A jar of another type is not a destination");
        c.assertTrue(bellows.amount()==5 && jar.contents().total()==2,"Nothing is lost when the type does not fit");
        jar.setContents(of(EssenceType.IGNIS,net.alkahest.item.custom.JarItem.CAPACITY,.5f));
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0,"A full jar stops the line");
        c.assertTrue(bellows.amount()==5,"A blocked pulse is lossless");
        c.complete();
    }

    /** Clicking a jar with pipes still places a segment that connects on its own. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clickJarToPlacePipe(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.JAR); BlockPos jar=c.getAbsolutePos(new BlockPos(2,2,3));
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setPosition(jar.getX()-2,jar.getY(),jar.getZ());
        var pipes=new ItemStack(ModBlocks.ALCHEMY_PIPE,8); player.setStackInHand(Hand.MAIN_HAND,pipes);
        var hit=new BlockHitResult(Vec3d.ofCenter(jar).add(.5,0,0),Direction.EAST,jar,false);
        c.getWorld().getBlockState(jar).onUseWithItem(pipes,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var state=c.getWorld().getBlockState(jar.east());
        c.assertTrue(state.isOf(ModBlocks.ALCHEMY_PIPE),"Regular click places a pipe");
        c.assertFalse(state.get(AlchemyPipeBlock.ACTIVE),"A fresh pipe is idle");
        c.assertTrue(pipes.getCount()==7 && state.get(AlchemyPipeBlock.WEST),"Placement consumes one item and connects to the jar");
        c.complete();
    }

    /** Old mixtures never receive anything new, and a jar never becomes a source. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyMixturesAreNeverFed(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east());
        var legacy=new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA,4,.2f),new EssenceStack(EssenceType.ORDO,3,.7f)));
        jar.setContents(legacy);
        bellows.accept(EssenceType.IGNIS,4,.3f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0,"A legacy mixed jar is not a destination");
        c.assertTrue(jar.contents().equals(legacy) && bellows.amount()==4,"The legacy jar and the flask stay untouched");
        c.complete();
    }

    /** The working state and the tinted essence reach the client copy. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workingStateAndEssenceSyncToClients(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos pos=c.getAbsolutePos(new BlockPos(2,2,3));
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(pos.west());
        bellows.accept(EssenceType.LUX,2,.5f);
        AlchemyPipeBlockEntity.pulse(c.getWorld(),pos);
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pos);
        var replica=new AlchemyPipeBlockEntity(pos,pipe.getCachedState());
        replica.readComponentlessNbt(pipe.toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.flowing()==EssenceType.LUX,"Initial chunk data carries the flowing essence");
        c.assertTrue(pipe.toUpdatePacket()!=null,"Changing state produces update data");
        c.complete();
    }

}
