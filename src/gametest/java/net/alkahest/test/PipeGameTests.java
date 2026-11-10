package net.alkahest.test;

import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
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
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void transferFiltersLoopsAndPersistence(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR); c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE.getDefaultState().with(AlchemyPipeBlock.FACING,Direction.EAST));
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE.getDefaultState().with(AlchemyPipeBlock.FACING,Direction.EAST)); c.setBlockState(4,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(p.west());
        var target=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(p.east());
        source.setContents(of(EssenceType.ORDO,3,.25f)); target.setContents(of(EssenceType.ORDO,2,.75f));
        c.assertTrue(AlchemyPipeBlockEntity.transfer(c.getWorld(),p,source),"Connected pipes must move one essence unit");
        c.assertTrue(source.contents().total()==2 && target.contents().total()==3,"Transfer conserves quantity");
        c.assertTrue(Math.abs(target.contents().contents().get(0).stability()-(1.75f/3))<.001,"Stability is weighted on receipt");
        pipe.nextFilter(); // Ignis rejects Ordo
        c.assertFalse(AlchemyPipeBlockEntity.transfer(c.getWorld(),p,source),"Filter must reject other essence");
        c.assertTrue(source.contents().total()==2 && target.contents().total()==3,"Rejected transfer is lossless");
        var saved=pipe.createNbt(c.getWorld().getRegistryManager());
        var loaded=new AlchemyPipeBlockEntity(p.east(),pipe.getCachedState()); loaded.readComponentlessNbt(saved,c.getWorld().getRegistryManager());
        c.assertTrue(loaded.filter()==EssenceType.IGNIS,"Filter persists on reload");
        while(pipe.filter()!=EssenceType.ORDO) pipe.nextFilter();
        c.assertTrue(AlchemyPipeBlockEntity.transfer(c.getWorld(),p,source),"Matching filter passes essence");
        target.setContents(of(EssenceType.ORDO,100,.7f));
        c.assertFalse(AlchemyPipeBlockEntity.transfer(c.getWorld(),p,source),"A full jar blocks transport without overflow");
        c.assertTrue(source.contents().total()==1 && target.contents().total()==100,"No loss when destination full");
        target.setContents(JarContentsComponent.EMPTY);
        c.getWorld().setBlockState(p.east(),c.getWorld().getBlockState(p.east()).with(AlchemyPipeBlock.FACING,Direction.WEST));
        c.assertFalse(AlchemyPipeBlockEntity.transfer(c.getWorld(),p,source),"A directed loop must stop");
        c.assertTrue(source.contents().total()==1 && target.contents().total()==0,"Loop does not delete or duplicate essence");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=70)
    public void automaticTransportAndConnection(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR); c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE.getDefaultState().with(AlchemyPipeBlock.FACING,Direction.EAST));
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE.getDefaultState().with(AlchemyPipeBlock.FACING,Direction.EAST)); c.setBlockState(4,2,3,ModBlocks.JAR);
        BlockPos p=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(p.west()); var target=(JarBlockEntity)c.getWorld().getBlockEntity(p.east(2));
        source.setContents(of(EssenceType.AQUA,8,.4f));
        c.assertTrue(c.getWorld().getBlockState(p).get(AlchemyPipeBlock.WEST) && c.getWorld().getBlockState(p).get(AlchemyPipeBlock.EAST),"Pipe connects to jar and neighboring pipe");
        c.runAtTick(35,()->{
            int n=target.contents().total();
            c.assertTrue(n>=3 && n<=4 && source.contents().total()+n==8,"Pipe transfers one unit every ten ticks and conserves contents");
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clickJarToPlacePipe(TestContext c) {
        c.setBlockState(2,2,3,ModBlocks.JAR); BlockPos jar=c.getAbsolutePos(new BlockPos(2,2,3));
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setPosition(jar.getX()-2,jar.getY(),jar.getZ());
        var pipes=new ItemStack(ModBlocks.ALCHEMY_PIPE,8); player.setStackInHand(Hand.MAIN_HAND,pipes);
        var hit=new BlockHitResult(Vec3d.ofCenter(jar).add(.5,0,0),Direction.EAST,jar,false);
        c.getWorld().getBlockState(jar).onUseWithItem(pipes,c.getWorld(),player,Hand.MAIN_HAND,hit);
        var state=c.getWorld().getBlockState(jar.east());
        c.assertTrue(state.isOf(ModBlocks.ALCHEMY_PIPE) && state.get(AlchemyPipeBlock.FACING)==Direction.EAST,"Regular click places pipe facing away from source jar");
        c.assertTrue(pipes.getCount()==7 && state.get(AlchemyPipeBlock.WEST),"Placement consumes one item and connects to jar");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wandCanConfigureDirectionAndSneakFilter(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE);
        BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pos);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var wand=new ItemStack(net.alkahest.registry.ModItems.WANDS.get(EssenceType.ORDO));
        player.setStackInHand(Hand.MAIN_HAND,wand);
        var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
        c.getWorld().getBlockState(pos).onUseWithItem(wand,c.getWorld(),player,Hand.MAIN_HAND,hit);
        c.assertTrue(c.getWorld().getBlockState(pos).get(AlchemyPipeBlock.FACING)==Direction.EAST,"Ordinary wand use changes output");
        player.setSneaking(true);
        var result=wand.useOnBlock(new net.minecraft.item.ItemUsageContext(player,Hand.MAIN_HAND,hit));
        c.assertTrue(result.isAccepted() && pipe.filter()==EssenceType.IGNIS,"Sneak-use through the item changes filter even when block interaction is bypassed");
        c.assertTrue(wand.getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE,0)==100,"Configuring pipe does not consume wand charge");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pipesNeverCreateNewMixtures(TestContext c) {
        c.setBlockState(1,2,3,ModBlocks.JAR);
        c.setBlockState(2,2,3,ModBlocks.ALCHEMY_PIPE.getDefaultState().with(AlchemyPipeBlock.FACING,Direction.EAST));
        c.setBlockState(3,2,3,ModBlocks.JAR);
        BlockPos pipe=c.getAbsolutePos(new BlockPos(2,2,3));
        var source=(JarBlockEntity)c.getWorld().getBlockEntity(pipe.west());
        var target=(JarBlockEntity)c.getWorld().getBlockEntity(pipe.east());
        source.setContents(of(EssenceType.AQUA,4,.2f)); target.setContents(of(EssenceType.ORDO,3,.7f));
        c.assertFalse(AlchemyPipeBlockEntity.transfer(c.getWorld(),pipe,source),"Incompatible destination must block transfer");
        c.assertTrue(source.contents().total()==4 && target.contents().total()==3,"Blocked transfer preserves both quantities");
        var legacy=new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA,4,.2f),new EssenceStack(EssenceType.ORDO,3,.7f)));
        target.setContents(legacy);
        c.assertFalse(AlchemyPipeBlockEntity.transfer(c.getWorld(),pipe,source),"Cannot add to a legacy mixed jar");
        c.assertTrue(target.contents().equals(legacy) && source.contents().total()==4,"Legacy destination remains untouched");
        source.setContents(legacy); target.setContents(of(EssenceType.ORDO,2,.5f));
        c.assertTrue(AlchemyPipeBlockEntity.transfer(c.getWorld(),pipe,source),"An old mixture can drain its compatible type");
        c.assertTrue(target.contents().contents().size()==1 && target.contents().contents().getFirst().type()==EssenceType.ORDO && target.contents().total()==3, "Destination never becomes mixed");
        c.assertTrue(source.contents().total()==6,"Legacy source loses just the accepted unit");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void monoclePipeFilterSyncsToClients(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.ALCHEMY_PIPE); BlockPos pos=c.getAbsolutePos(new BlockPos(3,2,3));
        var pipe=(AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pos); pipe.nextFilter();
        var replica=new AlchemyPipeBlockEntity(pos,pipe.getCachedState());
        replica.readComponentlessNbt(pipe.toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.filter()==EssenceType.IGNIS, "Initial chunk data includes filter");
        pipe.nextFilter(); var packet=pipe.toUpdatePacket();
        c.assertTrue(packet!=null && packet.getNbt()!=null, "Changing filter produces update data");
        replica.readComponentlessNbt(packet.getNbt(),c.getWorld().getRegistryManager());
        c.assertTrue(replica.filter()==EssenceType.AQUA, "Filter change reaches the client copy");
        c.complete();
    }

}
