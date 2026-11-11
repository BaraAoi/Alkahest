package net.alkahest.test;

import net.alkahest.block.custom.TransmutatorPillarBlock;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
import net.alkahest.block.entity.TransmutatorPillarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** The 3x1x3 Transmutator: a ring of pillars feeds the core with essence taken straight from jars. */
public class TransmutatorGameTests implements FabricGameTest {
    private static final int CX = 3, CY = 2, CZ = 3;

    private static JarContentsComponent of(EssenceType type, int amount) {
        return new JarContentsComponent(List.of(new EssenceStack(type, amount, 0.4f)));
    }

    /** Builds the core with its eight pillars in one horizontal plane. */
    private static BlockPos build(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.TRANSMUTATOR_CORE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                c.setBlockState(CX + dx, CY, CZ + dz, ModBlocks.TRANSMUTATOR_PILLAR);
            }
        }
        return c.getAbsolutePos(new BlockPos(CX, CY, CZ));
    }

    private static JarBlockEntity jar(TestContext c, int x, int y, int z, EssenceType type, int amount) {
        c.setBlockState(x, y, z, ModBlocks.JAR);
        JarBlockEntity jar = (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        jar.setContents(of(type, amount));
        return jar;
    }

    private static void installInput(TestContext c, TransmutatorCoreBlockEntity core) {
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.COPPER_INGOT, 2));
        ItemStack amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        amulet.set(ModDataComponents.ESSENCE_CHARGE, 4);
        core.setStack(TransmutatorCoreBlockEntity.AMULET, amulet);
    }

    private static void run(World world, BlockPos corePos, int ticks) {
        TransmutatorCoreBlockEntity core = (TransmutatorCoreBlockEntity) world.getBlockEntity(corePos);
        for (int i = 0; i < ticks; i++) TransmutatorCoreBlockEntity.tick(world, corePos, core);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasSpeedCrossesEveryTransmutatorPrefillBoundary(TestContext c) {
        for (int phase = 0; phase < 5; phase++) {
            int progress = 0, ticks = 0, requests = 0;
            while (progress < TransmutatorCoreBlockEntity.DURATION) {
                int before = progress;
                progress = Math.min(TransmutatorCoreBlockEntity.DURATION, before +
                    net.alkahest.effect.MidasTouchEffect.progressStep(true, ticks + phase));
                if (before / 10 != progress / 10) requests++;
                ticks++;
            }
            c.assertTrue(ticks >= 83 && ticks <= 84, "The 100 progress ticks finish at Midas speed for every phase");
            c.assertTrue(requests == 10, "Every ten-progress essence request occurs at boosted speed");
        }
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        TransmutatorCoreBlockEntity.tick(c.getWorld(), pos, core);
        c.assertTrue(core.progress() == 1, "Without the effect the machine advances normally");
        c.complete();
    }

    /** One core alone is not a platform; the eight pillars make it one. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void platformNeedsAllEightPillars(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.TRANSMUTATOR_CORE);
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        c.assertFalse(core.formed(), "A lone core is not assembled");
        c.setBlockState(CX + 1, CY, CZ, ModBlocks.TRANSMUTATOR_PILLAR);
        c.setBlockState(CX - 1, CY, CZ, ModBlocks.TRANSMUTATOR_PILLAR);
        c.setBlockState(CX, CY, CZ + 1, ModBlocks.TRANSMUTATOR_PILLAR);
        c.assertFalse(core.formed(), "Four pillars are still not enough");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                c.setBlockState(CX + dx, CY, CZ + dz, ModBlocks.TRANSMUTATOR_PILLAR);
            }
        }
        c.assertTrue(core.formed(), "The full ring of eight pillars assembles the platform");
        c.complete();
    }

    /** Copper becomes gold while the essence flies out of the jars and the amulet pays a charge. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void transmutesCopperIntoGoldUsingJarEssence(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        JarBlockEntity metallum = jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 10);
        JarBlockEntity ordo = jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);

        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getItem() == Items.GOLD_INGOT,
            "Copper turns into gold");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 1,
            "Only one input is spent");
        c.assertTrue(metallum.contents().total() == 7, "Three metallum were taken from the jar");
        c.assertTrue(ordo.contents().total() == 8, "Two ordo were taken from the jar");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3,
            "The amulet pays one charge for the operation");
        c.complete();
    }

    /** Essence is drawn through the air into the pillars, not through pipes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pillarsPullEssenceStraightFromJars(TestContext c) {
        BlockPos pos = build(c);
        JarBlockEntity jar = jar(c, CX, CY + 2, CZ + 4, EssenceType.HERBA, 12);
        var pillar = (TransmutatorPillarBlockEntity) c.getWorld().getBlockEntity(
            c.getAbsolutePos(new BlockPos(CX, CY, CZ + 1)));
        c.assertTrue(pillar.request(c.getWorld(), EssenceType.HERBA, 6), "The pillar reaches a jar four blocks away");
        c.assertTrue(pillar.stored(EssenceType.HERBA) == 6, "The units arrive in the pillar buffer");
        c.assertTrue(jar.contents().total() == 6, "The jar hands over exactly what was asked");
        BlockPos pillarPos = c.getAbsolutePos(new BlockPos(CX, CY, CZ + 1));
        c.assertTrue(c.getWorld().getBlockState(pillarPos).get(TransmutatorPillarBlock.CHARGED),
            "A loaded pillar lights up");
        c.complete();
    }

    /** No essence around means no transmutation, and the input is never eaten. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waitsWithoutEssenceAndKeepsTheInput(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION * 2);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "Nothing is produced without essence");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "The input is untouched");
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_NO_ESSENCE, "The core reports the missing essence");
        c.complete();
    }

    /** A short jar leaves everything where it was: essence is only spent when the price is whole. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shortEssenceChangesNothing(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        JarBlockEntity metallum = jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 1);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "An incomplete price produces nothing");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "The input stays");
        c.assertTrue(metallum.contents().total() == 1, "The essence stays in the jar");
        c.complete();
    }

    /** Without a charged amulet the core does not run. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void needsAChargedAmulet(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.COPPER_INGOT));
        jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 10);
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_NO_AMULET, "The core asks for an amulet");
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "No amulet, no transmutation");
        c.complete();
    }

    /** A full output slot stops the machine instead of destroying the product. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullOutputBlocksTheMachine(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE, 64));
        jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getItem() == Items.STONE, "The output keeps its item");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getCount() == 64, "Nothing is added to a full slot");
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_BLOCKED, "The core reports a blocked output");
        c.complete();
    }

    /** Breaking the platform must not swallow what was inside the core. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void coreDropsItsContentsWhenBroken(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.dropContents(c.getWorld(), pos);
        c.assertTrue(core.isEmpty(), "The core is emptied when it is taken apart");
        c.complete();
    }
}
