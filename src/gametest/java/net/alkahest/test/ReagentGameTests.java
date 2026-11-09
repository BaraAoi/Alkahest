package net.alkahest.test;

import net.alkahest.entity.ReagentZoneEntity;
import net.alkahest.entity.ThrownReagentEntity;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ClickType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

public class ReagentGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryMixing(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.getInventory().setStack(0, new ItemStack(Items.SLIME_BALL, 2));
        Slot slot = new Slot(player.getInventory(), 0, 0, 0);
        ItemStack cursor = new ItemStack(ModItems.FIRE_DUST, 3);
        context.assertFalse(cursor.getItem().onStackClicked(cursor, slot, ClickType.LEFT, player), "Left click must not mix");
        context.assertTrue(cursor.getItem().onStackClicked(cursor, slot, ClickType.RIGHT, player), "Fire dust + slime must mix");
        context.assertTrue(cursor.getCount() == 2 && slot.getStack().getCount() == 1, "Exactly one of each must be consumed");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.NAPALM)), "Napalm must be delivered");

        player.getInventory().setStack(0, new ItemStack(ModItems.CAUSTIC_SLIME, 2));
        ItemStack gunpowder = new ItemStack(Items.GUNPOWDER, 3);
        ItemStack reagentInSlot = slot.getStack();
        context.assertTrue(reagentInSlot.getItem().onClicked(reagentInSlot, gunpowder, slot, ClickType.RIGHT,
            player, StackReference.EMPTY), "Gunpowder on caustic slime must mix");
        context.assertTrue(reagentInSlot.getCount() == 1 && gunpowder.getCount() == 2, "Exactly one of each must be consumed");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.ACID_BOMB)), "Acid bomb must be delivered");
        SimpleInventory other = new SimpleInventory(1);
        other.setStack(0, new ItemStack(Items.SLIME_BALL));
        ItemStack dust = new ItemStack(ModItems.FIRE_DUST);
        context.assertFalse(dust.getItem().onStackClicked(dust, new Slot(other, 0, 0, 0), ClickType.RIGHT, player),
            "Container slots must not be modified");
        context.assertTrue(dust.getCount() == 1 && other.getStack(0).getCount() == 1, "Rejected inputs must remain intact");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void acidProjectile(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        context.setBlockState(4, 1, 2, Blocks.STONE);
        context.setBlockState(4, 5, 2, Blocks.STONE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 4, 2, 2);
        zombie.setAiDisabled(true);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        zombie.equipStack(EquipmentSlot.HEAD, helmet);
        ThrownReagentEntity projectile = context.spawnEntity(ModEntities.THROWN_REAGENT, 2.5f, 3.5f, 2.5f);
        projectile.setItem(new ItemStack(ModItems.ACID_BOMB));
        projectile.setVelocity(0, -0.7, 0);
        context.runAtTick(10, () -> {
            context.assertTrue(projectile.isRemoved(), "Projectile must disappear after impact");
            context.assertTrue(Math.abs(zombie.getHealth() - 14.0f) < 0.01f, "Acid must deal 6 magic damage, health=" + zombie.getHealth());
            context.assertTrue(helmet.getDamage() == 35, "Acid must damage armor by 35, actual=" + helmet.getDamage());
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void slimeZone(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setAiDisabled(true);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.SLIME);
        context.runAtTick(5, () -> {
            var effect = zombie.getStatusEffect(StatusEffects.SLOWNESS);
            context.assertTrue(effect != null && effect.getAmplifier() == 1, "Slime must apply Slowness II");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void napalmZone(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        context.setBlockState(2, 5, 2, Blocks.STONE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setAiDisabled(true);
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(25, () -> {
            context.assertTrue(zombie.isOnFire(), "Targets should visibly burn inside the napalm zone");
            context.assertTrue(context.getWorld().getBlockState(center).isOf(ModBlocks.NAPALM_FLAME), "Napalm flames must be visible on solid ground");
            context.assertTrue(Math.abs(zombie.getHealth() - 14.0f) < 0.01f, "Napalm must deal 3 damage per second: " + zombie.getHealth());
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
    public void fireZoneExpires(TestContext context) {
        context.getWorld().getGameRules().get(GameRules.DO_FIRE_TICK).set(false, context.getWorld().getServer());
        context.setBlockState(2, 1, 2, Blocks.STONE);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.FIRE);
        context.runAtTick(2, () -> context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.FIRE), "Fire must be placed"));
        context.runAtTick(110, () -> {
            context.assertTrue(context.getWorld().getBlockState(center).isAir(), "Temporary fire must disappear");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void napalmKeepsDamagingInWater(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        context.setBlockState(2, 2, 2, Blocks.WATER);
        context.setBlockState(2, 5, 2, Blocks.STONE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setAiDisabled(true);
        zombie.setNoGravity(true);
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(25, () -> {
            context.assertTrue(Math.abs(zombie.getHealth() - 14.0f) < 0.01f, "Water must not stop napalm damage: " + zombie.getHealth());
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240)
    public void napalmFlameExpires(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(2, () -> context.assertTrue(context.getWorld().getBlockState(center).isOf(ModBlocks.NAPALM_FLAME), "Napalm flame must appear"));
        context.runAtTick(210, () -> {
            context.assertTrue(context.getWorld().getBlockState(center).isAir(), "Napalm flame must disappear after 10 seconds");
            context.complete();
        });
    }

}
