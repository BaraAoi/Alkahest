package net.alkahest.test;

import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** The 3x3x3 Matrix: eight pillars on the corners, eighteen bricks on the shell, essence by air. */
public class MatrixGameTests implements FabricGameTest {
    private static final int CX = 3, CY = 2, CZ = 3;

    private static JarContentsComponent of(EssenceType type, int amount) {
        return new JarContentsComponent(List.of(new EssenceStack(type, amount, 0.5f)));
    }

    private static BlockPos build(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.MATRIX_CORE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    boolean corner = dx != 0 && dy != 0 && dz != 0;
                    c.setBlockState(CX + dx, CY + dy, CZ + dz,
                        corner ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
                }
            }
        }
        return c.getAbsolutePos(new BlockPos(CX, CY, CZ));
    }

    private static MatrixCoreBlockEntity core(TestContext c) {
        return (MatrixCoreBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(CX, CY, CZ)));
    }

    private static JarBlockEntity jar(TestContext c, int x, int y, int z, EssenceType type, int amount) {
        c.setBlockState(x, y, z, ModBlocks.JAR);
        JarBlockEntity jar = (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        jar.setContents(of(type, amount));
        return jar;
    }

    private static void run(World world, BlockPos pos, int ticks) {
        MatrixCoreBlockEntity core = (MatrixCoreBlockEntity) world.getBlockEntity(pos);
        for (int i = 0; i < ticks; i++) MatrixCoreBlockEntity.tick(world, pos, core);
    }

    /** A lone core is nothing; the cube needs four bricks in every middle layer and pillars on the corners. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cubeNeedsPillarsAndBricks(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.MATRIX_CORE);
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        var core = (MatrixCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        c.assertFalse(core.formed(), "A lone core is not a Matrix");
        build(c);
        c.assertTrue(core.formed(), "Eight pillars and eighteen bricks assemble the cube");
        c.setBlockState(CX + 1, CY, CZ, Blocks.STONE);
        c.assertFalse(core.formed(), "A stray stone in the shell breaks the cube");
        c.setBlockState(CX + 1, CY, CZ, ModBlocks.ALCHEMICAL_BRICK);
        c.assertTrue(core.formed(), "Repairing the shell restores the cube");
        c.complete();
    }

    /** String becomes three binding salts, paid for with essence taken straight out of the jars. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void synthesisesBindingSaltFromJars(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 1));
        JarBlockEntity ordo = jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        JarBlockEntity aqua = jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);

        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.BINDING_SALT,
            "The Matrix yields binding salt");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getCount() == 3, "Three salts are made at once");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "The string is spent");
        c.assertTrue(ordo.contents().total() == 6, "Four ordo left the jar");
        c.assertTrue(aqua.contents().total() == 8, "Two aqua left the jar");
        c.complete();
    }

    /** Both the base and the extra part are consumed by a synthesis. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spendsBaseAndComponents(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE, 2));
        core.setStack(MatrixCoreBlockEntity.COMPONENTS[0], new ItemStack(Items.GOLD_INGOT, 2));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 12);
        jar(c, CX - 3, CY, CZ, EssenceType.LUX, 12);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);

        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.ALCHEMISTS_MONOCLE,
            "A pane and gold make the monocle");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 1, "One pane is spent");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.COMPONENTS[0]).getCount() == 1, "One ingot is spent");
        c.complete();
    }

    /** Without the extra part the base just waits in the slot. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waitsForTheMissingComponent(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 12);
        jar(c, CX - 3, CY, CZ, EssenceType.LUX, 12);
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_RECIPE, "The Matrix asks for the missing part");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "Nothing is made without the gold");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 1, "The pane stays");
        c.complete();
    }

    /** Essence trickles in while the synthesis runs, so the sparks are visible the whole time. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void essenceCrossesTheAirFromJars(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        JarBlockEntity ordo = jar(c, CX + 4, CY + 1, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 60);
        c.assertTrue(ordo.contents().total() < 10, "The jar is already giving essence away mid-synthesis");
        c.assertTrue(core.stored(EssenceType.ORDO) > 0, "The core keeps what flew in");
        c.assertTrue(c.getWorld().getBlockState(pos).get(MatrixCoreBlock.ACTIVE), "A working Matrix glows");
        c.complete();
    }

    /** No essence around means no synthesis and no losses. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void withoutEssenceNothingHappens(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 2));
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION * 2);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No essence, no product");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 2, "The base is untouched");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_ESSENCE, "The core reports the missing essence");
        c.complete();
    }

    /** A full output slot stops the cube instead of destroying the result. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullOutputBlocksTheCube(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        core.setStack(MatrixCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE, 64));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getCount() == 64, "The full slot is left alone");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_BLOCKED, "The core reports the blocked output");
        c.complete();
    }

    /** Breaking the shell stops the machine at once and does not eat the base item. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brokenShellStopsTheSynthesis(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, 40);
        c.assertTrue(core.progress() > 0, "The synthesis started");
        c.setBlockState(CX, CY + 1, CZ, Blocks.AIR);
        run(c.getWorld(), pos, 2);
        c.assertTrue(core.progress() == 0, "Breaking a brick resets the progress");
        c.assertFalse(c.getWorld().getBlockState(pos).get(MatrixCoreBlock.ACTIVE), "The cube stops glowing");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 1, "The base survives");
        c.complete();
    }
}
