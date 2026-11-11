package net.alkahest.test;

import net.alkahest.entity.ReagentZoneEntity;
import net.alkahest.entity.ThrownReagentEntity;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModEffects;
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
            context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.FIRE), "Napalm must place real fire on the ground");
            context.assertTrue(zombie.hasStatusEffect(ModEffects.IN_NAPALM), "Napalm must apply its status effect");
            context.assertTrue(zombie.getHealth() <= 17.0f, "Napalm must deal damage: " + zombie.getHealth());
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
        context.setBlockState(2, 3, 2, Blocks.WATER);
        context.setBlockState(2, 6, 2, Blocks.STONE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 2, 3, 2);
        zombie.setAiDisabled(true);
        zombie.setNoGravity(true);
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 3, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(25, () -> {
            context.assertTrue(zombie.isTouchingWater(), "Test target must remain in water");
            context.assertTrue(zombie.getHealth() <= 14.0f, "Water must not stop napalm damage: " + zombie.getHealth());
            context.assertTrue(zombie.hasStatusEffect(ModEffects.IN_NAPALM), "Napalm effect must persist in water");
            context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240)
    public void napalmFlameExpires(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(2, () -> context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.FIRE), "Real napalm fire must appear"));
        context.runAtTick(210, () -> {
            context.assertTrue(context.getWorld().getBlockState(center).isAir(), "Napalm flame must disappear after 10 seconds");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void napalmReignites(TestContext context) {
        context.getWorld().getGameRules().get(GameRules.DO_FIRE_TICK).set(false, context.getWorld().getServer());
        context.setBlockState(2, 1, 2, Blocks.STONE);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(3, () -> context.getWorld().setBlockState(center, Blocks.AIR.getDefaultState()));
        context.runAtTick(12, () -> {
            context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.FIRE), "Extinguished fire must reignite");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240)
    public void napalmRestoresDisplacedWater(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        context.setBlockState(1, 2, 2, Blocks.STONE);
        context.setBlockState(3, 2, 2, Blocks.STONE);
        context.setBlockState(2, 2, 1, Blocks.STONE);
        context.setBlockState(2, 2, 3, Blocks.STONE);
        context.setBlockState(2, 2, 2, Blocks.WATER);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(5, () -> {
            context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.FIRE), "Water cannot put out napalm fire");
            context.getWorld().setBlockState(center, Blocks.WATER.getDefaultState());
        });
        context.runAtTick(15, () -> context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.FIRE), "Fire must return after placing water"));
        context.runAtTick(210, () -> {
            context.assertTrue(context.getWorld().getBlockState(center).isOf(Blocks.WATER), "Displaced water must be restored");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void napalmEffectLingers(TestContext context) {
        context.setBlockState(2, 1, 2, Blocks.STONE);
        context.setBlockState(2, 5, 2, Blocks.STONE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setAiDisabled(true); zombie.setNoGravity(true);
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ReagentZoneEntity.spawn(context.getWorld(), Vec3d.ofCenter(center), ReagentZoneEntity.Kind.NAPALM);
        context.runAtTick(12, () -> {
            BlockPos away = context.getAbsolutePos(new BlockPos(7, 2, 2));
            zombie.setPosition(Vec3d.ofCenter(away));
            zombie.extinguish();
        });
        context.runAtTick(30, () -> {
            context.assertTrue(zombie.hasStatusEffect(ModEffects.IN_NAPALM), "Effect must linger after leaving the zone");
            context.assertTrue(zombie.isOnFire(), "Effect must re-ignite its target");
            context.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=300)
    public void miasmaDamagesArmorAndExpires(TestContext c) {
        var center=c.getAbsolutePos(new BlockPos(2,2,2));
        var near=c.spawnEntity(EntityType.PIG,2,2,2); near.setAiDisabled(true); near.setNoGravity(true);
        var far=c.spawnEntity(EntityType.PIG,7,2,2); far.setAiDisabled(true); far.setNoGravity(true);
        float initialHealth=near.getHealth();
        var helmet=new ItemStack(Items.IRON_HELMET); var boots=new ItemStack(Items.IRON_BOOTS);
        near.equipStack(EquipmentSlot.HEAD,helmet); near.equipStack(EquipmentSlot.FEET,boots);
        ReagentZoneEntity.spawn(c.getWorld(),Vec3d.ofCenter(center),ReagentZoneEntity.Kind.MIASMA);
        var zones=c.getWorld().getEntitiesByClass(ReagentZoneEntity.class,new net.minecraft.util.math.Box(center).expand(1),e->true);
        c.assertTrue(zones.size()==1, "Miasma zone is spawned"); var zone=zones.getFirst();
        c.runAtTick(5,()->{
            var wither=near.getStatusEffect(StatusEffects.WITHER);var poison=near.getStatusEffect(StatusEffects.POISON);
            c.assertTrue(wither!=null && wither.getAmplifier()==1 && poison!=null && poison.getAmplifier()==0, "Inside target gets Wither II and Poison I");
            c.assertTrue(helmet.getDamage()==5 && boots.getDamage()==5, "Each equipped armor piece loses 5 durability per second");
            c.assertFalse(far.hasStatusEffect(StatusEffects.WITHER), "Outside radius remains unaffected");
        });
        c.runAtTick(25,()->{
            c.assertTrue(helmet.getDamage()==10 && boots.getDamage()==10, "Armor is damaged again after one second");
            c.assertTrue(near.getHealth()<initialHealth, "Refreshing potion effects must not suppress their damage ticks");
        });
        c.runAtTick(245,()->{ c.assertTrue(zone.isRemoved(), "Miasma expires after 12 seconds"); c.complete(); });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=80)
    public void lightningBottleMakesRealLightning(TestContext c) {
        c.setBlockState(3,1,3,Blocks.STONE);
        var projectile=c.spawnEntity(ModEntities.THROWN_REAGENT,3.5f,3.5f,3.5f);
        projectile.setItem(new ItemStack(ModItems.LIGHTNING_IN_A_BOTTLE)); projectile.setVelocity(0,-.7,0);
        var pos=c.getAbsolutePos(new BlockPos(3,2,3));
        c.runAtTick(4,()->{
            c.assertTrue(projectile.isRemoved(), "Lightning projectile disappears on impact");
            var bolts=c.getWorld().getEntitiesByClass(net.minecraft.entity.LightningEntity.class,new net.minecraft.util.math.Box(pos).expand(2),e->true);
            c.assertTrue(!bolts.isEmpty(), "Impact creates a real vanilla lightning bolt");
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=80)
    public void miasmaBottleCreatesZoneOnImpact(TestContext c) {
        c.setBlockState(3,1,3,Blocks.STONE);
        var projectile=c.spawnEntity(ModEntities.THROWN_REAGENT,3.5f,3.5f,3.5f);
        projectile.setItem(new ItemStack(ModItems.MIASMA)); projectile.setVelocity(0,-.7,0);
        var pos=c.getAbsolutePos(new BlockPos(3,2,3));
        c.runAtTick(5,()->{
            c.assertTrue(projectile.isRemoved(), "Miasma projectile disappears on impact");
            var zones=c.getWorld().getEntitiesByClass(ReagentZoneEntity.class,new net.minecraft.util.math.Box(pos).expand(2),e->true);
            c.assertTrue(!zones.isEmpty(), "Impact creates a persistent effect zone");
            c.complete();
        });
    }


    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void frostFlaskAffectsNearbyButNotTheThrower(TestContext c) {
        c.setBlockState(2, 1, 2, Blocks.STONE);
        var owner = c.spawnEntity(EntityType.PIG, 3, 2, 2);
        var victim = c.spawnEntity(EntityType.PIG, 2, 2, 3);
        var distant = c.spawnEntity(EntityType.PIG, 7, 2, 2);
        for (var entity : new net.minecraft.entity.passive.PigEntity[]{owner, victim, distant}) {
            entity.setAiDisabled(true); entity.setNoGravity(true); entity.setOnFireFor(30);
        }
        var projectile = c.spawnEntity(ModEntities.THROWN_REAGENT, 2.5f, 3.5f, 2.5f);
        projectile.setOwner(owner); projectile.setItem(new ItemStack(ModItems.FROST_FLASK)); projectile.setVelocity(0, -.7, 0);
        c.runAtTick(10, () -> {
            c.assertTrue(projectile.isRemoved(), "Frost projectile is consumed on impact");
            var slow = victim.getStatusEffect(StatusEffects.SLOWNESS);
            c.assertTrue(slow != null && slow.getAmplifier() == 1 && victim.hasStatusEffect(StatusEffects.WEAKNESS), "Nearby target receives Slowness II and Weakness");
            c.assertFalse(victim.isOnFire(), "Frost extinguishes nearby targets");
            c.assertFalse(owner.hasStatusEffect(StatusEffects.SLOWNESS), "Thrower is excluded from the frost effect");
            c.assertFalse(distant.hasStatusEffect(StatusEffects.SLOWNESS), "The effect has a finite radius");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void purifyingFlaskCleansesNearbyIncludingOwner(TestContext c) {
        c.setBlockState(2, 1, 2, Blocks.STONE);
        var owner = c.spawnEntity(EntityType.PIG, 3, 2, 2);
        var distant = c.spawnEntity(EntityType.PIG, 7, 2, 2);
        for (var entity : new net.minecraft.entity.passive.PigEntity[]{owner, distant}) {
            entity.setAiDisabled(true); entity.setNoGravity(true); entity.setOnFireFor(30);
            entity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.POISON, 200));
            entity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.WITHER, 200));
            entity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.WEAKNESS, 200));
        }
        var projectile = c.spawnEntity(ModEntities.THROWN_REAGENT, 2.5f, 3.5f, 2.5f);
        projectile.setOwner(owner); projectile.setItem(new ItemStack(ModItems.PURIFYING_FLASK)); projectile.setVelocity(0, -.7, 0);
        c.runAtTick(10, () -> {
            c.assertTrue(projectile.isRemoved(), "Purifying projectile is consumed on impact");
            c.assertFalse(owner.isOnFire(), "Cleansing extinguishes the thrower");
            c.assertFalse(owner.hasStatusEffect(StatusEffects.POISON), "Cleansing removes poison");
            c.assertFalse(owner.hasStatusEffect(StatusEffects.WITHER), "Cleansing removes wither");
            c.assertTrue(owner.hasStatusEffect(StatusEffects.WEAKNESS), "Unrelated effects are retained");
            c.assertTrue(distant.hasStatusEffect(StatusEffects.POISON), "Outside the radius effects remain");
            c.complete();
        });
    }
}
