package net.alkahest.test;

import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.block.entity.StabilizerCoreBlockEntity;
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

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pipeFeedsStabilizerBeforeJarsAndPreservesMixtures(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,2,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(3,2,4,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(3,2,3));
        var first=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var second=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.north());
        var receiver=(StabilizerReceiverBlockEntity)c.getWorld().getBlockEntity(p.east());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.south());
        c.assertTrue(c.getWorld().getBlockState(p).get(AlchemyPipeBlock.EAST), "Pipe visibly connects to receiver");
        first.accept(EssenceType.IGNIS,8,.4f); second.accept(EssenceType.AQUA,8,.6f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==8, "Two bellows each send four units");
        c.assertTrue(receiver.contents().total()==8 && receiver.contents().contents().size()==2, "Receiver accepts both types");
        c.assertTrue(jar.contents().total()==0, "Receiver takes priority over connected storage jars");
        c.assertTrue(first.amount()==4 && second.amount()==4, "No essence is lost in transit");
        var replica=new StabilizerReceiverBlockEntity(p.east(),receiver.getCachedState());
        replica.readComponentlessNbt(receiver.toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.contents().equals(receiver.contents()), "Piped mixture survives serialization and client sync");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullReceiverBackpressuresWithoutLossAndJarsNeverRefillIt(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(3,2,4,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(3,2,3));
        var b=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var receiver=(StabilizerReceiverBlockEntity)c.getWorld().getBlockEntity(p.east());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.south());
        receiver.setContents(of(EssenceType.AQUA,98,.6f));
        jar.setContents(of(EssenceType.ORDO,100,.6f));
        b.accept(EssenceType.IGNIS,8,.4f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==2, "Only free receiver capacity is accepted");
        c.assertTrue(receiver.contents().total()==100 && b.amount()==6 && jar.contents().total()==100, "All quantities retained");
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0, "A full receiver and incompatible full jar block the line");
        b.drain(6);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0, "Jars never become sources for the receiver");
        c.assertTrue(jar.contents().total()==100 && receiver.contents().total()==100, "No jar/receiver feedback loop");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void continuousPipeFeedDoesNotRestartSorting(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(4,3,3,ModBlocks.STABILIZER_CORE);
        c.setBlockState(4,4,3,ModBlocks.STABILIZER_CREST);
        c.setBlockState(3,2,4,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(3,2,3));
        var b=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var receiver=(StabilizerReceiverBlockEntity)c.getWorld().getBlockEntity(p.east());
        var core=(StabilizerCoreBlockEntity)c.getWorld().getBlockEntity(p.east().up());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.south());
        core.setActivated(true);
        for (int i=0; i<StabilizerReceiverBlockEntity.TICKS_PER_TYPE; i++) {
            if (i%5==0) { b.accept(EssenceType.IGNIS,1,.5f); AlchemyPipeBlockEntity.pulse(c.getWorld(),p); }
            StabilizerReceiverBlockEntity.tick(c.getWorld(),p.east(),receiver);
        }
        c.assertTrue(jar.contents().total()==40, "Continual arrivals do not reset the cycle, output is sorted");
        c.assertTrue(receiver.contents().total()==0 && b.amount()==0, "All forty units were delivered without loss");
        c.assertTrue(Math.abs(jar.contents().contents().getFirst().stability()-.90f)<.001f, "Output is stabilized normally");
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0 && jar.contents().total()==40,
            "The output cannot flow back into its receiver");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sortingProgressTracksTheTypeAndKeepsWeightedStability(TestContext c) {
        c.setBlockState(4,2,3,ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(4,3,3,ModBlocks.STABILIZER_CORE);
        c.setBlockState(4,4,3,ModBlocks.STABILIZER_CREST);
        BlockPos p=c.getAbsolutePos(new BlockPos(4,2,3));
        var r=(StabilizerReceiverBlockEntity)c.getWorld().getBlockEntity(p);
        ((StabilizerCoreBlockEntity)c.getWorld().getBlockEntity(p.up())).setActivated(true);
        c.assertTrue(r.acceptFromPipe(new EssenceStack(EssenceType.AQUA,4,.4f))==4,"First input accepted");
        for (int i=0; i<40; i++) StabilizerReceiverBlockEntity.tick(c.getWorld(),p,r);
        c.assertTrue(r.progress()==40,"Sorting is in progress");
        c.assertTrue(r.acceptFromPipe(new EssenceStack(EssenceType.AQUA,4,.8f))==4,"More of the current type accepted");
        c.assertTrue(r.progress()==40,"Same-type input preserves work");
        c.assertTrue(Math.abs(r.contents().contents().getFirst().stability()-.6f)<.001f,"Stability is weighted by quantity");
        c.assertTrue(r.acceptFromPipe(new EssenceStack(EssenceType.IGNIS,1,.5f))==1,"A second type can enter the receiver");
        c.assertTrue(r.progress()==0,"Reordering to a different first type restarts its own sorting cycle");
        c.assertTrue(r.pipeFree(EssenceType.VACUUM)==0 && r.acceptFromPipe(new EssenceStack(EssenceType.VACUUM,1,.5f))==0,
            "Vacuum is not accepted");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clickReceiverToPlaceConnectingPipe(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.STABILIZER_RECEIVER); BlockPos receiver=c.getAbsolutePos(new BlockPos(2,2,3));
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setPosition(receiver.getX()-2,receiver.getY(),receiver.getZ());
        var pipes=new ItemStack(ModBlocks.ALCHEMY_PIPE,8); player.setStackInHand(Hand.MAIN_HAND,pipes);
        var hit=new BlockHitResult(Vec3d.ofCenter(receiver).add(.5,0,0),Direction.EAST,receiver,false);
        c.getWorld().getBlockState(receiver).onUseWithItem(pipes,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var state=c.getWorld().getBlockState(receiver.east());
        c.assertTrue(state.isOf(ModBlocks.ALCHEMY_PIPE) && state.get(AlchemyPipeBlock.WEST), "A click places a connected pipe");
        c.assertTrue(pipes.getCount()==7, "Placement consumes one pipe");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void filtersSplitSourcesIntoSeparateBranches(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,2,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(5,2,3,ModBlocks.JAR);
        c.setBlockState(3,2,5,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(3,2,3));
        var ignis=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var aqua=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.north());
        var firePipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p.east());
        var waterPipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p.south());
        var fireJar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        var waterJar=(JarBlockEntity)c.getWorld().getBlockEntity(p.south(2));
        firePipe.setFilter(EssenceType.IGNIS); waterPipe.setFilter(EssenceType.AQUA);
        ignis.accept(EssenceType.IGNIS,8,.3f); aqua.accept(EssenceType.AQUA,8,.7f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==8,"Both branches run in one pulse");
        c.assertTrue(fireJar.contents().equals(of(EssenceType.IGNIS,4,.3f)),"Fire reaches only its branch");
        c.assertTrue(waterJar.contents().equals(of(EssenceType.AQUA,4,.7f)),"Water reaches only its branch");
        c.assertTrue(firePipe.flowing()==EssenceType.IGNIS && waterPipe.flowing()==EssenceType.AQUA,
            "Each active branch shows its own latest essence");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void routeCanBypassABlockedSegmentWithoutLightingIt(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        for (int x=2; x<=4; x++) c.setBlockState(x,2,3,ModBlocks.ALCHEMY_PIPE);
        for (int x=2; x<=4; x++) c.setBlockState(x,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(5,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var blocked=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p.east());
        var alternate=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p.south().east());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(3));
        blocked.setFilter(EssenceType.AQUA); source.accept(EssenceType.IGNIS,6,.6f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==4,"An alternative path is used");
        c.assertTrue(jar.contents().total()==4 && source.amount()==2,"Bypass conserves essence");
        c.assertFalse(blocked.active(),"Blocked branch does not falsely animate");
        c.assertTrue(alternate.active() && alternate.flowing()==EssenceType.IGNIS,"Actual path animates");
        c.setBlockState(3,2,4,net.minecraft.block.Blocks.AIR);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0,"No allowed path means backpressure");
        c.assertTrue(jar.contents().total()==4 && source.amount()==2,"Blocked material remains in the source");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void filtersPersistSyncAndAllowDirectSelection(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        BlockPos pos=c.getAbsolutePos(new BlockPos(2,2,3));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pos);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        ItemStack crystal=new ItemStack(net.alkahest.registry.ModItems.CRYSTALS.get(EssenceType.MOTUS),3);
        player.setStackInHand(Hand.MAIN_HAND,crystal);
        var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
        c.getWorld().getBlockState(pos).onUseWithItem(crystal,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(pipe.filter()==EssenceType.MOTUS && crystal.getCount()==3,"Crystal selects filter without being spent");
        var replica=new AlchemyPipeBlockEntity(pos,pipe.getCachedState());
        replica.readComponentlessNbt(pipe.toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.filter()==EssenceType.MOTUS && pipe.toUpdatePacket()!=null,"Filter survives NBT and syncs");
        c.getWorld().getBlockState(pos).onUseWithItem(crystal,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(pipe.filter()==null && crystal.getCount()==3,"The same crystal clears the filter");
        player.setSneaking(true);
        var wand=new ItemStack(net.alkahest.registry.ModItems.WANDS.get(EssenceType.ORDO));
        player.setStackInHand(Hand.MAIN_HAND,wand);
        wand.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
        c.assertTrue(pipe.filter()==EssenceType.IGNIS
            && wand.get(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE)==100,
            "Sneak-use wand cycles without spending charge");
        var amulet=new ItemStack(net.alkahest.registry.ModItems.ALKAHEST_AMULET);
        player.setStackInHand(Hand.MAIN_HAND,amulet);
        amulet.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
        c.assertTrue(pipe.filter()==EssenceType.AQUA
            && amulet.get(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE)==100,
            "Amulet also cycles without spending charge");
        pipe.setFilter(EssenceType.SPIRITUS); pipe.cycleFilter(player);
        c.assertTrue(pipe.filter()==null,"Cycle wraps to unrestricted without admitting Vacuum");
        pipe.cycleFilter(player);
        c.assertTrue(pipe.filter()==EssenceType.IGNIS,"Cycle starts at the first ordinary essence");
        pipe.setFilter(null);
        for (EssenceType type : EssenceType.values()) {
            if (type==EssenceType.VACUUM) continue;
            pipe.cycleFilter(player);
            c.assertTrue(pipe.filter()==type,"Cycle visits every ordinary essence in order");
        }
        pipe.cycleFilter(player);
        c.assertTrue(pipe.filter()==null,"Cycle returns to unrestricted after all ordinary types");
        var oldNbt=new net.minecraft.nbt.NbtCompound(); oldNbt.putString("Filter","vacuum");
        replica.readComponentlessNbt(oldNbt,c.getWorld().getRegistryManager());
        c.assertTrue(replica.filter()==null,"Invalid old or unsupported filter safely defaults to unrestricted");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unreachablePreferredJarDoesNotStarveAccessibleEmptyJar(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(2,2,5,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var barred=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p.east());
        var filled=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        var empty=(JarBlockEntity)c.getWorld().getBlockEntity(p.south(2));
        filled.setContents(of(EssenceType.IGNIS,3,.4f)); barred.setFilter(EssenceType.AQUA);
        source.accept(EssenceType.IGNIS,5,.6f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==4,"A reachable empty jar wins over an inaccessible filled jar");
        c.assertTrue(filled.contents().total()==3 && empty.contents().total()==4 && source.amount()==1,
            "No essence crosses a blocked segment or disappears");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void searchesEveryEntryPointOfAnEndpoint(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(2,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,4,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(3,2,3));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var barred=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p);
        var target=(JarBlockEntity)c.getWorld().getBlockEntity(p.south().east());
        barred.setFilter(EssenceType.AQUA); source.accept(EssenceType.IGNIS,4,.5f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==4,"Another entry next to the source can reach the jar");
        c.assertTrue(!barred.active() && target.contents().total()==4,"The disallowed entry stays unused");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void networkTicksOnlyOnceEvenIfSegmentsAreVisitedInAnyOrder(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,3,ModBlocks.JAR);
        BlockPos first=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(first.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(first.east(2));
        source.accept(EssenceType.IGNIS,12,.5f);
        c.assertTrue(AlchemyPipeBlockEntity.pulseOncePerTick(c.getWorld(),first.east())==4,
            "A non-leader segment may scan the network first");
        c.assertTrue(AlchemyPipeBlockEntity.pulseOncePerTick(c.getWorld(),first)==0,
            "The leader does not run the same network again that tick");
        c.assertTrue(source.amount()==8 && jar.contents().total()==4,"Exactly one transfer occurred");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void joiningAnAlreadyVisitedNetworkDefersItsNextTransfer(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,4,ModBlocks.JAR);
        BlockPos first=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(first.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(first.east(2).south());
        source.accept(EssenceType.IGNIS,8,.5f);
        c.assertTrue(AlchemyPipeBlockEntity.pulseOncePerTick(c.getWorld(),first)==0,"First isolated section has no target");
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        c.assertTrue(AlchemyPipeBlockEntity.pulseOncePerTick(c.getWorld(),first.east().south())==0,
            "Merging cannot cause a second transfer in the same tick");
        c.assertTrue(source.amount()==8 && jar.contents().total()==0,"Deferred merge retains both contents");
        c.complete();
    }

    /** Exhaust every filter arrangement of a 3x3 loop against an independent bitset flood fill. */
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyThreeByThreeFilterLayoutMatchesConnectivity(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(5,2,3,ModBlocks.JAR);
        for (int z=2; z<=4; z++) for (int x=2; x<=4; x++)
            c.setBlockState(x,2,z,ModBlocks.ALCHEMY_PIPE);
        BlockPos first=c.getAbsolutePos(new BlockPos(2,2,2));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(1,2,3)));
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(5,2,3)));
        for (int mask=0; mask<(1<<9); mask++) {
            for (int z=0; z<3; z++) for (int x=0; x<3; x++) {
                var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(first.add(x,0,z));
                pipe.setFilter((mask & (1<<(z*3+x)))==0 ? null : EssenceType.AQUA);
            }
            int reachable=(mask & (1<<3))==0 ? 1<<3 : 0;
            int prior;
            do {
                prior=reachable;
                for (int n=0; n<9; n++) {
                    if ((prior & (1<<n))==0) continue;
                    int x=n%3, z=n/3;
                    if (x>0) reachable |= 1<<(n-1);
                    if (x<2) reachable |= 1<<(n+1);
                    if (z>0) reachable |= 1<<(n-3);
                    if (z<2) reachable |= 1<<(n+3);
                }
                reachable &= ~mask;
            } while (reachable!=prior);
            boolean expected=(reachable & (1<<5))!=0;
            source.drain(source.amount()); source.accept(EssenceType.IGNIS,1,.5f);
            jar.setContents(JarContentsComponent.EMPTY);
            int moved=AlchemyPipeBlockEntity.pulse(c.getWorld(),first);
            c.assertTrue(moved==(expected ? 1 : 0),"Filter layout " + mask + " has the expected reachability");
            c.assertTrue(source.amount()+jar.contents().total()==1,"Layout " + mask + " conserves its essence");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mergedNetworkResumesOnFollowingTicks(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,4,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(4,2,4,ModBlocks.JAR);
        BlockPos first=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(first.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(first.east(2).south());
        source.accept(EssenceType.IGNIS,8,.5f);
        AlchemyPipeBlockEntity.pulseOncePerTick(c.getWorld(),first);
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        AlchemyPipeBlockEntity.pulseOncePerTick(c.getWorld(),first.east().south());
        c.runAtTick(8,()->{
            c.assertTrue(source.amount()==0 && jar.contents().total()==8,
                "A merged network resumes automatically on subsequent pulses, without loss");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oversizedNetworkDoesNotPartiallyRunAndRecoversAtLimit(TestContext c) {
        for (int y=10; y<=11; y++) for (int z=2; z<=6; z++) for (int x=2; x<=6; x++)
            c.setBlockState(x,y,z,ModBlocks.ALCHEMY_PIPE);
        for (int z=2; z<=4; z++) for (int x=2; x<=6; x++)
            c.setBlockState(x,12,z,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(1,10,2,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(7,10,2,ModBlocks.JAR);
        BlockPos first=c.getAbsolutePos(new BlockPos(2,10,2));
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(first.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(first.east(5));
        source.accept(EssenceType.IGNIS,8,.5f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),first)==0,"65 segments do not partially transfer");
        c.assertTrue(source.amount()==8 && jar.contents().total()==0,"An oversized network retains all essence");
        c.setBlockState(6,12,4,net.minecraft.block.Blocks.AIR);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),first)==4,"A connected 64-segment network works");
        c.assertTrue(source.amount()==4 && jar.contents().total()==4,"Transfer after recovery is exact");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=80)
    public void minedFilterSurvivesInventoryAndPlacementAndStillRoutes(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        var world=c.getWorld(); BlockPos pos=c.getAbsolutePos(new BlockPos(2,2,3));
        var pipe=(AlchemyPipeBlockEntity)world.getBlockEntity(pos);
        pipe.setFilter(EssenceType.HERBA);
        world.breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2), e->e.getStack().isOf(ModBlocks.ALCHEMY_PIPE.asItem()));
            c.assertTrue(drops.size()==1,"One pipe drops on ordinary break");
            ItemStack item=drops.getFirst().getStack().copy(); drops.getFirst().discard();
            c.assertTrue(item.get(net.alkahest.registry.ModDataComponents.PIPE_FILTER)==EssenceType.HERBA,
                "The actual dropped pipe retains its filter");
            var slots=net.minecraft.util.collection.DefaultedList.ofSize(1,ItemStack.EMPTY); slots.set(0,item);
            var nbt=new net.minecraft.nbt.NbtCompound();
            net.minecraft.inventory.Inventories.writeNbt(nbt,slots,world.getRegistryManager());
            var loaded=net.minecraft.util.collection.DefaultedList.ofSize(1,ItemStack.EMPTY);
            net.minecraft.inventory.Inventories.readNbt(nbt,loaded,world.getRegistryManager());
            item=loaded.get(0);
            c.assertTrue(item.get(net.alkahest.registry.ModDataComponents.PIPE_FILTER)==EssenceType.HERBA,
                "The filter survives inventory save and load");
            BlockPos base=c.getAbsolutePos(new BlockPos(5,1,3)); world.setBlockState(base,net.minecraft.block.Blocks.STONE.getDefaultState());
            var player=c.createMockPlayer(GameMode.SURVIVAL); player.setStackInHand(Hand.MAIN_HAND,item);
            var hit=new BlockHitResult(Vec3d.ofCenter(base),Direction.UP,base,false);
            c.assertTrue(((net.minecraft.item.BlockItem)item.getItem()).place(new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,item,hit)).isAccepted(),
                "The saved pipe can be placed again");
            BlockPos placed=base.up(); var restored=(AlchemyPipeBlockEntity)world.getBlockEntity(placed);
            c.assertTrue(restored.filter()==EssenceType.HERBA && !restored.active(),"Placement restores the filter, not temporary flow");
            world.setBlockState(placed.west(),ModBlocks.DISTILLERY_BELLOWS.getDefaultState());
            world.setBlockState(placed.east(),ModBlocks.JAR.getDefaultState());
            var source=(DistilleryBellowsBlockEntity)world.getBlockEntity(placed.west());
            var jar=(JarBlockEntity)world.getBlockEntity(placed.east());
            source.accept(EssenceType.AQUA,1,.4f);
            c.assertTrue(AlchemyPipeBlockEntity.pulse(world,placed)==0 && source.amount()==1,"Restored filter blocks another type without loss");
            source.drain(1); source.accept(EssenceType.HERBA,1,.6f);
            c.assertTrue(AlchemyPipeBlockEntity.pulse(world,placed)==1 && jar.contents().total()==1,"Restored filter allows its own type");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void portablePipeStackComponentsAndInvalidFilterAreSafe(TestContext c) {
        ItemStack plain=new ItemStack(ModBlocks.ALCHEMY_PIPE,8);
        ItemStack fire=new ItemStack(ModBlocks.ALCHEMY_PIPE,3);
        ItemStack water=new ItemStack(ModBlocks.ALCHEMY_PIPE,2);
        fire.set(net.alkahest.registry.ModDataComponents.PIPE_FILTER,EssenceType.IGNIS);
        water.set(net.alkahest.registry.ModDataComponents.PIPE_FILTER,EssenceType.AQUA);
        c.assertTrue(plain.getMaxCount()==64 && fire.getMaxCount()==64,"All pipes retain the normal stack limit");
        c.assertFalse(ItemStack.areItemsAndComponentsEqual(plain,fire),"Filtered and plain stacks do not merge");
        c.assertFalse(ItemStack.areItemsAndComponentsEqual(fire,water),"Different filters do not merge");
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(fire,fire.copy()),"Matching filters can stack");
        ItemStack unsupported=new ItemStack(ModBlocks.ALCHEMY_PIPE);
        unsupported.set(net.alkahest.registry.ModDataComponents.PIPE_FILTER,EssenceType.VACUUM);
        BlockPos base=c.getAbsolutePos(new BlockPos(4,1,3));
        c.getWorld().setBlockState(base,net.minecraft.block.Blocks.STONE.getDefaultState());
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setStackInHand(Hand.MAIN_HAND,unsupported);
        var hit=new BlockHitResult(Vec3d.ofCenter(base),Direction.UP,base,false);
        c.assertTrue(((net.minecraft.item.BlockItem)unsupported.getItem()).place(new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,unsupported,hit)).isAccepted(),"Invalid old item can be placed safely");
        var placed=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(base.up());
        c.assertTrue(placed.filter()==null,"Unsupported Vacuum filter is cleared on placement");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void placingFilteredPipeAgainstJarPreservesItsSetting(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.JAR); BlockPos jar=c.getAbsolutePos(new BlockPos(2,2,3));
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setPosition(jar.getX()-2,jar.getY(),jar.getZ());
        var stack=new ItemStack(ModBlocks.ALCHEMY_PIPE,3);
        stack.set(net.alkahest.registry.ModDataComponents.PIPE_FILTER,EssenceType.ORDO);
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(jar).add(.5,0,0),Direction.EAST,jar,false);
        c.getWorld().getBlockState(jar).onUseWithItem(stack,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(jar.east());
        c.assertTrue(pipe!=null && pipe.filter()==EssenceType.ORDO,"The normal click-on-jar placement restores the filter");
        c.assertTrue(stack.getCount()==2 && stack.get(net.alkahest.registry.ModDataComponents.PIPE_FILTER)==EssenceType.ORDO,
            "The remaining stack retains its filter");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=80)
    public void unfilteredPipeDropsAsAnOrdinaryStackableItem(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        var world=c.getWorld(); BlockPos pos=c.getAbsolutePos(new BlockPos(2,2,3));
        world.breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2), e->e.getStack().isOf(ModBlocks.ALCHEMY_PIPE.asItem()));
            c.assertTrue(drops.size()==1,"The plain pipe drops normally");
            var dropped=drops.getFirst().getStack();
            c.assertTrue(dropped.get(net.alkahest.registry.ModDataComponents.PIPE_FILTER)==null
                && ItemStack.areItemsAndComponentsEqual(dropped,new ItemStack(ModBlocks.ALCHEMY_PIPE)),
                "Old and unconfigured pipes remain compatible with crafted stacks");
            drops.getFirst().discard(); c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redstoneModesAreOptInAndNeverLoseEssence(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,2,3,ModBlocks.JAR);
        c.setBlockState(2,1,3,net.minecraft.block.Blocks.REDSTONE_BLOCK);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p);
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east());
        source.accept(EssenceType.AQUA,20,.37f);
        c.assertTrue(pipe.valveMode()==net.alkahest.block.entity.PipeValveMode.OPEN && pipe.valveOpen(), "Old pipes ignore redstone by default");
        c.assertTrue(AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==4, "Powered normal pipe still works");
        pipe.setValveMode(net.alkahest.block.entity.PipeValveMode.CLOSE_WHEN_POWERED);
        c.assertTrue(!pipe.valveOpen() && AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0
            && source.amount()==16 && jar.contents().total()==4, "Powered close valve stops without spending essence");
        c.setBlockState(2,1,3,net.minecraft.block.Blocks.AIR);
        c.assertTrue(pipe.valveOpen() && AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==4, "Removing power opens this valve");
        pipe.setValveMode(net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED);
        c.assertTrue(!pipe.valveOpen() && AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0, "Inverse valve is normally closed");
        c.setBlockState(2,1,3,net.minecraft.block.Blocks.REDSTONE_BLOCK);
        c.assertTrue(pipe.valveOpen() && AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==4, "Power opens inverse valve");
        pipe.setValveMode(net.alkahest.block.entity.PipeValveMode.CLOSED);
        c.assertTrue(!pipe.valveOpen() && AlchemyPipeBlockEntity.pulse(c.getWorld(),p)==0, "Manual shutoff ignores power");
        c.assertTrue(source.amount()==8 && jar.contents().total()==12
            && Math.abs(jar.contents().contents().getFirst().stability()-.37f)<.001f, "Every moved unit and its stability are preserved");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void valvesAndFiltersUseAlternativeRoutes(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.DISTILLERY_BELLOWS);
        for(int x=2;x<=4;x++) { c.setBlockState(x,2,3,ModBlocks.ALCHEMY_PIPE); c.setBlockState(x,2,4,ModBlocks.ALCHEMY_PIPE); }
        c.setBlockState(5,2,3,ModBlocks.JAR);
        var world=c.getWorld(); BlockPos start=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(DistilleryBellowsBlockEntity)world.getBlockEntity(start.west());
        var jar=(JarBlockEntity)world.getBlockEntity(start.east(3));
        var direct=(AlchemyPipeBlockEntity)world.getBlockEntity(start.east());
        var alternate=(AlchemyPipeBlockEntity)world.getBlockEntity(start.east().south());
        direct.setValveMode(net.alkahest.block.entity.PipeValveMode.CLOSED);
        alternate.setFilter(EssenceType.IGNIS);
        source.accept(EssenceType.AQUA,8,.4f);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(world,start)==0 && source.amount()==8 && jar.contents().total()==0,
            "A closed shortcut and an incompatible alternate filter block without loss");
        alternate.setFilter(EssenceType.AQUA);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(world,start)==4 && alternate.active() && !direct.active(),
            "An allowed detour is chosen and only the actual route animates");
        direct.setValveMode(net.alkahest.block.entity.PipeValveMode.OPEN);
        alternate.setValveMode(net.alkahest.block.entity.PipeValveMode.CLOSED);
        c.assertTrue(AlchemyPipeBlockEntity.pulse(world,start)==4 && jar.contents().total()==8 && source.empty(),
            "Opening the shortcut and closing the detour resumes the remaining transfer");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void realPipeTickerRespondsToRedstone(TestContext c) {
        int y=110;
        c.setBlockState(1,y,3,ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(2,y,3,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(3,y,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,y,3));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p);
        var source=(DistilleryBellowsBlockEntity)c.getWorld().getBlockEntity(p.west());
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(p.east());
        pipe.setValveMode(net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED);
        source.accept(EssenceType.ORDO,12,.5f);
        c.runAtTick(10,()->{
            c.assertTrue(source.amount()==12 && jar.contents().total()==0, "Real world ticker respects an unpowered valve");
            c.setBlockState(2,y-1,3,net.minecraft.block.Blocks.REDSTONE_BLOCK);
        });
        c.runAtTick(20,()->{
            c.assertTrue(source.empty() && jar.contents().total()==12, "Real world ticker resumes after redstone opens it");
            c.setBlockState(2,y-1,3,net.minecraft.block.Blocks.AIR);
            source.accept(EssenceType.ORDO,4,.5f);
        });
        c.runAtTick(30,()->{
            c.assertTrue(source.amount()==4 && jar.contents().total()==12, "Removing signal stops later transfers");
            c.setBlockState(1,y,3,net.minecraft.block.Blocks.AIR);
            c.setBlockState(2,y,3,net.minecraft.block.Blocks.AIR);
            c.setBlockState(3,y,3,net.minecraft.block.Blocks.AIR);
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void valveControlsAndConfigurationSurviveMining(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        var world=c.getWorld(); BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var pipe=(AlchemyPipeBlockEntity)world.getBlockEntity(p);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        player.setSneaking(true);
        var hit=new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false);
        world.getBlockState(p).onUse(world,player,hit);
        c.assertTrue(pipe.valveMode()==net.alkahest.block.entity.PipeValveMode.CLOSE_WHEN_POWERED, "Sneak empty-hand click selects the first valve mode");
        world.getBlockState(p).onUse(world,player,hit);
        c.assertTrue(pipe.valveMode()==net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED, "Repeated clicks cycle valve modes");
        pipe.setFilter(EssenceType.ORDO);
        var replica=new AlchemyPipeBlockEntity(p,pipe.getCachedState());
        replica.readComponentlessNbt(pipe.toInitialChunkDataNbt(world.getRegistryManager()),world.getRegistryManager());
        c.assertTrue(replica.valveMode()==pipe.valveMode() && replica.filter()==EssenceType.ORDO && pipe.toUpdatePacket()!=null,
            "The mode and filter survive NBT and are included in client updates");
        world.breakBlock(p,true);
        c.runAtTick(5,()->{
            var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(p).expand(2),
                e->e.getStack().isOf(ModBlocks.ALCHEMY_PIPE.asItem()));
            c.assertTrue(drops.size()==1, "Mining returns one configured pipe");
            var item=drops.getFirst().getStack().copy(); drops.getFirst().discard();
            c.assertTrue(item.get(net.alkahest.registry.ModDataComponents.PIPE_VALVE)==net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED
                && item.get(net.alkahest.registry.ModDataComponents.PIPE_FILTER)==EssenceType.ORDO, "The item retains both settings");
            c.assertFalse(ItemStack.areItemsAndComponentsEqual(item,new ItemStack(ModBlocks.ALCHEMY_PIPE)), "Differently configured pipes do not stack");
            var slots=net.minecraft.util.collection.DefaultedList.ofSize(1,ItemStack.EMPTY); slots.set(0,item);
            var nbt=new net.minecraft.nbt.NbtCompound();
            net.minecraft.inventory.Inventories.writeNbt(nbt,slots,world.getRegistryManager());
            var loaded=net.minecraft.util.collection.DefaultedList.ofSize(1,ItemStack.EMPTY);
            net.minecraft.inventory.Inventories.readNbt(nbt,loaded,world.getRegistryManager());
            var restoredItem=loaded.get(0);
            BlockPos base=c.getAbsolutePos(new BlockPos(5,1,3)); world.setBlockState(base,net.minecraft.block.Blocks.STONE.getDefaultState());
            player.setSneaking(false); player.setStackInHand(Hand.MAIN_HAND,restoredItem);
            var placement=new BlockHitResult(Vec3d.ofCenter(base),Direction.UP,base,false);
            c.assertTrue(((net.minecraft.item.BlockItem)restoredItem.getItem()).place(new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,restoredItem,placement)).isAccepted(), "Saved configured pipe can be placed");
            var restored=(AlchemyPipeBlockEntity)world.getBlockEntity(base.up());
            c.assertTrue(restored.valveMode()==net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED && restored.filter()==EssenceType.ORDO,
                "Placement restores both settings after inventory save");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void valveCycleAndOldDataRemainCompatible(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p);
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setSneaking(true);
        var hit=new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false);
        for (var mode : new net.alkahest.block.entity.PipeValveMode[]{
            net.alkahest.block.entity.PipeValveMode.CLOSE_WHEN_POWERED,
            net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED,
            net.alkahest.block.entity.PipeValveMode.CLOSED,
            net.alkahest.block.entity.PipeValveMode.OPEN}) {
            c.getWorld().getBlockState(p).onUse(c.getWorld(),player,hit);
            c.assertTrue(pipe.valveMode()==mode, "Empty-hand interaction cycles all valve modes in order");
        }
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.STICK));
        c.getWorld().getBlockState(p).onUse(c.getWorld(),player,hit);
        c.assertTrue(pipe.valveMode()==net.alkahest.block.entity.PipeValveMode.OPEN, "A held item does not accidentally cycle the valve");
        var old=pipe.createNbt(c.getWorld().getRegistryManager()); old.remove("Valve");
        var restored=new AlchemyPipeBlockEntity(p,pipe.getCachedState());
        restored.readComponentlessNbt(old,c.getWorld().getRegistryManager());
        c.assertTrue(restored.valveMode()==net.alkahest.block.entity.PipeValveMode.OPEN, "Old saves default to unrestricted mode");
        old.putString("Valve","unknown"); restored.readComponentlessNbt(old,c.getWorld().getRegistryManager());
        c.assertTrue(restored.valveMode()==net.alkahest.block.entity.PipeValveMode.OPEN, "Unknown stored modes recover safely");
        c.complete();
    }

}
