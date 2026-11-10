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

    /** jar - pipe - pipe - bellows: the pipes find the container with essence and the empty one. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void autoRoutesWithoutDirectionsOrFilters(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.DISTILLERY_BELLOWS);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(p.west());
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        source.setContents(of(EssenceType.HERBA,8,.30f));
        int moved=AlchemyPipeBlockEntity.pulse(c.getWorld(),p);
        c.assertTrue(moved==AlchemyPipeBlockEntity.UNITS_PER_PULSE,"A pulse moves the faster burst of units");
        c.assertTrue(bellows.amount()==moved && bellows.storedType()==EssenceType.HERBA,"Bellows receive the essence");
        c.assertTrue(source.contents().total()+moved==8,"Essence is conserved");
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p);
        c.assertTrue(pipe.active() && pipe.flowing()==EssenceType.HERBA,"A working pipe reports the flowing essence");
        c.assertTrue(c.getWorld().getBlockState(p).get(AlchemyPipeBlock.ACTIVE),"The animation state switches on while working");
        c.complete();
    }

    /** The network also drains a bellows back into an empty jar, with no configuration at all. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void networkDrainsBellowsIntoJars(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_BELLOWS);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.west());
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.east());
        bellows.accept(EssenceType.AQUA,6,.45f);
        int moved=AlchemyPipeBlockEntity.pulse(c.getWorld(),p);
        c.assertTrue(moved==AlchemyPipeBlockEntity.UNITS_PER_PULSE,"The flask empties into the jar");
        c.assertTrue(jar.contents().total()==moved && bellows.amount()==6-moved,"Both sides are updated consistently");
        c.assertTrue(jar.contents().contents().getFirst().type()==EssenceType.AQUA,"The drained type is kept");
        c.complete();
    }

    /** Two different essences never end up in the same container. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void networkKeepsEssenceTypesApart(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,2,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var first=(JarBlockEntity)c.getWorld().getBlockEntity(p.west());
        var second=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        var empty=(JarBlockEntity)c.getWorld().getBlockEntity(p.north());
        first.setContents(of(EssenceType.IGNIS,5,.2f));
        second.setContents(of(EssenceType.ORDO,5,.6f));
        AlchemyPipeBlockEntity.pulse(c.getWorld(),p);
        c.assertTrue(empty.contents().contents().size()==1,"The empty jar accepts exactly one type");
        EssenceType landed=empty.contents().contents().getFirst().type();
        c.assertTrue(landed==EssenceType.IGNIS || landed==EssenceType.ORDO,"Only a stored type moves");
        c.assertTrue(empty.contents().total()==AlchemyPipeBlockEntity.UNITS_PER_PULSE,"The empty jar fills at the pulse rate");
        c.assertTrue(first.contents().total()==5 || second.contents().total()==5,"Only the source of that type was drained");
        c.complete();
    }

    /** A destination that cannot hold more stops the line without any loss. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullDestinationsNeverLoseEssence(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_BELLOWS);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(p.west());
        var bellows=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.east());
        bellows.accept(EssenceType.AQUA,DistilleryBellowsBlockEntity.CAPACITY,.4f);
        source.setContents(of(EssenceType.HERBA,3,.30f));
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0,"A different type cannot enter an occupied flask");
        c.assertTrue(source.contents().total()==3 && bellows.amount()==DistilleryBellowsBlockEntity.CAPACITY,"Nothing is lost");
        source.setContents(of(EssenceType.AQUA,3,.30f));
        int moved=AlchemyPipeBlockEntity.pulse(c.getWorld(),p);
        c.assertTrue(moved==AlchemyPipeBlockEntity.UNITS_PER_PULSE,"A full flask gives essence away instead of overflowing");
        c.assertTrue(bellows.amount()==DistilleryBellowsBlockEntity.CAPACITY-moved
            && source.contents().total()==3+moved,"The full flask never exceeds its capacity");
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

    /** Old mixtures can be drained by type, but a legacy mixed jar never receives anything. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pipesNeverCreateNewMixtures(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos pipe=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(pipe.west());
        var target=(JarBlockEntity)c.getWorld().getBlockEntity(pipe.east());
        source.setContents(of(EssenceType.AQUA,4,.2f)); target.setContents(of(EssenceType.ORDO,3,.7f));
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),pipe)==0,"Incompatible destinations block the line");
        c.assertTrue(source.contents().total()==4 && target.contents().total()==3,"Blocked transfer preserves both quantities");
        var legacy=new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA,4,.2f),new EssenceStack(EssenceType.ORDO,3,.7f)));
        target.setContents(legacy);
        int moved=AlchemyPipeBlockEntity.pulse(c.getWorld(),pipe);
        c.assertTrue(target.contents().total()<=7,"A legacy mixed jar never receives anything new");
        c.assertTrue(source.contents().total()+target.contents().total()==11,"A drained legacy jar still conserves essence");
        source.setContents(JarContentsComponent.EMPTY); target.setContents(legacy);
        int drained=AlchemyPipeBlockEntity.pulse(c.getWorld(),pipe);
        c.assertTrue(drained==AlchemyPipeBlockEntity.UNITS_PER_PULSE,"An old mixture can drain its compatible type");
        c.assertTrue(source.contents().contents().size()==1,"The receiving jar holds a single type");
        c.assertTrue(source.contents().total()+target.contents().total()==7,"Nothing is created or lost");
        c.complete();
    }

    /** The working state and the tinted essence reach the client copy. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workingStateAndEssenceSyncToClients(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos pos=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(pos.west());
        source.setContents(of(EssenceType.LUX,2,.5f));
        AlchemyPipeBlockEntity.pulse(c.getWorld(),pos);
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pos);
        var replica=new AlchemyPipeBlockEntity(pos,pipe.getCachedState());
        replica.readComponentlessNbt(pipe.toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.flowing()==EssenceType.LUX,"Initial chunk data carries the flowing essence");
        c.assertTrue(pipe.toUpdatePacket()!=null,"Changing state produces update data");
        c.complete();
    }
}
