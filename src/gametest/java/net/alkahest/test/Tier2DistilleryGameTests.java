package net.alkahest.test;

import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.List;

public class Tier2DistilleryGameTests implements FabricGameTest {
    private BlockPos corePos(TestContext c) { return c.getAbsolutePos(new BlockPos(3, 2, 3)); }

    private Tier2DistilleryBlockEntity core(TestContext c) {
        return (Tier2DistilleryBlockEntity) c.getWorld().getBlockEntity(corePos(c));
    }

    private void build(TestContext c) {
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY_TIER2);
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3, 4, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3, 5, 3, ModBlocks.DISTILLERY_BELLOWS);
    }

    private void distil(TestContext c, int ticks) {
        for (int i = 0; i < ticks; i++) Tier2DistilleryBlockEntity.tick(c.getWorld(), corePos(c), core(c));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void distilsEveryEssenceTypeIntoBellows(TestContext c) {
        build(c);
        Tier2DistilleryBlockEntity core = core(c);
        c.assertTrue(core.bellows().size() == 3, "Three bellows must be detected above the core");
        core.setStack(0, new ItemStack(Items.APPLE, 2));
        distil(c, Tier2DistilleryBlockEntity.DURATION);

        List<EssenceStack> expected = ItemEssenceMap.get(new ItemStack(Items.APPLE));
        int stored = 0;
        for (DistilleryBellowsBlockEntity bellows : core.bellows()) stored += bellows.amount();
        c.assertTrue(stored > 0, "Essence must accumulate in the bellows");
        for (DistilleryBellowsBlockEntity bellows : core.bellows()) {
            if (bellows.empty()) continue;
            EssenceType type = bellows.storedType();
            c.assertTrue(expected.stream().anyMatch(e -> e.type() == type), "Only item essences may be distilled, got " + type);
            c.assertTrue(bellows.stability() >= 0.55f - 1e-4 && bellows.stability() <= 0.70f + 1e-4,
                "Stable-tier stability expected, got " + bellows.stability());
        }
        c.assertTrue(expected.stream().anyMatch(e -> e.type() == EssenceType.HERBA), "Apple must contain Herba");
        c.assertTrue(core.bellows().stream().anyMatch(b -> b.storedType() == EssenceType.HERBA),
            "The tier-2 cube must distil composite Herba instead of only primaries");
        c.assertTrue(core.getStack(0).getCount() == 1, "Exactly one item must be consumed per cycle");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void jarTakesEssenceFromBellows(TestContext c) {
        build(c);
        Tier2DistilleryBlockEntity core = core(c);
        core.setStack(0, new ItemStack(Items.APPLE));
        distil(c, Tier2DistilleryBlockEntity.DURATION);

        DistilleryBellowsBlockEntity bellows = core.bellows().get(0);
        c.assertFalse(bellows.empty(), "The first bellow must be filled");
        int amount = bellows.amount();
        EssenceType type = bellows.storedType();
        float stability = bellows.stability();

        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.JAR));
        bellows.pourToJar(player, Hand.MAIN_HAND);

        JarContentsComponent jar = JarItem.contents(player.getStackInHand(Hand.MAIN_HAND));
        c.assertTrue(jar.total() == Math.min(amount, JarItem.CAPACITY), "The jar must take the stored essence");
        c.assertTrue(jar.contents().getFirst().type() == type, "The essence type must be preserved");
        c.assertTrue(Math.abs(jar.contents().getFirst().stability() - stability) < 1e-4f, "Stability must be preserved");
        c.assertTrue(bellows.amount() == Math.max(0, amount - JarItem.CAPACITY), "Leftover essence must stay in the bellows");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void withoutBellowsNothingHappens(TestContext c) {
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY_TIER2);
        Tier2DistilleryBlockEntity core = core(c);
        core.setStack(0, new ItemStack(Items.APPLE));
        distil(c, Tier2DistilleryBlockEntity.DURATION * 2);
        c.assertTrue(core.progress() == 0, "Without bellows the cube must not progress");
        c.assertTrue(core.pending().isEmpty(), "Nothing may be produced without bellows");
        c.assertTrue(core.getStack(0).getCount() == 1, "The input must not be consumed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bellowsHoldOneTypeOnly(TestContext c) {
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_BELLOWS);
        DistilleryBellowsBlockEntity bellows = (DistilleryBellowsBlockEntity)
            c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, 3, 3)));
        c.assertTrue(bellows.accept(EssenceType.IGNIS, 10, 0.6f) == 10, "The first type must be accepted");
        c.assertTrue(bellows.accept(EssenceType.AQUA, 10, 0.6f) == 0, "A second type must be rejected");
        c.assertTrue(bellows.amount() == 10 && bellows.storedType() == EssenceType.IGNIS, "Contents must stay untouched");
        c.assertTrue(bellows.accept(EssenceType.IGNIS, 100, 0.6f) == DistilleryBellowsBlockEntity.CAPACITY - 10,
            "Only the free capacity may be filled");
        c.complete();
    }
}
