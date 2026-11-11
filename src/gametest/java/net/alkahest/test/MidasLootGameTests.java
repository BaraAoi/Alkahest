package net.alkahest.test;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.List;

/** Exercises actual death, loot and item-entity creation, not only the random helper. */
public final class MidasLootGameTests implements FabricGameTest {
    private static final class LootMob extends ZombieEntity {
        LootMob(World world) { super(EntityType.ZOMBIE, world); }

        @Override protected void dropLoot(DamageSource source, boolean playerKill) {
            // Real vanilla zombie loot table, plus a predictable manually emitted modded drop.
            super.dropLoot(source, playerKill);
            dropStack(new ItemStack(Items.BLAZE_ROD, 2));
            ItemStack crystal = new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO));
            crystal.set(ModDataComponents.ESSENCE_STABILITY, .68f);
            dropStack(crystal);
        }

        @Override protected void dropEquipment(ServerWorld world, DamageSource source, boolean playerKill) {
            super.dropEquipment(world, source, playerKill);
            dropStack(new ItemStack(Items.LAPIS_LAZULI, 3));
        }

        @Override protected void dropInventory() {
            // Simulates a chest-bearing mob: its stored items must never be duplicated.
            dropStack(new ItemStack(Items.EMERALD));
        }
    }

    private List<ItemStack> kill(TestContext c, PlayerEntity killer, boolean playerDamage) {
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 40, 3));
        var mob = new LootMob(c.getWorld());
        mob.setPosition(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        c.assertTrue(c.getWorld().spawnEntity(mob), "The test mob is spawned in the server world");
        DamageSource source = playerDamage ? c.getWorld().getDamageSources().playerAttack(killer)
            : c.getWorld().getDamageSources().generic();
        c.assertTrue(mob.damage(source, 1000), "The mob receives lethal damage");
        Box box = new Box(pos).expand(3);
        List<ItemEntity> entities = c.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> !e.getStack().isEmpty());
        List<ItemStack> stacks = entities.stream().map(e -> e.getStack().copy()).toList();
        for (ItemEntity item : entities) item.discard();
        mob.discard();
        return stacks;
    }

    private int count(List<ItemStack> stacks, net.minecraft.item.Item item) {
        return stacks.stream().filter(s -> s.isOf(item)).mapToInt(ItemStack::getCount).sum();
    }

    private void assertCoherentDrop(TestContext c, List<ItemStack> stacks, int copies) {
        c.assertTrue(count(stacks, Items.BLAZE_ROD) == copies * 2, "The whole manual drop is duplicated exactly once or not at all");
        c.assertTrue(count(stacks, Items.LAPIS_LAZULI) == copies * 3, "Equipment uses the same single roll as loot");
        c.assertTrue(count(stacks, ModItems.CRYSTALS.get(EssenceType.ORDO)) == copies, "The component-bearing drop uses the same roll");
        c.assertTrue(count(stacks, Items.EMERALD) == 1, "A mob's carried inventory is never duplicated");
        for (ItemStack stack : stacks) {
            if (stack.isOf(ModItems.CRYSTALS.get(EssenceType.ORDO)))
                c.assertTrue(Math.abs(stack.get(ModDataComponents.ESSENCE_STABILITY) - .68f) < .001f,
                    "Copied crystals keep their exact stability component");
            if (copies == 2 && (stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.IRON_INGOT)
                    || stack.isOf(Items.CARROT) || stack.isOf(Items.POTATO))) {
                int total = count(stacks, stack.getItem());
                c.assertTrue(total % 2 == 0, "Real vanilla loot is copied, never rerolled");
            }
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noEffectAndNonPlayerKillDoNotDouble(TestContext c) {
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        for (int i = 0; i < 8; i++) assertCoherentDrop(c, kill(c, player, true), 1);
        player.addStatusEffect(new StatusEffectInstance(ModEffects.MIDAS_TOUCH, 3600));
        for (int i = 0; i < 8; i++) assertCoherentDrop(c, kill(c, player, false), 1);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasRollIsOncePerKillForLootAndEquipment(TestContext c) {
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.addStatusEffect(new StatusEffectInstance(ModEffects.MIDAS_TOUCH, 3600));
        int doubled = 0, ordinary = 0;
        for (int i = 0; i < 48; i++) {
            var stacks = kill(c, player, true);
            int rods = count(stacks, Items.BLAZE_ROD);
            c.assertTrue(rods == 2 || rods == 4, "Each kill produces one whole extra copy or none");
            int copies = rods / 2;
            assertCoherentDrop(c, stacks, copies);
            if (copies == 2) doubled++; else ordinary++;
        }
        c.assertTrue(doubled > 0 && ordinary > 0, "The 50% roll produces both outcomes across many independent kills");
        c.complete();
    }
}
