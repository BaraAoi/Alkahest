package net.alkahest.test;

import net.alkahest.block.custom.StabilizerReceiverBlock;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.StabilizerCoreBlockEntity;
import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import static net.alkahest.essence.EssenceType.*;

public class StabilizerGameTests implements FabricGameTest {
    private void build(TestContext c) {
        c.setBlockState(2, 2, 2, ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(2, 3, 2, ModBlocks.STABILIZER_CORE);
        c.setBlockState(2, 4, 2, ModBlocks.STABILIZER_CREST);
        c.setBlockState(5, 2, 2, ModBlocks.JAR);
        c.setBlockState(5, 2, 4, ModBlocks.JAR);
    }

    private StabilizerReceiverBlockEntity receiver(TestContext c) {
        return (StabilizerReceiverBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(2, 2, 2)));
    }

    private StabilizerCoreBlockEntity core(TestContext c) {
        return (StabilizerCoreBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    }

    private JarBlockEntity target(TestContext c) {
        return (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(5, 2, 2)));
    }

    private JarBlockEntity secondTarget(TestContext c) {
        return (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(5, 2, 4)));
    }

    private void tick(TestContext c, int times) {
        BlockPos pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
        StabilizerReceiverBlockEntity receiver = receiver(c);
        for (int i = 0; i < times; i++) StabilizerReceiverBlockEntity.tick(c.getWorld(), pos, receiver);
    }

    private void insertMixture(TestContext c) {
        ItemStack jar = new ItemStack(ModBlocks.JAR);
        JarItem.set(jar, new JarContentsComponent(List.of(
            new EssenceStack(IGNIS, 5, 0.6f), new EssenceStack(AQUA, 3, 0.6f))));
        receiver(c).setStored(jar);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sortsMixtureIntoJarsAtNinetyPercent(TestContext c) {
        build(c);
        StabilizerCoreBlockEntity core = core(c);
        StabilizerReceiverBlockEntity receiver = receiver(c);
        c.assertTrue(core.structureFormed(), "Receiver, core and crest must assemble the tower");
        insertMixture(c);
        c.assertTrue(c.getWorld().getBlockState(c.getAbsolutePos(new BlockPos(2, 2, 2)))
            .get(StabilizerReceiverBlock.OCCUPIED), "Occupied state must be visible to clients");

        tick(c, 250);
        c.assertTrue(receiver.progress() == 0 && target(c).contents().total() == 0,
            "An inactive tower must not sort or progress");

        core.setActivated(true);
        c.assertTrue(core.isActivated(), "Amulet activation must switch the tower on");

        tick(c, StabilizerReceiverBlockEntity.TICKS_PER_TYPE);
        JarBlockEntity target = target(c);
        c.assertTrue(target.contents().total() == 5, "First sorted type must arrive completely, got " + target.contents().total());
        c.assertTrue(target.contents().contents().getFirst().type() == IGNIS, "First entry must be sorted first");
        c.assertTrue(Math.abs(target.contents().contents().getFirst().stability() - 0.90f) < 0.001f,
            "Output stability must become 90%");
        c.assertTrue(receiver.inputContents().total() == 3, "The other essence must stay in the input jar");

        tick(c, StabilizerReceiverBlockEntity.TICKS_PER_TYPE);
        // One type per jar: the second type must go to the other empty jar.
        JarBlockEntity second = secondTarget(c);
        c.assertTrue(second.contents().total() == 3, "Second type must go into the other empty jar");
        c.assertTrue(second.contents().contents().getFirst().type() == AQUA, "Only Aqua may be sorted second");
        c.assertTrue(Math.abs(second.contents().contents().getFirst().stability() - 0.90f) < 0.001f,
            "Second jar must also receive 90% stability");
        c.assertTrue(target.contents().total() == 5, "The first jar must keep its single type");
        c.assertTrue(receiver.inputContents().total() == 0, "Input jar must be empty at the end");
        c.assertTrue(!receiver.stored().isEmpty(), "The jar item itself must remain in the receiver");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockedTowerKeepsInputSafe(TestContext c) {
        c.setBlockState(2, 2, 2, ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(2, 3, 2, ModBlocks.STABILIZER_CORE);
        c.setBlockState(2, 4, 2, ModBlocks.STABILIZER_CREST);
        StabilizerCoreBlockEntity core = core(c);
        insertMixture(c);
        core.setActivated(true);
        for (int i = 0; i < 3 * StabilizerReceiverBlockEntity.TICKS_PER_TYPE; i++)
            StabilizerReceiverBlockEntity.tick(c.getWorld(), c.getAbsolutePos(new BlockPos(2, 2, 2)), receiver(c));
        c.assertTrue(receiver(c).inputContents().total() == 8, "Without an output jar nothing may be lost");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removingCrestSwitchesTowerOff(TestContext c) {
        build(c);
        StabilizerCoreBlockEntity core = core(c);
        core.setActivated(true);
        c.assertTrue(core.isActivated(), "Tower must be active first");
        c.setBlockState(2, 4, 2, Blocks.AIR);
        core.syncState();
        c.assertFalse(core.structureFormed(), "Missing crest breaks the structure");
        c.assertFalse(core.isActivated(), "A broken tower must switch itself off");
        c.complete();
    }
}
