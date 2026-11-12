package net.alkahest.block.entity;

import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModEffects;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.List;

/** Machine ticks use the same path as production with a deterministic effect and world clock. */
public final class MidasStabilizerGameTests implements FabricGameTest {
    private static final int Y = 52;
    private BlockPos receiverPos(TestContext c) { return c.getAbsolutePos(new BlockPos(2, Y, 2)); }
    private StabilizerReceiverBlockEntity receiver(TestContext c) { return (StabilizerReceiverBlockEntity)c.getWorld().getBlockEntity(receiverPos(c)); }
    private StabilizerCoreBlockEntity core(TestContext c) { return (StabilizerCoreBlockEntity)c.getWorld().getBlockEntity(receiverPos(c).up()); }
    private JarBlockEntity target(TestContext c) { return (JarBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(5, Y, 2))); }
    private JarContentsComponent essence(EssenceType type, int amount, float stability) {
        return new JarContentsComponent(List.of(new EssenceStack(type, amount, stability)));
    }
    private void build(TestContext c) {
        c.setBlockState(2, Y, 2, ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(2, Y + 1, 2, ModBlocks.STABILIZER_CORE);
        c.setBlockState(2, Y + 2, 2, ModBlocks.STABILIZER_CREST);
        c.setBlockState(5, Y, 2, ModBlocks.JAR);
    }
    private void tick(TestContext c, boolean midas, int time) {
        StabilizerReceiverBlockEntity.tickWithMidas(c.getWorld(), receiverPos(c), receiver(c), midas, time);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boostedAndNormalSpeedAndStability(TestContext c) {
        build(c);
        receiver(c).setContents(essence(EssenceType.ORDO, 10, .5f));
        core(c).setActivated(true);
        for (int t = 1; t <= 166; t++) tick(c, true, t);
        c.assertTrue(receiver(c).progress() == 199 && target(c).contents().total() == 0,
            "The accelerated cycle is not delivered before 200 progress");
        tick(c, true, 167);
        c.assertTrue(receiver(c).contents().total() == 0 && target(c).contents().total() == 10,
            "The Midas cycle delivers the original ten units in 167 ticks");
        c.assertTrue(target(c).contents().contents().getFirst().stability() == 1f, "Fresh output reaches 100% stability");
        target(c).setContents(JarContentsComponent.EMPTY);
        receiver(c).setContents(essence(EssenceType.ORDO, 10, .5f));
        for (int t = 1; t <= 199; t++) tick(c, false, t);
        c.assertTrue(receiver(c).progress() == 199 && target(c).contents().total() == 0, "Normal cycle still requires 200 ticks");
        tick(c, false, 200);
        c.assertTrue(target(c).contents().total() == 10
            && Math.abs(target(c).contents().contents().getFirst().stability() - .9f) < .001f,
            "Without Midas the original 90% stability is preserved");
        c.setBlockState(5, Y, 2, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void effectAtDeliveryDeterminesStability(TestContext c) {
        build(c);
        receiver(c).setContents(essence(EssenceType.AQUA, 4, .5f));
        core(c).setActivated(true);
        for (int t = 1; t <= 150; t++) tick(c, true, t);
        for (int t = 151; t <= 170; t++) tick(c, false, t);
        c.assertTrue(target(c).contents().total() == 4 && Math.abs(target(c).contents().contents().getFirst().stability() - .9f) < .001f,
            "An effect that expires before delivery no longer boosts stability");
        target(c).setContents(JarContentsComponent.EMPTY);
        receiver(c).setContents(essence(EssenceType.AQUA, 4, .5f));
        for (int t = 1; t <= 199; t++) tick(c, false, t);
        tick(c, true, 200);
        c.assertTrue(target(c).contents().total() == 4 && target(c).contents().contents().getFirst().stability() == 1f,
            "Midas arriving on the delivery tick boosts that batch");
        c.setBlockState(5, Y, 2, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inactiveOrBlockedCannotConsumeAndRecoveryIsExact(TestContext c) {
        build(c);
        receiver(c).setContents(essence(EssenceType.IGNIS, 12, .5f));
        target(c).setContents(essence(EssenceType.AQUA, 100, .5f));
        for (int t = 1; t <= 250; t++) tick(c, true, t);
        c.assertTrue(receiver(c).progress() == 0 && receiver(c).contents().total() == 12, "Midas cannot activate an inactive tower");
        core(c).setActivated(true);
        for (int t = 1; t <= 600; t++) tick(c, true, t);
        c.assertTrue(receiver(c).contents().total() == 12 && target(c).contents().total() == 100
            && target(c).contents().contents().getFirst().type() == EssenceType.AQUA,
            "A full incompatible jar blocks safely, without mixing or losing essence");
        target(c).setContents(JarContentsComponent.EMPTY);
        for (int t = 601; t <= 768; t++) tick(c, true, t);
        c.assertTrue(receiver(c).contents().total() == 0 && target(c).contents().total() == 12,
            "Unblocking delivers exactly the waiting units");
        c.setBlockState(5, Y, 2, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void partialCapacityKeepsRemainderAndMergesStability(TestContext c) {
        build(c);
        target(c).setContents(essence(EssenceType.ORDO, 95, .5f));
        receiver(c).setContents(essence(EssenceType.ORDO, 10, .5f));
        core(c).setActivated(true);
        for (int t = 1; t <= 167; t++) tick(c, true, t);
        c.assertTrue(target(c).contents().total() == 100 && receiver(c).contents().total() == 5,
            "Only five units fit and the remainder stays in the receiver");
        c.assertTrue(Math.abs(target(c).contents().contents().getFirst().stability() - .525f) < .001f,
            "Fresh 100% essence averages with existing lower-stability contents, not overwriting them");
        var saved = receiver(c).createNbt(c.getWorld().getRegistryManager());
        var restored = new StabilizerReceiverBlockEntity(receiverPos(c), receiver(c).getCachedState());
        restored.readComponentlessNbt(saved, c.getWorld().getRegistryManager());
        c.assertTrue(restored.contents().total() == 5, "Partial remainder survives save/load");
        target(c).setContents(JarContentsComponent.EMPTY);
        for (int t = 168; t <= 335; t++) tick(c, true, t);
        c.assertTrue(receiver(c).contents().total() == 0 && target(c).contents().total() == 5
            && target(c).contents().contents().getFirst().stability() == 1f, "The exact remainder can be recovered into a fresh jar");
        c.setBlockState(5, Y, 2, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rangeUsesCoreAndDoesNotStack(TestContext c) {
        build(c);
        BlockPos pos = receiverPos(c).up();
        var a = c.createMockPlayer(GameMode.SURVIVAL);
        var b = c.createMockPlayer(GameMode.SURVIVAL);
        a.addStatusEffect(new StatusEffectInstance(ModEffects.MIDAS_TOUCH, 3600));
        b.addStatusEffect(new StatusEffectInstance(ModEffects.MIDAS_TOUCH, 3600));
        a.setPosition(pos.getX()+8.5, pos.getY()+.5, pos.getZ()+.5);
        b.setPosition(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5);
        c.assertTrue(MidasTouchEffect.nearby(List.of(a), pos), "A player exactly eight blocks from the core qualifies");
        a.setPosition(pos.getX()+8.51, pos.getY()+.5, pos.getZ()+.5);
        c.assertFalse(MidasTouchEffect.nearby(List.of(a), pos), "A player outside the sphere does not qualify");
        c.assertTrue(MidasTouchEffect.progressStep(MidasTouchEffect.nearby(List.of(a, b), pos), 5) == 2,
            "Multiple players grant at most one extra tick");
        c.setBlockState(5, Y, 2, Blocks.AIR);
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void manualTopUpPreservesCurrentWorkAndWeightedStability(TestContext c) {
        build(c);
        receiver(c).setContents(essence(EssenceType.AQUA, 4, .4f));
        core(c).setActivated(true);
        for(int t=1;t<=40;t++) tick(c,false,t);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var jar=new net.minecraft.item.ItemStack(ModBlocks.JAR);
        net.alkahest.item.custom.JarItem.set(jar,essence(EssenceType.AQUA,4,.8f));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,jar);
        receiver(c).pourJar(player,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(receiver(c).progress()==40 && receiver(c).contents().total()==8
            && net.alkahest.item.custom.JarItem.contents(jar).total()==0,"Same-type manual input preserves progress and empties the jar");
        c.assertTrue(Math.abs(receiver(c).contents().contents().getFirst().stability()-.6f)<.001f,
            "The manual top-up keeps weighted stability");
        var replica=new StabilizerReceiverBlockEntity(receiverPos(c),receiver(c).getCachedState());
        replica.readComponentlessNbt(receiver(c).toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.progress()==40 && replica.contents().equals(receiver(c).contents()) && receiver(c).toUpdatePacket()!=null,
            "The current progress and contents are available to clients and saves");
        net.alkahest.item.custom.JarItem.set(jar,essence(EssenceType.ORDO,2,.5f));
        receiver(c).pourJar(player,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(receiver(c).progress()==40,"Another type after the current type does not reset it");
        net.alkahest.item.custom.JarItem.set(jar,essence(EssenceType.IGNIS,2,.5f));
        receiver(c).pourJar(player,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(receiver(c).progress()==0 && receiver(c).contents().contents().getFirst().type()==EssenceType.IGNIS,
            "Adding a type that becomes first starts its own cycle");
        c.setBlockState(5,Y,2,Blocks.AIR); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void finishedBatchWaitsPersistsAndUsesMidasAtDelivery(TestContext c) {
        build(c);
        receiver(c).setContents(essence(EssenceType.IGNIS,10,.5f));
        target(c).setContents(essence(EssenceType.AQUA,100,.5f));
        core(c).setActivated(true);
        for(int t=1;t<=200;t++) tick(c,false,t);
        c.assertTrue(receiver(c).progress()==200 && receiver(c).waitingForOutput(),"A finished batch waits for an available jar");
        for(int t=201;t<=499;t++) tick(c,true,t);
        c.assertTrue(receiver(c).progress()==200 && receiver(c).contents().total()==10 && target(c).contents().total()==100,
            "Long blockage neither repeats work nor loses or mixes essence");
        var replica=new StabilizerReceiverBlockEntity(receiverPos(c),receiver(c).getCachedState());
        replica.readComponentlessNbt(receiver(c).createNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.waitingForOutput() && replica.progress()==200 && replica.contents().equals(receiver(c).contents()),
            "The ready state survives world NBT and client data");
        target(c).setContents(JarContentsComponent.EMPTY);
        for(int t=501;t<=509;t++) tick(c,false,t);
        c.assertTrue(target(c).contents().total()==0 && receiver(c).progress()==200,"The ready batch waits for the next short retry");
        tick(c,false,510);
        c.assertTrue(target(c).contents().total()==10 && receiver(c).contents().total()==0 && receiver(c).progress()==0,
            "Unblocking delivers the waiting batch without another full cycle");
        c.assertTrue(Math.abs(target(c).contents().contents().getFirst().stability()-.90f)<.001,
            "Stability uses Midas at actual delivery, not during the wait");
        c.setBlockState(5,Y,2,Blocks.AIR); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void readyTopUpAndTypeChangeRespectBatchIdentity(TestContext c) {
        build(c);
        target(c).setContents(essence(EssenceType.ORDO,100,.5f));
        receiver(c).setContents(essence(EssenceType.AQUA,5,.5f));
        core(c).setActivated(true);
        for(int t=1;t<=200;t++) tick(c,false,t);
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var jar=new net.minecraft.item.ItemStack(ModBlocks.JAR);
        net.alkahest.item.custom.JarItem.set(jar,essence(EssenceType.AQUA,3,.5f));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,jar);
        receiver(c).pourJar(player,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(receiver(c).waitingForOutput() && receiver(c).contents().total()==8,
            "A top-up of the ready type keeps its completed work");
        net.alkahest.item.custom.JarItem.set(jar,essence(EssenceType.IGNIS,2,.5f));
        receiver(c).pourJar(player,net.minecraft.util.Hand.MAIN_HAND);
        c.assertTrue(!receiver(c).waitingForOutput() && receiver(c).progress()==0 && receiver(c).contents().total()==10,
            "An earlier new type cannot inherit another type's finished work");
        core(c).setActivated(false);
        tick(c,false,201);
        c.assertTrue(receiver(c).progress()==0 && receiver(c).contents().total()==10,"Deactivation leaves all essence intact");
        c.setBlockState(5,Y,2,Blocks.AIR); c.complete();
    }

}
