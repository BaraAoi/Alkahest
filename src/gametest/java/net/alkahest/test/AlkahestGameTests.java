package net.alkahest.test;

import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.entity.DissolvedEntity;
import net.alkahest.entity.ReagentZoneEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.AlkahestVialItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModWorldGen;
import net.alkahest.research.EarlyResearch;
import net.alkahest.screen.DistilleryScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.List;

public class AlkahestGameTests implements FabricGameTest {
    private static CauldronBlockEntity setupCauldron(TestContext c, int x, int y, int z) {
        c.setBlockState(x, y - 1, z, Blocks.MAGMA_BLOCK);
        c.setBlockState(x, y, z, ModBlocks.ALCHEMY_CAULDRON);
        return (CauldronBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
    }

    private static boolean clickCauldron(CauldronBlockEntity cauldron, PlayerEntity player, ItemStack stack) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return cauldron.useItem(player, Hand.MAIN_HAND);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void binaryAlkahestBrewAnnihilationAndExplosion(TestContext c) {
        CauldronBlockEntity cauldron = setupCauldron(c, 3, 2, 3);
        c.setBlockState(4, 2, 3, Blocks.CHEST);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        BlockPos chestPos = c.getAbsolutePos(new BlockPos(4, 2, 3));
        PlayerEntity player = c.createMockPlayer(GameMode.SURVIVAL);

        clickCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));

        // 1) Opposite essences without Alchemical Moss annihilate when struck with Diamond
        for (int i = 0; i < 3; i++) cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS)));
        for (int i = 0; i < 2; i++) cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(EssenceType.AQUA)));
        ItemStack diamond = new ItemStack(Items.DIAMOND, 2);
        c.assertTrue(clickCauldron(cauldron, player, diamond), "Diamond triggers antipode annihilation");
        c.assertTrue(diamond.getCount() == 2, "Annihilation does not consume diamond");
        c.assertTrue(cauldron.essences().getOrDefault(EssenceType.IGNIS, 0) == 1, "3 - 2 = 1 Ignis remains");
        c.assertTrue(cauldron.essences().getOrDefault(EssenceType.AQUA, 0) == 0, "All 2 Aqua annihilated");
        c.assertTrue(c.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.DIRTY), "Annihilation clouds the water");

        // 2) All 6 Prima essences without Alchemical Moss explode the cauldron and spawn DissolvedEntity
        clickCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        for (EssenceType prima : List.of(EssenceType.IGNIS, EssenceType.AQUA, EssenceType.TERRA,
                EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO)) {
            cauldron.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(prima)));
        }
        c.assertTrue(clickCauldron(cauldron, player, diamond), "Unstabilized 6-Prima synthesis triggers explosion");
        c.assertTrue(diamond.getCount() == 1, "Explosion consumes 1 diamond");
        c.assertTrue(c.getWorld().getBlockState(pos).isAir(), "Cauldron block shattered by explosion");
        c.assertTrue(c.getWorld().getBlockState(chestPos).isOf(Blocks.CHEST), "Nearby player chest is not griefed");
        var spawned = c.getWorld().getEntitiesByClass(DissolvedEntity.class, new Box(pos).expand(2.5), DissolvedEntity::isAlive);
        c.assertTrue(spawned.size() == 1, "Dissolved mutant spawned at shattered cauldron");
        spawned.forEach(DissolvedEntity::discard);

        // 3) Stabilized with Alchemical Moss + Ender Pearl + all 6 Prima crystals + Diamond -> Alkahest Vial (32 charges)
        CauldronBlockEntity stabilized = setupCauldron(c, 3, 2, 3);
        clickCauldron(stabilized, player, new ItemStack(Items.WATER_BUCKET));
        c.assertTrue(stabilized.dissolveOne(new ItemStack(ModBlocks.ALCHEMICAL_MOSS)), "Alchemical moss dissolves");
        c.assertTrue(stabilized.isMossStabilized(), "Cauldron marked as moss-stabilized");
        c.assertTrue(stabilized.dissolveOne(new ItemStack(Items.ENDER_PEARL)), "Ender pearl dissolves");
        for (EssenceType prima : List.of(EssenceType.IGNIS, EssenceType.AQUA, EssenceType.TERRA,
                EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO)) {
            c.assertTrue(stabilized.dissolveOne(new ItemStack(ModItems.CRYSTALS.get(prima))), "Prima crystal dissolves: " + prima);
        }
        c.assertTrue(clickCauldron(stabilized, player, diamond), "Stabilized synthesis succeeds");
        c.assertTrue(diamond.isEmpty(), "Final diamond consumed");
        ItemStack brewedVial = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(ModItems.ALKAHEST_VIAL)) {
                brewedVial = s;
                break;
            }
        }
        c.assertTrue(!brewedVial.isEmpty() && AlkahestVialItem.charges(brewedVial) == 32,
            "Brewed Alkahest Vial must carry 32 solvent charges");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 90)
    public void dissolvedAcidPuddleAndLoot(TestContext c) {
        c.setBlockState(3, 1, 3, Blocks.STONE);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        DissolvedEntity mutant = new DissolvedEntity(ModEntities.DISSOLVED, c.getWorld());
        mutant.setAiDisabled(true);
        mutant.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        c.getWorld().spawnEntity(mutant);
        c.assertTrue(Math.abs(mutant.getMaxHealth() - 25.0f) < 0.01f, "Dissolved must have 25 HP");

        c.runAtTick(65, () -> {
            Box search = new Box(pos).expand(2.5);
            var puddles = c.getWorld().getEntitiesByClass(
                ReagentZoneEntity.class, search, z -> z.isAlive() && z.kind() == ReagentZoneEntity.Kind.ACID_PUDDLE
            );
            c.assertTrue(!puddles.isEmpty(), "Dissolved must leave an acid puddle every 60 ticks");

            mutant.damage(c.getWorld().getDamageSources().genericKill(), 200.0f);
            var drops = c.getWorld().getEntitiesByClass(ItemEntity.class, search, ItemEntity::isAlive);
            boolean hasSlime = false;
            boolean hasUnstableCrystal = false;
            for (ItemEntity item : drops) {
                ItemStack stack = item.getStack();
                if (stack.isOf(ModItems.CAUSTIC_SLIME)) hasSlime = true;
                if (ModItems.CRYSTALS.containsValue(stack.getItem())) {
                    Float stab = stack.get(ModDataComponents.ESSENCE_STABILITY);
                    if (stab != null && Math.abs(stab - 0.10f) < 0.001f) {
                        hasUnstableCrystal = true;
                    }
                }
                item.discard();
            }
            puddles.forEach(ReagentZoneEntity::discard);
            c.assertTrue(hasSlime, "Dissolved must drop Caustic Slime");
            c.assertTrue(hasUnstableCrystal, "Dissolved must drop a Prima crystal at 0.10 stability");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void distilleryAlkahestFiveSecondMineralMode(TestContext c) {
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        DistilleryBlockEntity d = (DistilleryBlockEntity) c.getWorld().getBlockEntity(pos);
        PlayerEntity player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(pos.getX() + 1, pos.getY(), pos.getZ());
        DistilleryScreenHandler h = (DistilleryScreenHandler) d.createMenu(1, player.getInventory(), player);

        c.assertFalse(h.getSlot(0).canInsert(new ItemStack(Items.IRON_INGOT)), "Raw mode rejects iron ingot");
        c.assertTrue(d.currentMaxProgress() == 1200, "Raw mode takes 1200 ticks");

        // Shift-click Alkahest Vial into catalyst slot (screen slot 44 -> inventory slot 9)
        ItemStack vial = new ItemStack(ModItems.ALKAHEST_VIAL);
        vial.set(ModDataComponents.SOLVENT_CHARGES, 2);
        player.getInventory().setStack(9, vial);
        c.assertFalse(h.quickMove(player, 8).isEmpty(), "Shift-click moves Alkahest Vial into catalyst slot");
        c.assertTrue(d.hasAlkahest() && d.currentMaxProgress() == 100, "Alkahest mode switches cycle to 100 ticks (5s)");
        c.assertTrue(h.getSlot(0).canInsert(new ItemStack(Items.IRON_INGOT)), "Alkahest mode accepts iron ingot");

        d.setStack(0, new ItemStack(Items.IRON_INGOT, 2));
        for (int t = 0; t < 100; t++) {
            DistilleryBlockEntity.tick(c.getWorld(), pos, d);
        }

        c.assertTrue(d.getStack(0).getCount() == 1, "1 iron ingot distilled in exactly 100 ticks");
        c.assertTrue(AlkahestVialItem.charges(d.getStack(DistilleryBlockEntity.CATALYST)) == 1, "1 solvent charge consumed");

        int metallum = 0, terra = 0, ash = 0;
        for (int slot = DistilleryBlockEntity.OUTPUT_START; slot < DistilleryBlockEntity.OUTPUT_END; slot++) {
            ItemStack out = d.getStack(slot);
            if (out.isEmpty()) continue;
            if (out.isOf(ModItems.ASH)) ash += out.getCount();
            if (out.isOf(ModItems.CRYSTALS.get(EssenceType.METALLUM))) {
                metallum += out.getCount();
                float stab = out.getOrDefault(ModDataComponents.ESSENCE_STABILITY, 0f);
                c.assertTrue(stab >= 0.45f && stab <= 0.60f, "Alkahest crystal stability must be 45-60%: " + stab);
            }
            if (out.isOf(ModItems.CRYSTALS.get(EssenceType.TERRA))) {
                terra += out.getCount();
            }
        }
        c.assertTrue(ash == 0 && metallum == 6 && terra == 2,
            "Iron ingot must yield 6 Metallum + 2 Terra crystals and 0 ash");

        // Second cycle consumes final charge and leaves an empty glass bottle
        for (int t = 0; t < 100; t++) {
            DistilleryBlockEntity.tick(c.getWorld(), pos, d);
        }
        c.assertTrue(d.getStack(DistilleryBlockEntity.CATALYST).isOf(Items.GLASS_BOTTLE),
            "Depleted Alkahest Vial becomes an empty glass bottle");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theAlkahestResearchAndAmuletCharging(TestContext c) {
        CauldronBlockEntity cauldron = setupCauldron(c, 3, 2, 3);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        ServerPlayerEntity player = c.createMockCreativeServerPlayerInWorld();

        // Verify Alkahest Amulet crafting recipe, 5-uses-per-charge efficiency, and Ordo recharging
        var input = CraftingRecipeInput.create(3, 3, List.of(
            ItemStack.EMPTY, new ItemStack(Items.STRING), ItemStack.EMPTY,
            new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)), new ItemStack(Items.GOLD_INGOT),
            ItemStack.EMPTY, new ItemStack(ModItems.ALKAHEST_VIAL), ItemStack.EMPTY
        ));
        var recipe = c.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, c.getWorld());
        c.assertTrue(recipe.isPresent() && recipe.get().value().craft(input, c.getWorld().getRegistryManager()).isOf(ModItems.ALKAHEST_AMULET),
            "Alkahest Amulet crafting recipe works");

        PlayerEntity survival = c.createMockPlayer(GameMode.SURVIVAL);
        ItemStack amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        for (int i = 0; i < 5; i++) {
            c.setBlockState(5, 2, 3, Blocks.AIR);
            c.setBlockState(5, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
            CauldronBlockEntity dry = (CauldronBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(5, 2, 3)));
            c.assertTrue(clickCauldron(dry, survival, amulet), "Amulet fills dry cauldron on use #" + (i + 1));
        }
        c.assertTrue(amulet.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) == 99,
            "5 amulet activations consume exactly 1 Ordo charge");

        amulet.set(ModDataComponents.ESSENCE_CHARGE, 85);
        survival.getInventory().setStack(0, amulet);
        c.setBlockState(5, 3, 5, ModBlocks.JAR);
        JarBlockEntity jar = (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(5, 3, 5)));
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 2, 0.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, survival) == EssenceType.ORDO, "Charger recharges Alkahest Amulet from Ordo jar");
        c.assertTrue(amulet.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) == 95 && jar.contents().total() == 1,
            "One Ordo unit restores 10 charges to Alkahest Amulet");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void alchemicalWastesBiomeRegisteredInOverworld(TestContext c) {
        var biomeRegistry = c.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
        var wastes = biomeRegistry.getOrEmpty(ModWorldGen.ALCHEMICAL_WASTES);
        c.assertTrue(wastes.isPresent(), "alkahest:alchemical_wastes biome must be loaded");
        c.assertTrue(wastes.get().getEffects().getFogColor() == 0x8A2BE2, "Wastes fog color matches #8A2BE2");
        c.assertTrue(wastes.get().getEffects().getGrassColor().orElse(0) == 0x5D3FD3, "Wastes grass color matches #5D3FD3");

        var paramRegistry = c.getWorld().getRegistryManager().get(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldKey = RegistryKey.of(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, Identifier.ofVanilla("overworld"));
        var overworldEntry = paramRegistry.getEntry(overworldKey).orElseThrow();
        MultiNoiseBiomeSource overworldSource = MultiNoiseBiomeSource.create(overworldEntry);
        boolean inOverworldSource = overworldSource.getBiomes().stream()
            .anyMatch(entry -> entry.matchesKey(ModWorldGen.ALCHEMICAL_WASTES));
        c.assertTrue(inOverworldSource, "alkahest:alchemical_wastes must be injected into Overworld MultiNoiseBiomeSource");
        var sampled = overworldSource.getBiomeAtPoint(
            MultiNoiseUtil.createNoiseValuePoint(0.05f, 0.60f, 0.25f, 0.35f, 0.0f, 0.75f)
        );
        c.assertTrue(sampled.matchesKey(ModWorldGen.ALCHEMICAL_WASTES),
            "Overworld MultiNoiseBiomeSource must sample alkahest:alchemical_wastes in its climate niche");

        c.setBlockState(3, 1, 3, Blocks.MOSS_BLOCK);
        c.setBlockState(3, 2, 3, ModBlocks.ALCHEMICAL_MOSS);
        BlockPos mossPos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        c.assertTrue(c.getWorld().getBlockState(mossPos).getLuminance() == 7, "Alchemical Moss emits light level 7");
        c.complete();
    }
}
