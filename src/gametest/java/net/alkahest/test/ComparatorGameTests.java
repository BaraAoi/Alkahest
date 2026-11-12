package net.alkahest.test;

import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.entity.ComparatorBlockEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.List;

public final class ComparatorGameTests implements FabricGameTest {
    private static JarContentsComponent one(int amount) {
        return amount == 0 ? JarContentsComponent.EMPTY : new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, amount, .7f)));
    }
    private static int output(TestContext c, int z) {
        return ((ComparatorBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(4,2,z)))).getOutputSignal();
    }
    private static void placeComparator(TestContext c, int z) {
        c.setBlockState(4,1,z,Blocks.STONE);
        c.setBlockState(4,2,z,Blocks.COMPARATOR.getDefaultState().with(ComparatorBlock.FACING,Direction.WEST));
    }

    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=100)
    public void realComparatorsTrackEveryTankEvenBetweenVisualLevels(TestContext c) {
        c.setBlockState(3,2,1,ModBlocks.JAR);
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,5,ModBlocks.STABILIZER_RECEIVER);
        placeComparator(c,1); placeComparator(c,3); placeComparator(c,5);
        var world=c.getWorld();
        var jar=(JarBlockEntity)world.getBlockEntity(c.getAbsolutePos(new BlockPos(3,2,1)));
        var bellows=(DistilleryBellowsBlockEntity)world.getBlockEntity(c.getAbsolutePos(new BlockPos(3,2,3)));
        var receiver=(StabilizerReceiverBlockEntity)world.getBlockEntity(c.getAbsolutePos(new BlockPos(3,2,5)));
        c.runAtTick(5,()->{
            c.assertTrue(output(c,1)==0 && output(c,3)==0 && output(c,5)==0,"Empty tanks give zero");
            jar.setContents(one(1)); bellows.accept(EssenceType.ORDO,1,.7f);
            receiver.acceptFromPipe(new EssenceStack(EssenceType.ORDO,1,.7f));
        });
        c.runAtTick(15,()->{
            c.assertTrue(output(c,1)==1 && output(c,3)==1 && output(c,5)==1,"One unit emits level one");
            // The visual jar and bellows levels remain unchanged, as does receiver occupied state.
            jar.setContents(one(8)); bellows.accept(EssenceType.ORDO,4,.7f);
            receiver.acceptFromPipe(new EssenceStack(EssenceType.AQUA,7,.5f));
        });
        c.runAtTick(25,()->{
            c.assertTrue(output(c,1)==2 && output(c,3)==2 && output(c,5)==2,"Changes within the same visible level notify real comparators, including receiver mixtures");
            jar.setContents(one(100)); bellows.accept(EssenceType.ORDO,100,.7f); receiver.setContents(one(100));
        });
        c.runAtTick(35,()->{
            c.assertTrue(output(c,1)==15 && output(c,3)==15 && output(c,5)==15,"Full tanks emit 15");
            jar.setContents(one(50)); bellows.drain(32); receiver.setContents(one(50));
        });
        c.runAtTick(45,()->{
            c.assertTrue(output(c,1)==8 && output(c,3)==8 && output(c,5)==8,"Half full emits 8");
            jar.setContents(JarContentsComponent.EMPTY); bellows.drain(64); receiver.setContents(JarContentsComponent.EMPTY);
        });
        c.runAtTick(55,()->{
            c.assertTrue(output(c,1)==0 && output(c,3)==0 && output(c,5)==0,"Draining returns signal to zero");
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=80)
    public void comparatorCanControlPipeValve(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.JAR); placeComparator(c,3);
        c.setBlockState(5,2,3,ModBlocks.ALCHEMY_PIPE);
        var world=c.getWorld();
        var jar=(JarBlockEntity)world.getBlockEntity(c.getAbsolutePos(new BlockPos(3,2,3)));
        var pipe=(net.alkahest.block.entity.AlchemyPipeBlockEntity)world.getBlockEntity(c.getAbsolutePos(new BlockPos(5,2,3)));
        pipe.setValveMode(net.alkahest.block.entity.PipeValveMode.CLOSE_WHEN_POWERED);
        c.runAtTick(5,()->{
            c.assertTrue(pipe.valveOpen(),"Empty jar leaves the valve open");
            jar.setPipeSource(true); jar.setContents(one(8));
        });
        c.runAtTick(15,()->{
            c.assertTrue(output(c,3)==2 && !pipe.valveOpen(),"Comparator output closes the redstone valve");
            jar.setContents(JarContentsComponent.EMPTY);
        });
        c.runAtTick(25,()->{
            c.assertTrue(output(c,3)==0 && pipe.valveOpen(),"Emptying the jar reopens the valve");
            c.assertTrue(jar.pipeSource(),"Comparator and storage changes do not reset source mode");
            c.complete();
        });
    }

}
