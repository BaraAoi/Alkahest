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

    /** Isolate conservation tests from the neighbouring structures' jars in the shared world. */
    private static BlockPos isolatedPlatform(TestContext c, int y) {
        c.setBlockState(CX, y, CZ, ModBlocks.TRANSMUTATOR_CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) c.setBlockState(CX + dx, y, CZ + dz, ModBlocks.TRANSMUTATOR_PILLAR);
        }
        return c.getAbsolutePos(new BlockPos(CX, y, CZ));
    }

    private static int stored(TransmutatorCoreBlockEntity core, EssenceType type) {
        return core.pillars().stream().mapToInt(p -> p.stored(type)).sum();
    }

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

    private static BlockPos tower(TestContext c, int y) {
        c.setBlockState(CX, y, CZ, ModBlocks.TRANSMUTATOR_CORE);
        c.setBlockState(CX, y + 1, CZ, ModBlocks.TRANSMUTATOR_CHAMBER);
        c.setBlockState(CX, y + 2, CZ, ModBlocks.TRANSMUTATOR_CROWN);
        return c.getAbsolutePos(new BlockPos(CX, y, CZ));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void towerNeedsAwakeningAndSurvivesReload(TestContext c) {
        int y = CY + 32;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        run(c.getWorld(), pos, 200);
        c.assertTrue(core.verticalFormed() && !core.awake() && core.progress() == 0, "A new tower is dormant");
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_DORMANT, "The tower asks for a wand");
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        ItemStack wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        BlockPos crown = pos.up(2);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(crown), net.minecraft.util.math.Direction.UP, crown, false);
        c.getWorld().getBlockState(crown).onUseWithItem(wand, c.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hit);
        c.assertTrue(core.awake() && !core.wake(), "A wand click on the crown awakens the tower once");
        c.assertTrue(c.getWorld().getBlockState(pos.up()).get(net.alkahest.block.custom.TransmutatorAssemblyBlock.AWAKE), "The chamber lights after awakening");
        var replica = new TransmutatorCoreBlockEntity(pos, core.getCachedState());
        replica.readComponentlessNbt(core.createNbt(c.getWorld().getRegistryManager()), c.getWorld().getRegistryManager());
        c.assertTrue(replica.awake() && replica.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "Awake state and inventory survive NBT reload");
        c.setBlockState(CX, y + 2, CZ, net.minecraft.block.Blocks.AIR);
        run(c.getWorld(), pos, 1);
        c.assertFalse(core.awake(), "Breaking the crown deactivates the tower");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "Breaking a component preserves the core inventory");
        c.setBlockState(CX, y + 2, CZ, ModBlocks.TRANSMUTATOR_CROWN);
        c.assertTrue(core.verticalFormed() && !core.awake(), "Reassembly needs another awakening");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void connectedTowerWaitsForWholePriceAndSpendsExactlyOnce(TestContext c) {
        int y = CY + 48;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 60);
        var remoteOrdo = jar(c, CX - 3, y, CZ, EssenceType.ORDO, 20);
        run(c.getWorld(), pos, 150);
        for (int i = 0; i < 200; i++) net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 60 && remoteOrdo.contents().total() == 20, "An absent connected type never drains the other type or a nearby disconnected jar");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty() && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 4,
            "Waiting never spends an item or amulet charge");
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Connecting the missing jar resumes the operation");
        c.assertTrue(metal.contents().total() == 57 && ordo.contents().total() == 8 && remoteOrdo.contents().total() == 20,
            "A successful pipe operation spends exactly 3 Metallum and 2 Ordo");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 1 && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3,
            "Only one item and one charge are consumed");
        for (int i = 0; i < 20; i++) net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 57 && ordo.contents().total() == 8, "An idle progress cycle does not drain or transfer jars");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sharedNetworkTransfersBellowsToJarsAndFeedsTower(TestContext c) {
        int y = CY + 64;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 1);
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        c.setBlockState(CX + 2, y, CZ, ModBlocks.DISTILLERY_BELLOWS);
        var bellow = (net.alkahest.block.entity.DistilleryBellowsBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(CX + 2, y, CZ)));
        bellow.accept(EssenceType.METALLUM, 10, .7f);
        run(c.getWorld(), pos, 110);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(bellow.amount() == 6 && metal.contents().total() == 2 && ordo.contents().total() == 8,
            "Bellows feed their jar while the tower pays the exact cost in the same network");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Shared distillery and storage network feeds the tower");
        c.assertTrue(metal.contents().contents().size() == 1 && ordo.contents().contents().size() == 1, "Jars remain single-type");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void towerDoesNotDrainWhileBlockedOrDisconnected(TestContext c) {
        int y = CY + 80;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.wake();
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 10);
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE, 64));
        run(c.getWorld(), pos, 200);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 10 && ordo.contents().total() == 10, "A full output never draws essence");
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        run(c.getWorld(), pos, 110);
        c.setBlockState(CX + 1, y, CZ, net.minecraft.block.Blocks.AIR);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 10 && ordo.contents().total() == 10 && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(),
            "A disconnected pipe cannot pay the machine");
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Reconnecting resumes one operation");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyPillarReturnsReservedEssenceWhenBroken(TestContext c) {
        int y = CY + 96;
        BlockPos pos = isolatedPlatform(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        var metal = jar(c, CX + 3, y, CZ, EssenceType.METALLUM, 10);
        jar(c, CX - 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 10);
        var pillar = core.pillars().stream().filter(p -> p.stored(EssenceType.METALLUM) > 0).findFirst().orElseThrow();
        BlockPos pillarPos = pillar.getPos();
        int reserved = pillar.stored(EssenceType.METALLUM);
        c.getWorld().setBlockState(pillarPos, net.minecraft.block.Blocks.AIR.getDefaultState());
        var items = c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
            new net.minecraft.util.math.Box(pillarPos).expand(2), e -> e.getStack().isOf(ModBlocks.JAR.asItem()));
        int recovered = 0;
        for (var item : items) for (EssenceStack entry : net.alkahest.item.custom.JarItem.contents(item.getStack()).contents())
            if (entry.type() == EssenceType.METALLUM) recovered += entry.amount();
        c.assertTrue(reserved == recovered && metal.contents().total() + recovered == 10, "Upgrading a legacy pillar returns every buffered unit in jars");
        c.assertTrue(pillar.buffer().total() == 0, "Recovered buffer is cleared and cannot duplicate");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chamberAndCrownHaveWorkingMultiIngredientRecipes(TestContext c) {
        for (int which = 0; which < 2; which++) {
            ItemStack other = which == 0 ? new ItemStack(Items.GLASS) : new ItemStack(Items.AMETHYST_SHARD);
            ItemStack crystal = new ItemStack(ModItems.CRYSTALS.get(which == 0 ? EssenceType.AQUA : EssenceType.ORDO));
            var input = net.minecraft.recipe.input.CraftingRecipeInput.create(2, 2, List.of(
                new ItemStack(ModBlocks.TRANSMUTATOR_PILLAR), other, new ItemStack(Items.GOLD_INGOT), crystal));
            var found = c.getWorld().getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, c.getWorld());
            c.assertTrue(found.isPresent(), "The tower component crafting recipe is registered");
            ItemStack result = found.get().value().craft(input, c.getWorld().getRegistryManager());
            c.assertTrue(result.isOf((which == 0 ? ModBlocks.TRANSMUTATOR_CHAMBER : ModBlocks.TRANSMUTATOR_CROWN).asItem()),
                "The component recipe makes the correct block");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipeCanBePlacedOnTowerCore(TestContext c) {
        int y = CY + 112;
        BlockPos pos = tower(c, y);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setPosition(pos.getX() + 3, pos.getY(), pos.getZ());
        ItemStack pipes = new ItemStack(ModBlocks.ALCHEMY_PIPE, 4);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, pipes);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos).add(.5, 0, 0),
            net.minecraft.util.math.Direction.EAST, pos, false);
        c.getWorld().getBlockState(pos).onUseWithItem(pipes, c.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hit);
        c.assertTrue(c.getWorld().getBlockState(pos.east()).isOf(ModBlocks.ALCHEMY_PIPE), "A normal pipe click places the segment next to the core");
        c.assertTrue(c.getWorld().getBlockState(pos.east()).get(net.alkahest.block.custom.AlchemyPipeBlock.WEST), "The pipe visually connects to the core");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void visibleAmuletUpdatesAndClearsOnClientReplicas(TestContext c) {
        BlockPos pos = tower(c, CY + 32);
        var source = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        var replica = new TransmutatorCoreBlockEntity(pos, source.getCachedState());
        var registry = c.getWorld().getRegistryManager();
        ItemStack amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        amulet.set(ModDataComponents.ESSENCE_CHARGE, 17);
        source.setStack(TransmutatorCoreBlockEntity.AMULET, amulet);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).isOf(ModItems.ALKAHEST_AMULET)
            && replica.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 17,
            "The rendered item and its actual charge reach the client");
        source.removeStack(TransmutatorCoreBlockEntity.AMULET);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).isEmpty(), "Taking the amulet clears the old client image");
        ItemStack replacement = new ItemStack(ModItems.ALKAHEST_AMULET);
        replacement.set(ModDataComponents.ESSENCE_CHARGE, 0);
        source.setStack(TransmutatorCoreBlockEntity.AMULET, replacement);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 0,
            "A depleted amulet is still rendered and shows zero charge");
        source.clear();
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).isEmpty(), "Clearing inventory also removes the client image");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redesignedGuiSlotsRemainUsable(TestContext c) {
        BlockPos pos = tower(c, CY + 32);
        var core = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var h = new net.alkahest.screen.TransmutatorScreenHandler(1, player.getInventory(), core,
            new net.minecraft.screen.ArrayPropertyDelegate(3));
        c.assertTrue(h.getSlot(0).x == 26 && h.getSlot(0).y == 45 && h.getSlot(1).x == 26 && h.getSlot(1).y == 78,
            "Input and amulet hitboxes align with their new frames");
        c.assertTrue(h.getSlot(2).x == 102 && h.getSlot(2).y == 45 && h.getSlot(3).x == 37 && h.getSlot(3).y == 143 && h.getSlot(30).y == 201,
            "Output, inventory and hotbar hitboxes align with their new frames");
        c.assertFalse(h.getSlot(2).canInsert(new ItemStack(Items.STONE)), "Output still rejects insertion");
        player.getInventory().setStack(9, new ItemStack(Items.COPPER_INGOT, 3));
        c.assertFalse(h.quickMove(player, 3).isEmpty(), "Shift-click still inserts a valid input");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 3, "The input stack reaches the core");
        player.getInventory().setStack(10, new ItemStack(ModItems.ALKAHEST_AMULET));
        c.assertFalse(h.quickMove(player, 4).isEmpty(), "Shift-click still inserts the amulet");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).isOf(ModItems.ALKAHEST_AMULET), "The amulet reaches its dedicated slot");
        c.assertFalse(h.quickMove(player, 0).isEmpty(), "Input can still be returned to inventory");
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void missingLaterEssenceNeverConsumesEarlierType(TestContext c) {
        int y = CY + 32;
        BlockPos pos = isolatedPlatform(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        var metallum = jar(c, CX + 3, y, CZ, EssenceType.METALLUM, 60);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 200);
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 60,
            "Missing Ordo cannot repeatedly destroy Metallum while waiting");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2 && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(),
            "An incomplete price never consumes the input or creates output");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 4,
            "Waiting never spends an amulet charge");
        var ordo = jar(c, CX - 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 1);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Providing the missing type completes one operation");
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 57 &&
            ordo.contents().total() + stored(core, EssenceType.ORDO) == 8, "Successful operation spends exactly 3 Metallum and 2 Ordo");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3, "Exactly one charge is spent");
        c.setBlockState(CX + 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.setBlockState(CX - 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void disappearingEssenceKeepsReservationsUntilPayment(TestContext c) {
        int y = CY + 48;
        BlockPos pos = isolatedPlatform(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        var metallum = jar(c, CX + 3, y, CZ, EssenceType.METALLUM, 20);
        var ordo = jar(c, CX - 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 10);
        int reservedOrdo = stored(core, EssenceType.ORDO);
        c.assertTrue(reservedOrdo == 1 && stored(core, EssenceType.METALLUM) == 1, "Both types are partially reserved");
        int savedOrdo = ordo.contents().total();
        ordo.setContents(JarContentsComponent.EMPTY);
        run(c.getWorld(), pos, 400);
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 20 && stored(core, EssenceType.ORDO) == reservedOrdo,
            "A missing jar never spends or drains earlier reservations");
        ordo.setContents(of(EssenceType.ORDO, savedOrdo));
        run(c.getWorld(), pos, 1);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Returning the jar resumes the waiting operation");
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 17 &&
            ordo.contents().total() + stored(core, EssenceType.ORDO) == 8, "Only the successful price was spent");
        c.setBlockState(CX + 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.setBlockState(CX - 3, y, CZ, net.minecraft.block.Blocks.AIR);
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
