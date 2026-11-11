package net.alkahest.atmosphere;

import net.alkahest.atmosphere.AtmosphereEvents;
import net.alkahest.atmosphere.AtmosphereMath;
import net.alkahest.atmosphere.AtmosphereState;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.List;

public class AtmosphereGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blocksChangeBaselineAndFluxPersists(TestContext c) {
        var w = c.getWorld(); BlockPos pos = c.getAbsolutePos(new BlockPos(3, 3, 3));
        w.setBlockState(pos, Blocks.AIR.getDefaultState());
        var initial = new AtmosphereState().baseline(w, pos);
        w.setBlockState(pos, Blocks.WATER.getDefaultState());
        var changed = new AtmosphereState().baseline(w, pos);
        c.assertTrue(Math.abs(changed.get(EssenceType.AQUA) - initial.get(EssenceType.AQUA) - 1.0 / 256) < 1e-8,
            "One placed water block changes the real 4096-block cell baseline");
        c.assertTrue(Math.abs(changed.get(EssenceType.AER) - initial.get(EssenceType.AER) + 1.0 / 256) < 1e-8,
            "Replaced air no longer contributes Aer");
        var local = new AtmosphereState();
        local.add(w, pos, EssenceType.VAPOR, 8);
        var levels = local.levels(w, pos);
        c.assertTrue(Math.abs(levels.get(EssenceType.AQUA) - changed.get(EssenceType.AQUA) - 4) < 1e-8, "Vapor adds half Aqua");
        c.assertTrue(Math.abs(levels.get(EssenceType.IGNIS) - changed.get(EssenceType.IGNIS) - 4) < 1e-8, "Vapor adds half Ignis");
        var nbt = local.writeNbt(new NbtCompound(), w.getRegistryManager());
        var restored = AtmosphereState.read(nbt, w.getRegistryManager());
        c.assertTrue(Math.abs(restored.levels(w, pos).get(EssenceType.IGNIS) - levels.get(EssenceType.IGNIS)) < 1e-8,
            "Saved dimension state preserves atmospheric excess");
        nbt.getList("Cells", NbtElement.COMPOUND_TYPE).getCompound(0).putLong("Time", w.getTime() - AtmosphereMath.HALF_LIFE);
        var aged = AtmosphereState.read(nbt, w.getRegistryManager());
        c.assertTrue(Math.abs(aged.levels(w, pos).get(EssenceType.IGNIS) - changed.get(EssenceType.IGNIS) - 2) < 1e-8,
            "Unloaded excess ages lazily toward its baseline");
        c.assertTrue(AtmosphereState.cellOrigin(new BlockPos(-1, -1, -17)).equals(new BlockPos(-16, -16, -32)),
            "Negative coordinates use floor division");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void idleCauldronGraduallyEvaporatesAndPersists(TestContext c) {
        var w = c.getWorld(); c.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        c.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        var cauldron = (CauldronBlockEntity) w.getBlockEntity(pos);
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET)); cauldron.fill(player, Hand.MAIN_HAND);
        for (int i = 0; i < 3; i++) c.assertTrue(cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS))), "Dissolves crystal");
        double before = AtmosphereState.of(w).levels(w, pos).get(EssenceType.IGNIS);
        for (int i = 0; i < 1399; i++) CauldronBlockEntity.tick(w, pos, cauldron);
        c.assertTrue(cauldron.essences().get(EssenceType.IGNIS) == 3, "No loss during grace or first ten seconds");
        var nbt = cauldron.createNbt(w.getRegistryManager());
        cauldron.readComponentlessNbt(nbt, w.getRegistryManager());
        CauldronBlockEntity.tick(w, pos, cauldron);
        c.assertTrue(cauldron.essences().get(EssenceType.IGNIS) == 2, "Exactly one unit evaporates after reload");
        c.assertTrue(Math.abs(AtmosphereState.of(w).levels(w, pos).get(EssenceType.IGNIS) - before - 1) < 1e-8,
            "The lost unit increases atmospheric Ignis");
        nbt = cauldron.createNbt(w.getRegistryManager()); nbt.putBoolean("Water", false);
        cauldron.readComponentlessNbt(nbt, w.getRegistryManager());
        for (int i = 0; i < 200; i++) CauldronBlockEntity.tick(w, pos, cauldron);
        c.assertTrue(cauldron.essences().get(EssenceType.IGNIS) == 1, "Leftover essence also evaporates without water");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changingCauldronRestartsItsGracePeriod(TestContext c) {
        var w = c.getWorld(); c.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        c.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        var cauldron = (CauldronBlockEntity) w.getBlockEntity(pos);
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET)); cauldron.fill(player, Hand.MAIN_HAND);
        var crystal = new ItemStack(ModItems.CRYSTALS.get(EssenceType.AQUA));
        c.assertTrue(cauldron.dissolveOne(crystal), "Initial essence dissolved");
        for (int i = 0; i < 1300; i++) CauldronBlockEntity.tick(w, pos, cauldron);
        c.assertTrue(cauldron.dissolveOne(crystal), "Adding essence counts as work");
        for (int i = 0; i < 1399; i++) CauldronBlockEntity.tick(w, pos, cauldron);
        c.assertTrue(cauldron.essences().get(EssenceType.AQUA) == 2, "Work restarts the complete grace and evaporation interval");
        CauldronBlockEntity.tick(w, pos, cauldron);
        c.assertTrue(cauldron.essences().get(EssenceType.AQUA) == 1, "Loss resumes only after the fresh interval");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void successfulMachineCyclesAndOverflowEmit(TestContext c) {
        var w = c.getWorld(); BlockPos p = c.getAbsolutePos(new BlockPos(3, 2, 3));
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY_TIER2);
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3, 4, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3, 5, 3, ModBlocks.DISTILLERY_BELLOWS);
        var cube = (Tier2DistilleryBlockEntity) w.getBlockEntity(p);
        var air = AtmosphereState.of(w); double before = air.levels(w, p).get(EssenceType.AQUA);
        cube.setStack(0, new ItemStack(Items.WATER_BUCKET));
        for (int i = 0; i < Tier2DistilleryBlockEntity.DURATION; i++) Tier2DistilleryBlockEntity.tick(w, p, cube);
        c.assertTrue(Math.abs(air.levels(w, p).get(EssenceType.AQUA) - before - .8) < 1e-8,
            "Tier-two release is ten percent of the capped actual Aqua output");
        c.setBlockState(5, 2, 3, ModBlocks.DISTILLERY);
        BlockPos q = c.getAbsolutePos(new BlockPos(5, 2, 3));
        var crude = (DistilleryBlockEntity) w.getBlockEntity(q);
        double start = air.levels(w, q).get(EssenceType.AQUA);
        crude.setStack(0, new ItemStack(Items.WATER_BUCKET));
        for (int i = 0; i < DistilleryBlockEntity.RAW_DURATION; i++) DistilleryBlockEntity.tick(w, q, crude);
        c.assertTrue(Math.abs(air.levels(w, q).get(EssenceType.AQUA) - start - .8) < 1e-8, "Crude cube emits once per successful cycle");
        c.setBlockState(1, 2, 3, ModBlocks.JAR); BlockPos jarPos = c.getAbsolutePos(new BlockPos(1, 2, 3));
        var jar = (JarBlockEntity) w.getBlockEntity(jarPos);
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 95, 1))));
        ItemStack donor = new ItemStack(ModBlocks.JAR);
        JarItem.set(donor, new JarContentsComponent(List.of(new EssenceStack(EssenceType.IGNIS, 10, 1))));
        var player = c.createMockPlayer(GameMode.SURVIVAL); player.setStackInHand(Hand.MAIN_HAND, donor);
        var hit = new BlockHitResult(Vec3d.ofCenter(jarPos), Direction.UP, jarPos, false);
        double initial = air.levels(w, jarPos).get(EssenceType.IGNIS);
        w.getBlockState(jarPos).onUseWithItem(donor, w, player, Hand.MAIN_HAND, hit);
        c.assertTrue(jar.contents().total() == 100 && JarItem.contents(donor).total() == 0, "Overflow actually consumes the excess");
        c.assertTrue(Math.abs(air.levels(w, jarPos).get(EssenceType.IGNIS) - initial - 5) < 1e-8, "Exactly the lost five units enter the air");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void manifestationChecksDistanceCapAndSpendsExcess(TestContext c) {
        var w = c.getWorld();
        BlockPos origin = AtmosphereState.cellOrigin(c.getAbsolutePos(new BlockPos(3, 3, 3)));
        BlockPos target = origin.add(14, 8, 14);
        w.setBlockState(target, Blocks.AIR.getDefaultState());
        w.setBlockState(target.up(), Blocks.AIR.getDefaultState());
        var player = c.createMockCreativeServerPlayerInWorld();
        player.setPosition(origin.getX() + 1.5, target.getY(), origin.getZ() + 1.5);
        c.assertTrue(w.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL, "Test world must permit hostile mobs: " + w.getDifficulty());
        var rule = w.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING);
        boolean oldRule = rule.get();
        rule.set(false, w.getServer());
        c.assertFalse(AtmosphereEvents.spawnAt(w, origin, target, EssenceType.IGNIS, List.of(player)), "Disabled mob spawning prevents manifestation");
        rule.set(true, w.getServer());
        try {
        c.assertTrue(w.isChunkLoaded(target), "Target chunk must be loaded");
        c.assertTrue(player.getServerWorld() == w, "Mock player belongs to the test dimension");
        c.assertTrue(player.squaredDistanceTo(target.getX()+.5, target.getY(), target.getZ()+.5) >= 64
            && player.squaredDistanceTo(target.getX()+.5, target.getY(), target.getZ()+.5) <= 576, "Player must be in the 8–24 block range");
        c.assertTrue(w.getEntitiesByClass(net.alkahest.entity.ElementalEntity.class,
            new net.minecraft.util.math.Box(target).expand(24), e -> e.isAlive()).isEmpty(), "No nearby elemental before the test");
        c.assertTrue(net.alkahest.entity.ElementalEntity.canSpawn(net.alkahest.registry.ModEntities.ELEMENTALS.get(EssenceType.IGNIS),
            w, net.minecraft.entity.SpawnReason.EVENT, target, w.random), "Target must have space for an elemental");
        var air = AtmosphereState.of(w); air.add(w, target, EssenceType.IGNIS, 40);
        double before = air.levels(w, target).get(EssenceType.IGNIS);
        c.assertTrue(AtmosphereEvents.spawnAt(w, origin, target, EssenceType.IGNIS, List.of(player)), "Valid player distance and clear loaded space allow manifestation");
        c.assertTrue(Math.abs(air.levels(w, target).get(EssenceType.IGNIS) - before + 25) < 1e-8,
            "Successful manifestation spends only 25 excess Ignis");
        var mobs = w.getEntitiesByClass(net.alkahest.entity.ElementalEntity.class,
            new net.minecraft.util.math.Box(target).expand(24), e -> e.isAlive());
        c.assertTrue(mobs.size() == 1 && mobs.getFirst().essenceType() == EssenceType.IGNIS, "The selected elemental actually appears");
        c.assertFalse(AtmosphereEvents.spawnAt(w, origin, target, EssenceType.IGNIS, List.of(player)), "A nearby elemental enforces the local cap");
        mobs.forEach(net.minecraft.entity.Entity::discard);
        player.setPosition(target.getX() + .5, target.getY(), target.getZ() + .5);
        c.assertFalse(AtmosphereEvents.spawnAt(w, origin, target, EssenceType.IGNIS, List.of(player)), "Never spawn on top of a player");
        } finally {
            rule.set(oldRule, w.getServer());
            player.discard();
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void manifestationsNeverForceLoadChunks(TestContext c) {
        var w = c.getWorld(); BlockPos unloaded = new BlockPos(29000000, 64, 29000000);
        c.assertFalse(w.isChunkLoaded(unloaded), "Distant chunk starts unloaded");
        c.assertFalse(AtmosphereEvents.spawnAt(w, unloaded, unloaded, EssenceType.IGNIS), "Unloaded target is rejected");
        c.assertFalse(w.isChunkLoaded(unloaded), "Attempt did not force-load a chunk");
        c.assertFalse(AtmosphereEvents.spawnAt(w, c.getAbsolutePos(new BlockPos(3, 2, 3)), unloaded, EssenceType.IGNIS),
            "Manifestation cannot escape its source cell");
        c.complete();
    }
}
