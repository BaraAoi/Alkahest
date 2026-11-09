package net.alkahest.test;

import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.registry.ModBlocks;
import net.alkahest.research.EarlyResearch;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
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
        context.complete();
    }
}
