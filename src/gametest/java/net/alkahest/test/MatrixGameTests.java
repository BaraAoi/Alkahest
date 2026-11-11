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

    private static void wake(TestContext c) {
        MatrixCoreBlockEntity core = core(c);
        core.setAwake(c.getWorld(), c.getAbsolutePos(new BlockPos(CX, CY, CZ)), true);
    }

    private static JarBlockEntity jar(TestContext c, int x, int y, int z, EssenceType type, int amount) {
        c.setBlockState(x, y, z, ModBlocks.JAR);
        JarBlockEntity jar = (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        jar.setContents(of(type, amount));
        return jar;
    }

    private static void pedestal(TestContext c, int x, int y, int z, ItemStack stack) {
        c.setBlockState(x, y, z, ModBlocks.MATRIX_PEDESTAL);
        var pedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity)
            c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        pedestal.setHeld(stack.copy());
    }

    private static void start(TestContext c) {
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        c.assertTrue(core(c).startWith(new ItemStack(ModItems.ALKAHEST_AMULET), player), "The focus starts one synthesis");
    }

    private static void run(World world, BlockPos pos, int ticks) {
        MatrixCoreBlockEntity core = (MatrixCoreBlockEntity) world.getBlockEntity(pos);
        for (int i = 0; i < ticks; i++) MatrixCoreBlockEntity.tick(world, pos, core);
    }

    /** Reproduce client update packets on already-populated replicas, including omitted empty slots. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyUpdatesClearClientReplicas(TestContext c) {
        BlockPos pos = build(c);
        var registry = c.getWorld().getRegistryManager();
        var source = core(c);
        var replica = new MatrixCoreBlockEntity(pos, source.getCachedState());
        source.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.COAL));
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.COAL), "Replica receives the base");
        source.setStack(MatrixCoreBlockEntity.BASE, ItemStack.EMPTY);
        source.setStack(MatrixCoreBlockEntity.OUTPUT, new ItemStack(Items.DIAMOND));
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "Consumed base is removed on client");
        c.assertTrue(replica.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(Items.DIAMOND), "Replica receives product");
        source.setStack(MatrixCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.isEmpty(), "Taken product no longer floats on client");

        pedestal(c, CX + 3, CY, CZ, new ItemStack(Items.GOLD_INGOT));
        BlockPos pedestalPos = c.getAbsolutePos(new BlockPos(CX + 3, CY, CZ));
        var pedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(pedestalPos);
        var replicaPedestal = new net.alkahest.block.entity.MatrixPedestalBlockEntity(pedestalPos, pedestal.getCachedState());
        replicaPedestal.readComponentlessNbt(pedestal.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replicaPedestal.held().isOf(Items.GOLD_INGOT), "Replica receives the pedestal item");
        pedestal.takeOne();
        replicaPedestal.readComponentlessNbt(pedestal.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replicaPedestal.isEmpty(), "Taken pedestal item no longer floats on client");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void eachSynthesisRequiresFocusAndPersists(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 2));
        JarBlockEntity ordo = jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, 40);
        c.assertTrue(core.progress() == 0 && ordo.contents().total() == 10, "Awakening alone starts nothing");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NEEDS_START, "HUD asks for a focus");
        var amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, amulet);
        c.assertTrue(core.started() && core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.STRING), "Amulet starts without replacing base");
        var saved = core.createNbt(c.getWorld().getRegistryManager());
        var replica = new MatrixCoreBlockEntity(pos, core.getCachedState());
        replica.readComponentlessNbt(saved, c.getWorld().getRegistryManager());
        c.assertTrue(replica.started(), "Running state survives reload and sync");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.BINDING_SALT), "One batch completes");
        c.assertFalse(core.started(), "The focus must start every batch");
        int remaining = ordo.contents().total();
        run(c.getWorld(), pos, 40);
        c.assertTrue(ordo.contents().total() == remaining, "A second base cannot auto-start");
        core.setStack(MatrixCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        var stone = new ItemStack(ModItems.PHILOSOPHERS_STONE);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, stone);
        c.assertTrue(core.started() && stone.getCount() == 1, "Stone starts without being inserted or consumed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void focusCannotStartInvalidOrUnchargedRecipe(TestContext c) {
        build(c);
        wake(c);
        var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        c.assertFalse(core.startWith(amulet, player), "Missing components reject start");
        c.assertTrue(amulet.getOrDefault(net.alkahest.registry.ModDataComponents.AMULET_USES_STEP, 0) == 0,
            "A rejected start costs no charge");
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        amulet.set(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, 0);
        c.assertFalse(core.startWith(amulet, player), "Empty amulet cannot start a synthesis");
        c.assertFalse(core.started(), "The Matrix remains idle");
        c.complete();
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

    /** A newly built cube does nothing at all until a wand wakes it up. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void matrixStaysDormantUntilAwoken(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        c.assertFalse(core.awake(), "A fresh Matrix is dormant");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_ASLEEP, "The core reports that it is asleep");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 20);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "A dormant cube never works");
        c.assertFalse(c.getWorld().getBlockState(pos).get(net.alkahest.block.custom.MatrixCoreBlock.ACTIVE),
            "A dormant cube does not glow");
        wake(c);
        c.assertTrue(c.getWorld().getBlockState(pos).get(net.alkahest.block.custom.MatrixCoreBlock.AWAKE),
            "The wand switches the cube on");
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.BINDING_SALT,
            "An awakened cube works");
        c.complete();
    }

    /** Awakening hides the rough shell but keeps the blocks where they are. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void awakeningHidesTheShell(TestContext c) {
        BlockPos pos = build(c);
        BlockPos corner = c.getAbsolutePos(new BlockPos(CX + 1, CY + 1, CZ + 1));
        BlockPos brick = c.getAbsolutePos(new BlockPos(CX + 1, CY, CZ));
        c.assertFalse(c.getWorld().getBlockState(corner)
            .get(net.alkahest.block.custom.MatrixPillarBlock.REVEALED), "A dormant cube shows its pyramids");
        wake(c);
        c.assertTrue(c.getWorld().getBlockState(corner)
            .get(net.alkahest.block.custom.MatrixPillarBlock.REVEALED), "Awakening hides the corner pyramids");
        c.assertTrue(c.getWorld().getBlockState(brick)
            .get(net.alkahest.block.custom.AlchemicalBrickBlock.REVEALED), "Awakening hides the bricks");
        c.assertTrue(c.getWorld().getBlockState(brick).getBlock() == ModBlocks.ALCHEMICAL_BRICK,
            "Hidden blocks are still there and can be broken");
        c.setBlockState(CX + 1, CY, CZ, Blocks.AIR);
        MatrixCoreBlockEntity.tick(c.getWorld(), pos, core(c));
        c.assertFalse(core(c).awake(), "Breaking the shell puts the Matrix back to its initial state");
        c.assertFalse(c.getWorld().getBlockState(corner)
            .get(net.alkahest.block.custom.MatrixPillarBlock.REVEALED), "The remaining pyramids come back");
        c.complete();
    }

    /** Waking an already awakened cube changes nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void awakeningHappensOnlyOnce(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        wake(c);
        c.assertTrue(core(c).awake(), "The Matrix stays awake");
        c.assertTrue(core(c).status() != MatrixCoreBlockEntity.STATUS_UNFORMED, "The cube is still whole");
        MatrixCoreBlockEntity.tick(c.getWorld(), pos, core(c));
        c.assertTrue(core(c).awake(), "A second activation does not break anything");
        c.complete();
    }

    /** Parts for the synthesis lie on the pedestals around the cube, not in the interface. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pedestalPartsArePulledIntoTheSynthesis(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        pedestal(c, CX + 3, CY, CZ, new ItemStack(Items.GOLD_INGOT));
        jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 12);
        jar(c, CX - 3, CY, CZ + 1, EssenceType.LUX, 12);
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.ALCHEMISTS_MONOCLE,
            "The pane and the golden ingot from the pedestal make a monocle");
        var used = (net.alkahest.block.entity.MatrixPedestalBlockEntity)
            c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(CX + 3, CY, CZ)));
        c.assertTrue(used.isEmpty(), "The pedestal gives its item away");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "The base is spent as well");
        c.complete();
    }

    /** Use the real block interactions and server ticker, with a diagonal pedestal below the core. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240)
    public void diamondUsesTwoClickedPedestalsOnRealTicks(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        ItemStack coal = new ItemStack(Items.COAL);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, coal);
        c.assertTrue(coal.isEmpty(), "A real core click inserts the base");
        c.setBlockState(7, CY - 1, 7, ModBlocks.MATRIX_PEDESTAL);
        c.setBlockState(0, CY - 1, CZ, ModBlocks.MATRIX_PEDESTAL);
        BlockPos goldPos = c.getAbsolutePos(new BlockPos(7, CY - 1, 7));
        BlockPos saltPos = c.getAbsolutePos(new BlockPos(0, CY - 1, CZ));
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack salt = new ItemStack(ModItems.SALT);
        for (var entry : java.util.Map.of(goldPos, gold, saltPos, salt).entrySet()) {
            BlockPos target = entry.getKey();
            var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(target),
                net.minecraft.util.math.Direction.UP, target, false);
            c.getWorld().getBlockState(target).onUseWithItem(entry.getValue(), c.getWorld(), player,
                net.minecraft.util.Hand.MAIN_HAND, hit);
        }
        c.assertTrue(gold.isEmpty() && salt.isEmpty(), "Actual clicks place both ingredients");
        c.assertTrue(MatrixCoreBlockEntity.forPedestal(c.getWorld(), goldPos) == core,
            "Diagonal pedestal one level below is linked to the Matrix");
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(Items.DIAMOND),
            "Both components are recognised before work starts");
        jar(c, 0, CY, 0, EssenceType.METALLUM, 8);
        jar(c, 6, CY, 0, EssenceType.LUX, 6);
        jar(c, 0, CY, 6, EssenceType.ORDO, 4);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, new ItemStack(ModItems.ALKAHEST_AMULET));
        c.assertTrue(core.started(), "Real amulet interaction starts the synthesis");
        c.runAtTick(180, () -> {
            c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(Items.DIAMOND),
                "The natural server ticker completes the diamond synthesis");
            var goldPedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(goldPos);
            var saltPedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(saltPos);
            c.assertTrue(goldPedestal.isEmpty() && saltPedestal.isEmpty(), "Both real pedestal items were absorbed");
            c.complete();
        });
    }

    /** Missing essence stalls safely; once the missing jar arrives, the pedestal is consumed. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lightningWaitsForEssenceBeforeTakingPedestalItem(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.LIGHTNING_ROD));
        pedestal(c, 7, CY - 1, 7, new ItemStack(Items.GLASS_BOTTLE));
        BlockPos pedestalPos = c.getAbsolutePos(new BlockPos(7, CY - 1, 7));
        var part = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(pedestalPos);
        jar(c, 0, CY, 0, EssenceType.IGNIS, 6);
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_ESSENCE, "Missing Aer stops synthesis");
        c.assertTrue(part.held().isOf(Items.GLASS_BOTTLE), "The pedestal item is not lost while waiting");
        jar(c, 6, CY, 0, EssenceType.AER, 6);
        run(c.getWorld(), pos, 3);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.LIGHTNING_IN_A_BOTTLE),
            "Adding Aer completes the recipe");
        c.assertTrue(part.isEmpty(), "The bottle is absorbed exactly on successful completion");
        c.complete();
    }

    /** The specification's miasma recipe uses the physical pedestal and waits for all essence. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void miasmaRequiresSlimeFocusAndFullEssence(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_BOTTLE));
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        var amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        c.assertFalse(core.startWith(amulet, player), "The bottle alone must not start miasma");
        pedestal(c, 7, CY - 1, 7, new ItemStack(ModItems.CAUSTIC_SLIME));
        BlockPos pedestalPos = c.getAbsolutePos(new BlockPos(7, CY - 1, 7));
        var part = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(pedestalPos);
        var perditio = jar(c, 0, CY, 0, EssenceType.PERDITIO, 23);
        var mortuus = jar(c, 6, CY, 0, EssenceType.MORTUUS, 17);
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.MIASMA), "Bottle plus caustic slime select miasma");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NEEDS_START, "A focus is required");
        run(c.getWorld(), pos, 20);
        c.assertTrue(perditio.contents().total() == 23, "No essence is pulled before activation");
        c.assertTrue(core.startWith(amulet, player), "The amulet starts miasma");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 2);
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_ESSENCE, "The Matrix waits for missing Aqua");
        c.assertTrue(part.held().isOf(ModItems.CAUSTIC_SLIME) && core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_BOTTLE),
            "Bottle and slime are safe while the price is incomplete");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No free result without Aqua");
        var aqua = jar(c, 0, CY, 6, EssenceType.AQUA, 12);
        run(c.getWorld(), pos, 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.MIASMA), "Completing the price produces miasma");
        c.assertTrue(part.isEmpty() && core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "One slime and one bottle are consumed");
        c.assertTrue(perditio.contents().total() == 3 && mortuus.contents().total() == 2 && aqua.contents().total() == 2,
            "The exact price is Perditio 20, Mortuus 15, Aqua 10");
        c.assertFalse(core.started(), "The completed synthesis requires a new activation");
        c.complete();
    }

    /** The specification's lightning recipe shares a bottle base with miasma. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bottledLightningFromTwoCrystalPedestals(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_BOTTLE));
        pedestal(c, 7, CY - 1, 7, new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS)));
        pedestal(c, 0, CY - 1, CZ, new ItemStack(ModItems.CRYSTALS.get(EssenceType.AER)));
        pedestal(c, 6, CY - 1, CZ, new ItemStack(Items.GOLD_INGOT));
        var ignis = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, CY - 1, 7)));
        var aerCrystal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(0, CY - 1, CZ)));
        var extra = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(6, CY - 1, CZ)));
        var potentia = jar(c, 0, CY, 0, EssenceType.POTENTIA, 20);
        var aer = jar(c, 6, CY, 0, EssenceType.AER, 12);
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.LIGHTNING_IN_A_BOTTLE), "Both crystals select lightning, not miasma");
        c.assertTrue(core.recipe().cost().get(EssenceType.POTENTIA) == 15 && core.recipe().cost().get(EssenceType.AER) == 10,
            "The monocle's selected recipe has the specification price");
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.LIGHTNING_IN_A_BOTTLE), "The bottle turns into lightning");
        c.assertTrue(ignis.isEmpty() && aerCrystal.isEmpty(), "Both crystals are absorbed");
        c.assertTrue(extra.held().isOf(Items.GOLD_INGOT), "An unrelated pedestal item survives");
        c.assertTrue(potentia.contents().total() == 5 && aer.contents().total() == 2, "The exact price is paid");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void conflictingBottleRecipesRefuseToGuess(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_BOTTLE));
        pedestal(c, 7, CY - 1, 7, new ItemStack(ModItems.CAUSTIC_SLIME));
        pedestal(c, 0, CY - 1, CZ, new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS)));
        pedestal(c, 6, CY - 1, CZ, new ItemStack(ModItems.CRYSTALS.get(EssenceType.AER)));
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        c.assertTrue(core.recipe() == null && core.status() == MatrixCoreBlockEntity.STATUS_AMBIGUOUS,
            "Both complete recipes are ambiguous instead of silently choosing one");
        c.assertFalse(core.startWith(amulet, player), "A focus cannot start an ambiguous recipe");
        c.assertTrue(amulet.getOrDefault(net.alkahest.registry.ModDataComponents.AMULET_USES_STEP, 0) == 0,
            "An ambiguous attempt costs no amulet use");
        run(c.getWorld(), pos, 20);
        c.assertTrue(core.progress() == 0 && core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No synthesis runs");
        var slime = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, CY - 1, 7)));
        slime.takeOne();
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.LIGHTNING_IN_A_BOTTLE), "Removing slime selects lightning");
        c.assertTrue(core.startWith(amulet, player), "The selected recipe can now start");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changedRecipeCancelsWithoutConsumingItems(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_BOTTLE));
        pedestal(c, 7, CY - 1, 7, new ItemStack(ModItems.CAUSTIC_SLIME));
        var slime = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, CY - 1, 7)));
        jar(c, 0, CY, 0, EssenceType.PERDITIO, 20);
        start(c);
        run(c.getWorld(), pos, 50);
        int buffered = core.stored(EssenceType.PERDITIO);
        c.assertTrue(buffered > 0, "Some essence was reserved before the ingredients changed");
        var registry = c.getWorld().getRegistryManager();
        var oldSave = core.createNbt(registry);
        oldSave.remove("StartedRecipe");
        var legacy = new MatrixCoreBlockEntity(pos, core.getCachedState());
        legacy.readComponentlessNbt(oldSave, registry);
        c.assertFalse(legacy.started(), "An old in-flight save without an identity stops instead of guessing");
        c.assertTrue(legacy.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_BOTTLE) && legacy.stored(EssenceType.PERDITIO) == buffered,
            "Migration keeps the base and reserved essence");
        slime.takeOne();
        pedestal(c, 0, CY - 1, CZ, new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS)));
        pedestal(c, 6, CY - 1, CZ, new ItemStack(ModItems.CRYSTALS.get(EssenceType.AER)));
        run(c.getWorld(), pos, 1);
        c.assertFalse(core.started(), "Changing to a different recipe cancels instead of switching mid-synthesis");
        c.assertTrue(core.stored(EssenceType.PERDITIO) == buffered && core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_BOTTLE),
            "Cancellation retains the base and reserved essence");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "Changing ingredients creates no result");
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.LIGHTNING_IN_A_BOTTLE), "The new recipe is ready for an explicit start");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasElixirRecipeRequiresEveryIngredient(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GOLDEN_APPLE));
        pedestal(c, 7, CY - 1, 7, new ItemStack(ModItems.PURIFIED_WATER));
        BlockPos pedestalPos = c.getAbsolutePos(new BlockPos(7, CY - 1, 7));
        var water = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(pedestalPos);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        c.assertFalse(core.startWith(new ItemStack(ModItems.PHILOSOPHERS_STONE), player), "The old water-only recipe no longer starts");
        pedestal(c, 7, CY - 1, 3, new ItemStack(Items.GOLD_INGOT));
        c.assertFalse(core.startWith(new ItemStack(ModItems.PHILOSOPHERS_STONE), player), "A crystal is also required");
        pedestal(c, 3, CY - 1, 7, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        var gold = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, CY - 1, 3)));
        var crystal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, CY - 1, 7)));
        var ordo = jar(c, 0, CY, 0, EssenceType.ORDO, 20);
        var lux = jar(c, 6, CY, 0, EssenceType.LUX, 12);
        var metallum = jar(c, 0, CY, 6, EssenceType.METALLUM, 7);
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.MIDAS_ELIXIR), "Golden apple and all three components select the elixir");
        start(c);
        run(c.getWorld(), pos, 20);
        int buffered = core.stored(EssenceType.ORDO);
        crystal.takeOne();
        run(c.getWorld(), pos, 1);
        c.assertFalse(core.started(), "Removing one component cancels synthesis");
        c.assertTrue(core.stored(EssenceType.ORDO) == buffered && !water.isEmpty() && !gold.isEmpty()
            && core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GOLDEN_APPLE), "Cancellation preserves the base, remaining ingredients and reserved essence");
        crystal.setHeld(new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.MIDAS_ELIXIR), "The Matrix produces a Midas elixir");
        c.assertTrue(water.isEmpty() && gold.isEmpty() && crystal.isEmpty() && core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "All four ingredients are consumed");
        c.assertTrue(ordo.contents().total() == 5 && lux.contents().total() == 2 && metallum.contents().total() == 2,
            "The exact price is Ordo 15, Lux 10, Metallum 5");
        // GameTest structures share a world; do not leave filled jars near a neighbouring test.
        c.setBlockState(0, CY, 0, Blocks.AIR);
        c.setBlockState(6, CY, 0, Blocks.AIR);
        c.setBlockState(0, CY, 6, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void napalmMatrixRecipeNeedsTwoPartsAndPaysPrice(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(ModItems.FIRE_DUST));
        pedestal(c, 7, CY - 1, 3, new ItemStack(Items.SLIME_BALL));
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        c.assertFalse(core.startWith(new ItemStack(ModItems.PHILOSOPHERS_STONE), player), "Both parts are required");
        pedestal(c, 3, CY - 1, 7, new ItemStack(Items.BLAZE_POWDER));
        var slime = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, CY - 1, 3)));
        var blaze = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, CY - 1, 7)));
        var ignis = jar(c, 0, CY, 0, EssenceType.IGNIS, 15);
        var potentia = jar(c, 6, CY, 0, EssenceType.POTENTIA, 10);
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.NAPALM), "Both parts select napalm");
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        ItemStack output = core.getStack(MatrixCoreBlockEntity.OUTPUT);
        c.assertTrue(output.isOf(ModItems.NAPALM) && output.getCount() == 3, "Synthesis yields three napalm");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty() && slime.isEmpty() && blaze.isEmpty(), "All ingredients are consumed");
        c.assertTrue(ignis.contents().total() == 3 && potentia.contents().total() == 2, "The exact cost is Ignis 12 and Potentia 8");
        c.setBlockState(0, CY, 0, Blocks.AIR);
        c.setBlockState(6, CY, 0, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void acidBombBatchNeedsBothComponentsAndExactEssence(TestContext c) {
        // Keep jars well above other simultaneous structures in the shared GameTest world.
        int y = CY + 48;
        c.setBlockState(CX, y, CZ, ModBlocks.MATRIX_CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            c.setBlockState(CX + dx, y + dy, CZ + dz,
                dx != 0 && dy != 0 && dz != 0 ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
        }
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, y, CZ));
        var core = (MatrixCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        core.setAwake(c.getWorld(), pos, true);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(ModItems.CAUSTIC_SLIME));
        pedestal(c, 7, y - 1, 3, new ItemStack(Items.GUNPOWDER));
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        var focus = new ItemStack(ModItems.ALKAHEST_AMULET);
        c.assertFalse(core.startWith(focus, player), "Gunpowder alone is not enough for the new synthesis");
        pedestal(c, 3, y - 1, 7, new ItemStack(Items.IRON_NUGGET));
        var powder = (net.alkahest.block.entity.MatrixPedestalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, y - 1, 3)));
        var iron = (net.alkahest.block.entity.MatrixPedestalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, y - 1, 7)));
        var vitium = jar(c, 0, y, 0, EssenceType.VITIUM, 15);
        var perditio = jar(c, 6, y, 0, EssenceType.PERDITIO, 10);
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(ModItems.ACID_BOMB), "Both pedestals select the acid batch");
        c.assertTrue(core.startWith(focus, player), "A focus starts the new recipe");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 3);
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_ESSENCE && core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(),
            "Missing Metallum prevents completion");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isOf(ModItems.CAUSTIC_SLIME) && !powder.isEmpty() && !iron.isEmpty(),
            "Input and both components remain intact while waiting");
        var metallum = jar(c, 0, y, 6, EssenceType.METALLUM, 6);
        run(c.getWorld(), pos, 4);
        var output = core.getStack(MatrixCoreBlockEntity.OUTPUT);
        c.assertTrue(output.isOf(ModItems.ACID_BOMB) && output.getCount() == 3, "The synthesis creates exactly three acid bombs");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty() && powder.isEmpty() && iron.isEmpty(),
            "One of each ingredient is consumed at completion");
        c.assertTrue(vitium.contents().total() == 3 && perditio.contents().total() == 2 && metallum.contents().total() == 2,
            "The exact price is Vitium 12, Perditio 8 and Metallum 4");
        c.setBlockState(0, y, 0, Blocks.AIR);
        c.setBlockState(6, y, 0, Blocks.AIR);
        c.setBlockState(0, y, 6, Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasElixirDrinkingAndNearbyRange(TestContext c) {
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var item = (net.alkahest.item.custom.MidasElixirItem) ModItems.MIDAS_ELIXIR;
        ItemStack elixir = new ItemStack(ModItems.MIDAS_ELIXIR);
        c.assertTrue(item.getMaxUseTime(elixir, player) == 32 && item.getUseAction(elixir) == net.minecraft.util.UseAction.DRINK,
            "The elixir uses the normal 32 tick drinking animation");
        ItemStack returned = item.finishUsing(elixir, c.getWorld(), player);
        c.assertTrue(returned.isOf(Items.GLASS_BOTTLE) && elixir.isEmpty(), "Survival drinking returns one empty bottle");
        var effect = player.getStatusEffect(net.alkahest.registry.ModEffects.MIDAS_TOUCH);
        c.assertTrue(effect != null && effect.getDuration() == 3600, "Midas Touch lasts three minutes");
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        player.setPosition(pos.getX() + 8.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        c.assertTrue(net.alkahest.effect.MidasTouchEffect.nearby(List.of(player), pos), "The eight-block boundary counts");
        player.setPosition(pos.getX() + 8.51, pos.getY() + 0.5, pos.getZ() + 0.5);
        c.assertFalse(net.alkahest.effect.MidasTouchEffect.nearby(List.of(player), pos), "Outside the radius gives no bonus");
        player.removeStatusEffect(net.alkahest.registry.ModEffects.MIDAS_TOUCH);
        player.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        c.assertFalse(net.alkahest.effect.MidasTouchEffect.nearby(List.of(player), pos), "Players without the effect do not boost");
        var spectator = c.createMockPlayer(net.minecraft.world.GameMode.SPECTATOR);
        spectator.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        spectator.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.alkahest.registry.ModEffects.MIDAS_TOUCH, 3600));
        c.assertFalse(net.alkahest.effect.MidasTouchEffect.nearby(List.of(spectator), pos), "Spectators do not boost");
        var creative = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        ItemStack creativeElixir = new ItemStack(ModItems.MIDAS_ELIXIR);
        c.assertTrue(item.finishUsing(creativeElixir, c.getWorld(), creative) == creativeElixir && creativeElixir.getCount() == 1,
            "Creative drinking does not consume the elixir");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasProgressIsTwentyPercentFaster(TestContext c) {
        int normal = 0, boosted = 0, normalTicks = 0, boostedTicks = 0, prefillCrossings = 0;
        while (normal < MatrixCoreBlockEntity.DURATION) {
            normal += net.alkahest.effect.MidasTouchEffect.progressStep(false, normalTicks++);
        }
        while (boosted < MatrixCoreBlockEntity.DURATION) {
            int previous = boosted;
            boosted = Math.min(MatrixCoreBlockEntity.DURATION,
                boosted + net.alkahest.effect.MidasTouchEffect.progressStep(true, boostedTicks++));
            if (previous / 10 != boosted / 10) prefillCrossings++;
        }
        c.assertTrue(normalTicks == 160 && boostedTicks == 133, "Midas gives one extra progress step per five ticks");
        c.assertTrue(prefillCrossings == 16, "Accelerated steps never skip a ten-progress essence request");
        c.complete();
    }

    /** String becomes three binding salts, paid for with essence taken straight out of the jars. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void synthesisesBindingSaltFromJars(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 1));
        JarBlockEntity ordo = jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        JarBlockEntity aqua = jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);

        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.BINDING_SALT,
            "The Matrix yields binding salt");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getCount() == 3, "Three salts are made at once");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "The string is spent");
        c.assertTrue(ordo.contents().total() == 6, "Four ordo left the jar");
        c.assertTrue(aqua.contents().total() == 8, "Two aqua left the jar");
        c.complete();
    }

    /** Without the extra part on any pedestal the base just waits inside the core. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waitsForTheMissingPart(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
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
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        JarBlockEntity ordo = jar(c, CX + 4, CY + 1, CZ, EssenceType.ORDO, 10);
        start(c);
        run(c.getWorld(), pos, 60);
        c.assertTrue(ordo.contents().total() < 10, "The jar is already giving essence away mid-synthesis");
        c.assertTrue(core.stored(EssenceType.ORDO) > 0, "The core keeps what flew in");
        c.assertTrue(c.getWorld().getBlockState(pos).get(MatrixCoreBlock.ACTIVE), "A working Matrix glows");
        c.complete();
    }

    /** No essence around means no synthesis and no losses. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void withoutEssenceNothingHappens(TestContext c) {
        // The shared GameTest world may contain jars in adjacent structures, within the 12-block
        // pickup radius. Build this negative test well above the other structures instead.
        int y = CY + 32;
        c.setBlockState(CX, y, CZ, ModBlocks.MATRIX_CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            c.setBlockState(CX + dx, y + dy, CZ + dz,
                dx != 0 && dy != 0 && dz != 0 ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
        }
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, y, CZ));
        var core = (MatrixCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        core.setAwake(c.getWorld(), pos, true);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 2));
        c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),
            c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE)), "Focus starts synthesis");
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
        wake(c);
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
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        start(c);
        run(c.getWorld(), pos, 40);
        c.assertTrue(core.progress() > 0, "The synthesis started");
        c.setBlockState(CX, CY + 1, CZ, Blocks.AIR);
        run(c.getWorld(), pos, 2);
        c.assertTrue(core.progress() == 0, "Breaking a brick resets the progress");
        c.assertFalse(c.getWorld().getBlockState(pos).get(MatrixCoreBlock.ACTIVE), "The cube stops glowing");
        c.assertFalse(core.awake(), "The Matrix falls back to its dormant form");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 1, "The base survives");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newSynthesisRecipesHaveDistinctInputsAndTwoPedestalItems(TestContext c) {
        var recipes = net.alkahest.matrix.MatrixRecipe.all();
        for (var output : List.of(ModItems.FROST_FLASK, ModItems.PURIFYING_FLASK, Items.ARROW, Items.POWERED_RAIL,
                Items.SEA_LANTERN, Items.MAGMA_BLOCK, Items.SOUL_LANTERN, Items.RAIL)) {
            var matching = recipes.stream().filter(r -> r.output().isOf(output)).toList();
            c.assertTrue(matching.size() == 1, "Exactly one new recipe exists for " + output);
            var recipe = matching.getFirst();
            c.assertTrue(recipe.components().size() >= 2, "Every new Matrix recipe uses multiple pedestal ingredients");
            var given = recipe.components().stream().map(ItemStack::new).toList();
            c.assertTrue(net.alkahest.matrix.MatrixRecipe.find(new ItemStack(recipe.base()), given) == recipe,
                "The complete recipe has an unambiguous match");
            c.assertTrue(net.alkahest.matrix.MatrixRecipe.find(new ItemStack(recipe.base()), given.subList(0, 1)) == null,
                "A missing ingredient cannot start this recipe");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void frostSynthesisWaitsForFullPriceAndConsumesExactlyOnce(TestContext c) {
        int y = CY + 160;
        c.setBlockState(CX, y, CZ, ModBlocks.MATRIX_CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            c.setBlockState(CX + dx, y + dy, CZ + dz,
                dx != 0 && dy != 0 && dz != 0 ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
        }
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, y, CZ));
        var core = (MatrixCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        core.setAwake(c.getWorld(), pos, true);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.SNOWBALL));
        pedestal(c, 7, y - 1, 3, new ItemStack(Items.GLASS_BOTTLE));
        pedestal(c, 3, y - 1, 7, new ItemStack(ModItems.CRYSTALS.get(EssenceType.GELUM)));
        var bottle = (net.alkahest.block.entity.MatrixPedestalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, y - 1, 3)));
        var crystal = (net.alkahest.block.entity.MatrixPedestalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, y - 1, 7)));
        var gelum = jar(c, 0, y, 0, EssenceType.GELUM, 15);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET), player), "A focus starts the frost recipe");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 3);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty() && !bottle.isEmpty() && !crystal.isEmpty(), "Missing Aqua preserves the ingredients");
        var aqua = jar(c, 6, y, 0, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.FROST_FLASK) && core.getStack(MatrixCoreBlockEntity.OUTPUT).getCount() == 2, "Produces two frost flasks");
        c.assertTrue(bottle.isEmpty() && crystal.isEmpty() && core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "Every required item is consumed once");
        c.assertTrue(gelum.contents().total() == 3 && aqua.contents().total() == 2, "Exactly Gelum 12 and Aqua 8 are paid");
        c.setBlockState(0, y, 0, Blocks.AIR); c.setBlockState(6, y, 0, Blocks.AIR);
        c.complete();
    }
}
