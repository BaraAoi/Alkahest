package net.alkahest.block.entity;

import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.essence.EssenceStack;
import net.alkahest.item.custom.AlkahestVialItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;

/** Runs actual machine ticks with an explicit effect and clock, avoiding fake world players. */
public final class MidasDistilleryGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void crudeSpeedAndNoEffectRegression(TestContext c) {
        c.setBlockState(1, 2, 2, ModBlocks.DISTILLERY);
        c.setBlockState(5, 2, 2, ModBlocks.DISTILLERY);
        BlockPos a = c.getAbsolutePos(new BlockPos(1, 2, 2)), b = c.getAbsolutePos(new BlockPos(5, 2, 2));
        var boosted = (DistilleryBlockEntity)c.getWorld().getBlockEntity(a);
        var normal = (DistilleryBlockEntity)c.getWorld().getBlockEntity(b);
        boosted.setStack(0, new ItemStack(Items.APPLE, 2));
        normal.setStack(0, new ItemStack(Items.APPLE, 2));
        for (int i = 0; i < 999; i++) {
            DistilleryBlockEntity.tickWithMidas(c.getWorld(), a, boosted, true, i);
            DistilleryBlockEntity.tickWithMidas(c.getWorld(), b, normal, false, i);
        }
        c.assertTrue(boosted.getStack(0).getCount() == 2 && normal.getStack(0).getCount() == 2,
            "Neither process completes early");
        DistilleryBlockEntity.tickWithMidas(c.getWorld(), a, boosted, true, 999);
        DistilleryBlockEntity.tickWithMidas(c.getWorld(), b, normal, false, 999);
        c.assertTrue(boosted.getStack(0).getCount() == 1 && normal.getStack(0).getCount() == 2,
            "Midas finishes the 1200-progress raw cycle in 1000 ticks");
        for (int i = 1000; i < 1200; i++) DistilleryBlockEntity.tickWithMidas(c.getWorld(), b, normal, false, i);
        c.assertTrue(normal.getStack(0).getCount() == 1, "Without Midas the original 1200-tick cycle is unchanged");
        c.assertTrue(!boosted.getStack(1).isEmpty() && !normal.getStack(1).isEmpty(), "Both cycles deliver their existing crystal or ash output");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void alkahestModeAddsStabilityAndOnlyOneCatalystCharge(TestContext c) {
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
        var core = (DistilleryBlockEntity)c.getWorld().getBlockEntity(pos);
        core.setStack(0, new ItemStack(Items.APPLE, 2));
        core.setStack(DistilleryBlockEntity.CATALYST, new ItemStack(ModItems.ALKAHEST_VIAL));
        for (int i = 0; i < 82; i++) DistilleryBlockEntity.tickWithMidas(c.getWorld(), pos, core, true, i);
        c.assertTrue(core.getStack(0).getCount() == 2 && AlkahestVialItem.charges(core.getStack(DistilleryBlockEntity.CATALYST)) == 32,
            "No input or Alkahest charge is spent early");
        DistilleryBlockEntity.tickWithMidas(c.getWorld(), pos, core, true, 82);
        c.assertTrue(core.getStack(0).getCount() == 1 && AlkahestVialItem.charges(core.getStack(DistilleryBlockEntity.CATALYST)) == 31,
            "One accelerated batch consumes exactly one input and one Alkahest charge");
        int crystalCount = 0;
        for (int i = DistilleryBlockEntity.OUTPUT_START; i < DistilleryBlockEntity.OUTPUT_END; i++) {
            ItemStack item = core.getStack(i);
            if (item.isEmpty()) continue;
            Float stability = item.get(ModDataComponents.ESSENCE_STABILITY);
            c.assertTrue(stability != null && stability >= .65f - 1e-5 && stability <= .80f + 1e-5,
                "Alkahest-mode crystals receive flat +0.20 stability");
            crystalCount += item.getCount();
        }
        c.assertTrue(crystalCount > 0, "The existing crystal output remains available");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tierTwoSpeedStabilityAndNoBellowsRegression(TestContext c) {
        for (int x : new int[]{1, 5}) {
            c.setBlockState(x, 2, 2, ModBlocks.DISTILLERY_TIER2);
            for (int y = 3; y <= 5; y++) c.setBlockState(x, y, 2, ModBlocks.DISTILLERY_BELLOWS);
        }
        BlockPos a = c.getAbsolutePos(new BlockPos(1, 2, 2)), b = c.getAbsolutePos(new BlockPos(5, 2, 2));
        var boosted = (Tier2DistilleryBlockEntity)c.getWorld().getBlockEntity(a);
        var normal = (Tier2DistilleryBlockEntity)c.getWorld().getBlockEntity(b);
        boosted.setStack(0, new ItemStack(Items.APPLE, 2));
        normal.setStack(0, new ItemStack(Items.APPLE, 2));
        for (int i = 0; i < 249; i++) {
            Tier2DistilleryBlockEntity.tickWithMidas(c.getWorld(), a, boosted, true, i);
            Tier2DistilleryBlockEntity.tickWithMidas(c.getWorld(), b, normal, false, i);
        }
        c.assertTrue(boosted.getStack(0).getCount() == 2 && normal.getStack(0).getCount() == 2,
            "Neither tier-two cycle completes early");
        Tier2DistilleryBlockEntity.tickWithMidas(c.getWorld(), a, boosted, true, 249);
        Tier2DistilleryBlockEntity.tickWithMidas(c.getWorld(), b, normal, false, 249);
        c.assertTrue(boosted.getStack(0).getCount() == 1 && normal.getStack(0).getCount() == 2,
            "Midas finishes the 300-progress cycle in 250 ticks");
        int total = 0;
        for (var bellows : boosted.bellows()) if (!bellows.empty()) {
            c.assertTrue(bellows.stability() >= .75f - 1e-5 && bellows.stability() <= .90f + 1e-5,
                "Boosted tier-two liquid has +0.20 stability");
            total += bellows.amount();
        }
        c.assertTrue(total > 0, "Tier-two output is delivered normally to the glass bellows");
        for (int i = 250; i < 300; i++) Tier2DistilleryBlockEntity.tickWithMidas(c.getWorld(), b, normal, false, i);
        c.assertTrue(normal.getStack(0).getCount() == 1, "The unboosted cycle still takes 300 ticks");
        c.setBlockState(3, 2, 5, ModBlocks.DISTILLERY_TIER2);
        BlockPos lone = c.getAbsolutePos(new BlockPos(3, 2, 5));
        var missing = (Tier2DistilleryBlockEntity)c.getWorld().getBlockEntity(lone);
        missing.setStack(0, new ItemStack(Items.APPLE));
        for (int i = 0; i < 400; i++) Tier2DistilleryBlockEntity.tickWithMidas(c.getWorld(), lone, missing, true, i);
        c.assertTrue(missing.progress() == 0 && missing.getStack(0).getCount() == 1, "Midas cannot bypass missing bellows");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stabilityBonusIsFlatAndCapped(TestContext c) {
        List<EssenceStack> normal = Tier2DistilleryBlockEntity.outputs(new ItemStack(Items.APPLE), Random.create(42), false);
        List<EssenceStack> boosted = Tier2DistilleryBlockEntity.outputs(new ItemStack(Items.APPLE), Random.create(42), true);
        c.assertTrue(!normal.isEmpty() && normal.size() == boosted.size(), "Midas preserves the number of outputs");
        for (int i = 0; i < normal.size(); i++) {
            EssenceStack a = normal.get(i), b = boosted.get(i);
            c.assertTrue(a.type() == b.type() && a.amount() == b.amount(), "Midas does not multiply essence");
            c.assertTrue(Math.abs((a.stability() + .20f) - b.stability()) < 1e-5, "Each output gets precisely +0.20 stability");
        }
        c.assertTrue(MidasTouchEffect.outputStability(.95f, true) == 1.0f && MidasTouchEffect.outputStability(.95f, false) == .95f,
            "Boosted stability is capped at 100% and the unboosted value is unchanged");
        c.complete();
    }
}
