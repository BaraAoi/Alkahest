package net.alkahest.test;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class ResearchGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hypothesisMatching(TestContext context) {
        var charcoal = new ItemStack(Items.CHARCOAL);
        var coal = new ItemStack(Items.COAL);
        var sand = new ItemStack(Items.SAND);
        var sugar = new ItemStack(Items.SUGAR);
        context.assertTrue(EarlyResearch.identify(0, List.of(charcoal, coal)) == EarlyResearch.Attempt.NONE, "Locked research cannot trigger");
        int f = EarlyResearch.DISCOVERED;
        context.assertTrue(EarlyResearch.identify(f, List.of(coal, charcoal)) == EarlyResearch.Attempt.COAL, "Inputs may be reversed");
        context.assertTrue(EarlyResearch.identify(f, List.of(charcoal, sand)) == EarlyResearch.Attempt.SAND, "Sand must fail");
        context.assertTrue(EarlyResearch.identify(f, List.of(sugar, charcoal)) == EarlyResearch.Attempt.SUGAR, "Sugar must fail");
        context.assertTrue(EarlyResearch.identify(f | EarlyResearch.FAILED_SAND, List.of(charcoal, sand)) == EarlyResearch.Attempt.ALREADY_FAILED, "Failed choice must be remembered");
        context.assertTrue(EarlyResearch.identify(f | EarlyResearch.COMPLETED, List.of(charcoal, coal)) == EarlyResearch.Attempt.NONE, "Completed research must not repeat");
        context.assertTrue(EarlyResearch.identify(f, List.of(charcoal, coal, sugar)) == EarlyResearch.Attempt.NONE, "Extra ingredients are not a hypothesis");
        context.assertTrue(EarlyResearch.identify(f, List.of(coal, new ItemStack(Items.GLASS_BOTTLE))) == EarlyResearch.Attempt.NONE, "Normal recipe must not be intercepted");
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
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        cauldron.stacks().set(0, new ItemStack(Items.CHARCOAL));
        cauldron.stacks().set(1, new ItemStack(Items.SAND));
        context.assertTrue(cauldron.activate(player), "Wrong hypothesis should be processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_SAND) != 0, "Wrong hypothesis must be recorded");
        context.assertTrue(cauldron.stacks().stream().allMatch(ItemStack::isEmpty), "Failed ingredients must be consumed");
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
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.DIRTY), "A water bucket should clean dirty water");
        cauldron.stacks().set(0, new ItemStack(Items.CHARCOAL));
        cauldron.stacks().set(1, new ItemStack(Items.SUGAR));
        context.assertTrue(cauldron.activate(player), "Sugar hypothesis should be processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_SUGAR) != 0, "Sugar failure must be recorded");
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.DIRTY), "Sugar should not dirty the water");
        cauldron.stacks().set(0, new ItemStack(Items.CHARCOAL));
        cauldron.stacks().set(1, new ItemStack(Items.SAND));
        context.assertFalse(cauldron.activate(player), "Repeated failed hypothesis should be rejected");
        context.assertTrue(!cauldron.stacks().get(0).isEmpty(), "Repeated attempt must not consume ingredients");
        cauldron.stacks().set(1, new ItemStack(Items.COAL));
        context.assertTrue(cauldron.activate(player), "Correct hypothesis should work");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.COMPLETED) != 0, "Completion must be recorded");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(context.getWorld().getServer().getAdvancementLoader());
        context.assertTrue((EarlyResearch.flags(player) & (EarlyResearch.COMPLETED | EarlyResearch.FAILED_SAND)) == (EarlyResearch.COMPLETED | EarlyResearch.FAILED_SAND), "Completion and failed choices must survive save and reload");
        cauldron.stacks().set(0, new ItemStack(Items.COAL));
        cauldron.stacks().set(1, new ItemStack(Items.GLASS_BOTTLE));
        context.assertTrue(cauldron.activate(player), "Research must not prevent existing recipes");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.REACTIVE_DISCOVERED) != 0, "Purified water should unlock the second research");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        cauldron.fill(player, Hand.MAIN_HAND);
        cauldron.stacks().set(0, new ItemStack(Items.GUNPOWDER));
        cauldron.stacks().set(1, new ItemStack(Items.FEATHER));
        context.assertTrue(cauldron.activate(player), "Second research wrong hypothesis should be processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_FEATHER) != 0, "Feather failure must be recorded");
        cauldron.stacks().set(0, new ItemStack(Items.GUNPOWDER));
        cauldron.stacks().set(1, new ItemStack(Items.CLAY_BALL));
        context.assertTrue(cauldron.activate(player), "Clay hypothesis should be processed");
        context.assertTrue((EarlyResearch.flags(player) & EarlyResearch.FAILED_CLAY) != 0, "Clay failure must be recorded");
        cauldron.stacks().set(0, new ItemStack(Items.GUNPOWDER));
        cauldron.stacks().set(1, new ItemStack(Items.REDSTONE));
        context.assertFalse(player.getInventory().contains(new ItemStack(ModItems.FIRE_DUST)), "Hypothesis should not produce the ordinary recipe result");
        context.assertTrue(cauldron.activate(player), "Second research correct hypothesis should work");
        context.assertFalse(player.getInventory().contains(new ItemStack(ModItems.FIRE_DUST)), "The research attempt itself should only complete research");
        context.assertTrue(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Research leaves water in the cauldron");
        player.getAdvancementTracker().save();
        player.getAdvancementTracker().reload(context.getWorld().getServer().getAdvancementLoader());
        context.assertTrue((EarlyResearch.flags(player) & (EarlyResearch.REACTIVE_COMPLETED | EarlyResearch.FAILED_FEATHER)) == (EarlyResearch.REACTIVE_COMPLETED | EarlyResearch.FAILED_FEATHER), "Second research must survive save and reload");
        cauldron.stacks().set(0, new ItemStack(Items.REDSTONE));
        cauldron.stacks().set(1, new ItemStack(Items.GUNPOWDER));
        context.assertTrue(cauldron.activate(player), "After research, repeating the combination must cook fire dust");
        int result = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ModItems.FIRE_DUST)) result += stack.getCount();
        }
        context.assertTrue(result == 2, "Repeat recipe should produce exactly two fire dust: " + result);
        context.assertTrue(cauldron.stacks().stream().allMatch(ItemStack::isEmpty), "Recipe must consume ingredients");
        context.assertFalse(context.getWorld().getBlockState(pos).get(AlchemyCauldronBlock.FILLED), "Recipe must consume water");
        context.complete();
    }
}
