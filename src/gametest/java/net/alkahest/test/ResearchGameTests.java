package net.alkahest.test;

import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class ResearchGameTests implements FabricGameTest {
    private static boolean useOnCauldron(CauldronBlockEntity cauldron, ServerPlayerEntity player, ItemStack stack) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return cauldron.useItem(player, Hand.MAIN_HAND);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hypothesisMatching(TestContext context) {
        var coal = new ItemStack(Items.COAL);
        var sand = new ItemStack(Items.SAND);
        var sugar = new ItemStack(Items.SUGAR);
        var basicsEssences = Map.of(EssenceType.IGNIS, 3, EssenceType.TERRA, 1);
        context.assertTrue(EarlyResearch.identify(0, basicsEssences, coal) == EarlyResearch.Attempt.NONE, "Locked research cannot trigger");
        long f = EarlyResearch.DISCOVERED;
        context.assertTrue(EarlyResearch.identify(f, Map.of(EssenceType.IGNIS, 2), coal) == EarlyResearch.Attempt.NONE, "Insufficient subject essences must not trigger");
        context.assertTrue(EarlyResearch.identify(f, basicsEssences, coal) == EarlyResearch.Attempt.COAL, "Coal catalyst with charcoal essences must match");
        context.assertTrue(EarlyResearch.identify(f, basicsEssences, sand) == EarlyResearch.Attempt.SAND, "Sand must fail");
        context.assertTrue(EarlyResearch.identify(f, basicsEssences, sugar) == EarlyResearch.Attempt.SUGAR, "Sugar must fail");
        context.assertTrue(EarlyResearch.identify(f | EarlyResearch.FAILED_SAND, basicsEssences, sand) == EarlyResearch.Attempt.ALREADY_FAILED, "Failed choice must be remembered");
        context.assertTrue(EarlyResearch.identify(f | EarlyResearch.COMPLETED, basicsEssences, coal) == EarlyResearch.Attempt.NONE, "Completed research must not repeat");
        context.assertTrue(EarlyResearch.identify(f, basicsEssences, new ItemStack(Items.GLASS_BOTTLE)) == EarlyResearch.Attempt.NONE, "Normal recipe catalyst must not be intercepted");

        var rawEssences = Map.of(EssenceType.HERBA, 3, EssenceType.AQUA, 1);
        var copper = new ItemStack(Items.COPPER_INGOT);
        var sightEssences = Map.of(EssenceType.ORDO, 1);
        var glass = new ItemStack(Items.GLASS_PANE);
        context.assertTrue(EarlyResearch.identify(0, rawEssences, copper) == EarlyResearch.Attempt.NONE, "Locked distillation must not trigger");
        context.assertTrue(EarlyResearch.identify(EarlyResearch.RAW_DISCOVERED, rawEssences, copper) == EarlyResearch.Attempt.RAW_COPPER, "Raw research matches dissolved paste + copper");
        context.assertTrue(EarlyResearch.identify(EarlyResearch.RAW_DISCOVERED | EarlyResearch.FAILED_IRON, rawEssences, new ItemStack(Items.IRON_INGOT)) == EarlyResearch.Attempt.ALREADY_FAILED, "Raw failures must be remembered");
        context.assertTrue(EarlyResearch.identify(0, sightEssences, glass) == EarlyResearch.Attempt.NONE, "Locked sight must not trigger");
        context.assertTrue(EarlyResearch.identify(EarlyResearch.SIGHT_DISCOVERED, sightEssences, glass) == EarlyResearch.Attempt.SIGHT_GLASS, "Sight research matches dissolved Ordo + glass pane");
        context.assertTrue(EarlyResearch.identify(EarlyResearch.SIGHT_DISCOVERED | EarlyResearch.FAILED_TORCH, sightEssences, new ItemStack(Items.TORCH)) == EarlyResearch.Attempt.ALREADY_FAILED, "Sight failures must be remembered");
        context.assertTrue(EarlyResearch.identify(EarlyResearch.SIGHT_COMPLETED | EarlyResearch.SIGHT_DISCOVERED, sightEssences, glass) == EarlyResearch.Attempt.NONE, "Completed sight must not repeat");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void researchViaCauldron(TestContext context) {
        context.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        context.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        var cauldron = (CauldronBlockEntity) context.getWorld().getBlockEntity(pos);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        EarlyResearch.openJournal(player);
        EarlyResearch.discoverCauldron(player, context.getWorld(), pos);
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.DISCOVERED) != 0, "Research should be unlocked");

        useOnCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.CHARCOAL)), "Charcoal dissolves directly on right-click");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.SAND)), "Sand hypothesis should be processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_SAND) != 0, "Wrong hypothesis must be recorded");
        context.assertTrue(cauldron.essences().isEmpty(), "Subject essences must be consumed by hypothesis");
        context.assertTrue(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.DIRTY), "Sand should visibly dirty the water");

        var saved = cauldron.createNbt(context.getWorld().getRegistryManager());
        CauldronBlockEntity restored = new CauldronBlockEntity(pos, cauldron.getCachedState());
        restored.readComponentlessNbt(saved, context.getWorld().getRegistryManager());
        context.assertTrue(restored.createNbt(context.getWorld().getRegistryManager()).getBoolean("DirtyWater"), "Dirty water must persist in NBT");
        NbtCompound oldSave = new NbtCompound();
        oldSave.putBoolean("Water", true);
        restored.readComponentlessNbt(oldSave, context.getWorld().getRegistryManager());
        var migrated = restored.createNbt(context.getWorld().getRegistryManager());
        context.assertTrue(migrated.getBoolean("Water") && !migrated.getBoolean("DirtyWater"), "Old water should migrate as clean water");

        useOnCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.DIRTY), "A water bucket should clean dirty water");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.CHARCOAL)), "Charcoal dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.SUGAR)), "Sugar hypothesis should be processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_SUGAR) != 0, "Sugar failure must be recorded");
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.DIRTY), "Sugar should not dirty the water");

        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.CHARCOAL)), "Charcoal dissolves");
        context.assertFalse(useOnCauldron(cauldron, player, new ItemStack(Items.SAND)), "Repeated failed hypothesis should be rejected");
        context.assertTrue(!cauldron.essences().isEmpty(), "Rejected repeat must preserve dissolved essences");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.COAL)), "Correct coal hypothesis should work");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.COMPLETED) != 0, "Completion must be recorded");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(context.getWorld().getServer().getAdvancementLoader());
        context.assertTrue((EarlyResearch.flags(player) & (EarlyResearch.COMPLETED | EarlyResearch.FAILED_SAND)) == (EarlyResearch.COMPLETED | EarlyResearch.FAILED_SAND), "Completion and failed choices must survive save and reload");

        // Brew purified water via dissolved essences + bottle catalyst.
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.COAL)), "Coal dissolves for Ignis");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.APPLE)), "Apple dissolves for Aqua");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GLASS_BOTTLE)), "Bottle catalyst brews purified water");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.REACTIVE_DISCOVERED) != 0, "Purified water should unlock reactive research");

        useOnCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GUNPOWDER)), "Gunpowder dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.FEATHER)), "Feather failure processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_FEATHER) != 0, "Feather failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GUNPOWDER)), "Gunpowder dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.CLAY_BALL)), "Clay failure processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_CLAY) != 0, "Clay failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GUNPOWDER)), "Gunpowder dissolves");
        context.assertFalse(player.getInventory().contains(new ItemStack(ModItems.FIRE_DUST)), "Hypothesis should not produce fire dust");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.REDSTONE)), "Redstone completes reactive research");
        context.assertFalse(player.getInventory().contains(new ItemStack(ModItems.FIRE_DUST)), "Research attempt only completes research");
        context.assertTrue(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Research leaves water in the cauldron");

        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(context.getWorld().getServer().getAdvancementLoader());
        context.assertTrue((EarlyResearch.flags(player) & (EarlyResearch.REACTIVE_COMPLETED | EarlyResearch.FAILED_FEATHER)) == (EarlyResearch.REACTIVE_COMPLETED | EarlyResearch.FAILED_FEATHER), "Second research must survive save and reload");

        // Brew fire dust via dissolved essences + gunpowder catalyst.
        cauldron.crystallize(); // clear any leftover residue from earlier apple
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.COAL)), "Coal dissolves for Ignis");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.REDSTONE)), "Redstone dissolves for Potentia");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GUNPOWDER)), "Gunpowder catalyst brews fire dust");
        int result = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ModItems.FIRE_DUST)) result += stack.getCount();
        }
        context.assertTrue(result == 2, "Recipe should produce two fire dust: " + result);
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Recipe consumes water");

        EarlyResearch.discoverSight(player, context.getWorld(), pos);
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.SIGHT_DISCOVERED) == 0, "Sight needs both parents");

        useOnCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.DANDELION)), "Dandelion dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.POPPY)), "Poppy dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.WHEAT_SEEDS)), "Wheat seeds catalyst brews herbal paste");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.RAW_DISCOVERED) != 0, "Herbal paste unlocks raw distillation");

        useOnCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        cauldron.crystallize();
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.HERBAL_PASTE)), "Herbal paste dissolves for raw hypothesis");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.IRON_INGOT)), "Iron hypothesis fails");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_IRON) != 0, "Iron failure persists");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.HERBAL_PASTE)), "Herbal paste dissolves");
        context.assertFalse(useOnCauldron(cauldron, player, new ItemStack(Items.IRON_INGOT)), "Repeated iron failure rejected");
        context.assertTrue(!cauldron.essences().isEmpty(), "Repeated hypothesis preserves dissolved essences");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.COBBLESTONE)), "Cobblestone hypothesis fails");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.HERBAL_PASTE)), "Herbal paste dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.COPPER_INGOT)), "Copper hypothesis completes raw distillation");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.RAW_COMPLETED) != 0, "Raw research completion persists");

        BlockPos distPos = context.getAbsolutePos(new BlockPos(5, 2, 3));
        context.setBlockState(5, 2, 3, ModBlocks.DISTILLERY);
        DistilleryBlockEntity distillery = (DistilleryBlockEntity) context.getWorld().getBlockEntity(distPos);
        distillery.stacks().set(1, new ItemStack(ModItems.ASH));
        var ashScreen = (net.alkahest.screen.DistilleryScreenHandler) distillery.createMenu(1, player.getInventory(), player);
        ashScreen.quickMove(player, 1);
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.SIGHT_DISCOVERED) == 0, "Ash does not count as a crystal");
        distillery.stacks().set(1, new ItemStack(ModItems.CRYSTALS.get(EssenceType.AQUA)));
        var distilleryScreen = (net.alkahest.screen.DistilleryScreenHandler) distillery.createMenu(2, player.getInventory(), player);
        context.assertFalse(distilleryScreen.quickMove(player, 1).isEmpty(), "Taking a crystal via GUI shift-click should succeed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.SIGHT_DISCOVERED) != 0, "GUI output collection unlocks true sight");

        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO))), "Ordo crystal dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.SPIDER_EYE)), "Spider eye hypothesis fails");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_EYE) != 0, "Eye failure persists");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO))), "Ordo crystal dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.TORCH)), "Torch hypothesis fails");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_TORCH) != 0, "Torch failure persists");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO))), "Ordo crystal dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GLASS_PANE)), "Glass pane completes true sight");

        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(context.getWorld().getServer().getAdvancementLoader());
        long expected = EarlyResearch.REACTIVE_COMPLETED | EarlyResearch.RAW_COMPLETED | EarlyResearch.FAILED_IRON | EarlyResearch.FAILED_COBBLE | EarlyResearch.SIGHT_COMPLETED | EarlyResearch.FAILED_EYE | EarlyResearch.FAILED_TORCH;
        context.assertTrue((EarlyResearch.flags(player) & expected) == expected, "Old and new research survive save and reload");

        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.AMETHYST_SHARD)), "Amethyst shard dissolves Ordo and Lux");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.GLASS_PANE)), "Glass pane catalyst brews monocle");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.ALCHEMISTS_MONOCLE)), "Monocle must be delivered");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void eraTwoResearchProgression(TestContext context) {
        context.setBlockState(3, 1, 3, Blocks.MAGMA_BLOCK);
        context.setBlockState(3, 2, 3, ModBlocks.ALCHEMY_CAULDRON);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        var cauldron = (CauldronBlockEntity) context.getWorld().getBlockEntity(pos);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();

        // Advance through True Sight so Era II nodes can unlock
        EarlyResearch.openJournal(player);
        EarlyResearch.discoverCauldron(player, context.getWorld(), pos);
        useOnCauldron(cauldron, player, new ItemStack(Items.WATER_BUCKET));
        useOnCauldron(cauldron, player, new ItemStack(Items.CHARCOAL));
        useOnCauldron(cauldron, player, new ItemStack(Items.COAL));
        EarlyResearch.discoverReactive(player, context.getWorld(), pos);
        EarlyResearch.discoverRaw(player, context.getWorld(), pos);
        useOnCauldron(cauldron, player, new ItemStack(Items.GUNPOWDER));
        useOnCauldron(cauldron, player, new ItemStack(Items.REDSTONE));
        useOnCauldron(cauldron, player, new ItemStack(ModItems.HERBAL_PASTE));
        useOnCauldron(cauldron, player, new ItemStack(Items.COPPER_INGOT));
        EarlyResearch.discoverSight(player, context.getWorld(), pos);
        useOnCauldron(cauldron, player, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        useOnCauldron(cauldron, player, new ItemStack(Items.GLASS_PANE));
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.SIGHT_COMPLETED) != 0, "True Sight must be complete");

        // Unlock both Era II elemental trails
        EarlyResearch.discoverIgnis(player, context.getWorld(), pos);
        EarlyResearch.discoverAqua(player, context.getWorld(), pos);
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.IGNIS_DISCOVERED) != 0, "Trail of Ignis must unlock");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.AQUA_DISCOVERED) != 0, "Abyssal Current must unlock");

        // Chain Ignis: blaze_rod + sugar_cane (fail), feather (fail), coal (success)
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.BLAZE_ROD)), "Blaze rod dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.SUGAR_CANE)), "Sugar cane fails Ignis hypothesis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_CANE) != 0, "Sugar cane failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.BLAZE_ROD)), "Blaze rod dissolves");
        context.assertFalse(useOnCauldron(cauldron, player, new ItemStack(Items.SUGAR_CANE)), "Repeated sugar cane failure rejected");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.FEATHER)), "Feather fails Ignis hypothesis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_IGNIS_FEATHER) != 0, "Feather failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.BLAZE_ROD)), "Blaze rod dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.COAL)), "Coal completes Trail of Ignis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.IGNIS_COMPLETED) != 0, "Trail of Ignis completed");

        // Binding must not unlock before BOTH Ignis and Aqua trails are completed
        EarlyResearch.discoverBinding(player, context.getWorld(), pos);
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.BINDING_DISCOVERED) == 0,
            "Elemental Binding requires both Ignis and Aqua trails");

        // Chain Aqua: prismarine_shard + sand (fail), ice (fail), kelp (success)
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.PRISMARINE_SHARD)), "Prismarine shard dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.SAND)), "Sand fails Aqua hypothesis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_AQUA_SAND) != 0, "Aqua sand failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.PRISMARINE_SHARD)), "Prismarine shard dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.ICE)), "Ice fails Aqua hypothesis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_ICE) != 0, "Aqua ice failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.PRISMARINE_SHARD)), "Prismarine shard dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.KELP)), "Kelp completes Abyssal Current");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.AQUA_COMPLETED) != 0, "Abyssal Current completed");

        // Unlock and complete Elemental Binding: salt + slime_ball (fail), bone_meal (fail), string (success)
        EarlyResearch.discoverBinding(player, context.getWorld(), pos);
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.BINDING_DISCOVERED) != 0, "Elemental Binding unlocked");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.SALT)), "Salt dissolves into Ordo/Terra/Aqua");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.SLIME_BALL)), "Slime ball fails Binding hypothesis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_SLIME) != 0, "Slime failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.SALT)), "Salt dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.BONE_MEAL)), "Bone meal fails Binding hypothesis");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_BONE_MEAL) != 0, "Bone meal failure recorded");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(ModItems.SALT)), "Salt dissolves");
        context.assertTrue(useOnCauldron(cauldron, player, new ItemStack(Items.STRING)), "String completes Elemental Binding");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.BINDING_COMPLETED) != 0, "Elemental Binding completed");

        // Reload advancements and verify all Era II flags persist
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(context.getWorld().getServer().getAdvancementLoader());
        long eraTwoFlags = EarlyResearch.IGNIS_COMPLETED | EarlyResearch.AQUA_COMPLETED | EarlyResearch.BINDING_COMPLETED
            | EarlyResearch.FAILED_CANE | EarlyResearch.FAILED_AQUA_SAND | EarlyResearch.FAILED_SLIME;
        context.assertTrue((EarlyResearch.flags(player) & eraTwoFlags) == eraTwoFlags,
            "Era II research flags must survive save and reload");
        context.complete();
    }
}
