package net.alkahest.test;

import net.alkahest.Alkahest;
import net.alkahest.entity.ElementalBoltEntity;
import net.alkahest.entity.ElementalEntity;
import net.alkahest.entity.ThrownReagentEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ClickType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.List;

public class ElementalGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bindingSaltInventoryAndCrafting(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.getInventory().setStack(0, new ItemStack(ModItems.CRYSTALS.get(EssenceType.AQUA), 2));
        Slot slot = new Slot(player.getInventory(), 0, 0, 0);
        ItemStack cursorSalt = new ItemStack(ModItems.BINDING_SALT, 2);

        context.assertFalse(cursorSalt.getItem().onStackClicked(cursorSalt, slot, ClickType.LEFT, player),
            "Left click must not charge binding salt");
        context.assertTrue(cursorSalt.getItem().onStackClicked(cursorSalt, slot, ClickType.RIGHT, player),
            "Right-clicking binding salt onto a Prima crystal must charge it");
        context.assertTrue(cursorSalt.getCount() == 1 && slot.getStack().getCount() == 1,
            "Charging must consume one salt and one crystal");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.CHARGED_BINDING_SALTS.get(EssenceType.AQUA))),
            "Aqua binding salt must be added to inventory");

        player.getInventory().setStack(0, new ItemStack(ModItems.BINDING_SALT, 2));
        ItemStack cursorIgnis = new ItemStack(ModItems.CRYSTALS.get(EssenceType.IGNIS), 2);
        ItemStack slotSalt = slot.getStack();
        context.assertTrue(slotSalt.getItem().onClicked(slotSalt, cursorIgnis, slot, ClickType.RIGHT, player, StackReference.EMPTY),
            "Right-clicking a Prima crystal onto binding salt in a slot must charge it");
        context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.CHARGED_BINDING_SALTS.get(EssenceType.IGNIS))),
            "Ignis binding salt must be added to inventory");

        player.getInventory().setStack(0, new ItemStack(ModItems.CRYSTALS.get(EssenceType.HERBA), 1));
        ItemStack base = new ItemStack(ModItems.BINDING_SALT, 1);
        context.assertFalse(base.getItem().onStackClicked(base, slot, ClickType.RIGHT, player),
            "Composite crystals must not charge binding salt");

        var recipeManager = context.getWorld().getRecipeManager();
        for (EssenceType prima : List.of(EssenceType.IGNIS, EssenceType.AQUA, EssenceType.TERRA,
                                         EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO)) {
            var id = Alkahest.id("binding_salt_" + prima.asString());
            context.assertTrue(recipeManager.get(id).isPresent(), "Missing shapeless recipe for " + id);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void thrownSaltStripsLevitationAndAppliesCrystallizedSlow(TestContext context) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE);
            }
        }
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setAiDisabled(true);
        double baseSpeed = zombie.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);

        // Spawn hovering Terra Elemental 3 blocks up in the air (with live AI!)
        ElementalEntity elemental = context.spawnEntity(ModEntities.ELEMENTALS.get(EssenceType.TERRA), 3.5f, 5.0f, 3.5f);
        context.assertTrue(elemental.hasNoGravity() && !elemental.isGroundedBySalt(),
            "Elemental must hover with levitation before salt impact");
        double startAirY = elemental.getY();
        double baseFlySpeed = elemental.getAttributeValue(EntityAttributes.GENERIC_FLYING_SPEED);

        // Fire Aer binding salt horizontally straight into the hovering Elemental's torso on tick 1
        ThrownReagentEntity projectile = context.spawnEntity(ModEntities.THROWN_REAGENT, 2.3f, 5.7f, 3.5f);
        projectile.setItem(new ItemStack(ModItems.CHARGED_BINDING_SALTS.get(EssenceType.AER)));
        projectile.setVelocity(1.2, 0.0, 0.0);

        // Also hit the ground zombie on a separate tile with base binding salt
        context.setBlockState(7, 1, 2, Blocks.STONE);
        zombie.refreshPositionAndAngles(
            context.getAbsolutePos(new BlockPos(7, 2, 2)).getX() + 0.5,
            context.getAbsolutePos(new BlockPos(7, 2, 2)).getY(),
            context.getAbsolutePos(new BlockPos(7, 2, 2)).getZ() + 0.5,
            0.0f, 0.0f
        );
        ThrownReagentEntity zombieSalt = context.spawnEntity(ModEntities.THROWN_REAGENT, 7.5f, 3.6f, 2.5f);
        zombieSalt.setItem(new ItemStack(ModItems.BINDING_SALT));
        zombieSalt.setVelocity(0, -0.7, 0);

        context.runAtTick(25, () -> {
            context.assertTrue(projectile.isRemoved(), "Salt projectile must discard on impact");
            context.assertTrue(elemental.isGroundedBySalt(), "Binding salt must strip Elemental levitation");
            context.assertFalse(elemental.hasNoGravity(), "Grounded Elemental must be subject to gravity");
            context.assertTrue(elemental.getY() < startAirY - 2.0 && elemental.isOnGround(),
                "Elemental stripped of levitation must fall onto the stone floor, y=" + elemental.getY());
            context.assertTrue(elemental.isProperlyBound(),
                "Terra elemental hit by Aer binding salt must become properly bound");

            double slowedFlySpeed = elemental.getAttributeValue(EntityAttributes.GENERIC_FLYING_SPEED);
            context.assertTrue(Math.abs(slowedFlySpeed - baseFlySpeed * 0.30) < 0.005,
                "Flying speed must be reduced by 70%, got " + slowedFlySpeed);

            var zombieEffect = zombie.getStatusEffect(ModEffects.CRYSTALLIZED);
            context.assertTrue(zombieEffect != null && zombieEffect.getDuration() >= 260,
                "Zombie must receive 15s Crystallized effect");
            double slowedSpeed = zombie.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            context.assertTrue(Math.abs(slowedSpeed - baseSpeed * 0.30) < 0.005,
                "Movement speed must be reduced by 70%, got " + slowedSpeed);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void elementalRangedAttacksInflictElementalHazards(TestContext context) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 7; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE);
            }
        }

        // 1. Hostile Ignis Elemental fires ranged bolt via AI combat goal and ignites target
        CowEntity fireTarget = context.spawnEntity(EntityType.COW, 7.5f, 2.0f, 2.5f);
        fireTarget.setAiDisabled(true);
        ElementalEntity ignis = context.spawnEntity(ModEntities.ELEMENTALS.get(EssenceType.IGNIS), 2.5f, 2.5f, 2.5f);
        ignis.setTarget(fireTarget);

        // 2. Direct Perditio bolt inflicts Wither and corrodes armor (Husk never burns helmet in daylight)
        ZombieEntity armorTarget = context.spawnEntity(EntityType.HUSK, 7.5f, 2.0f, 5.5f);
        armorTarget.setAiDisabled(true);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        armorTarget.equipStack(EquipmentSlot.HEAD, helmet);
        ElementalEntity perditio = context.spawnEntity(ModEntities.ELEMENTALS.get(EssenceType.PERDITIO), 2.5f, 2.5f, 5.5f);
        perditio.setAiDisabled(true);
        ElementalBoltEntity perditioBolt = perditio.shootElementalBolt(armorTarget);
        context.assertTrue(perditioBolt != null, "Server Elemental must spawn ElementalBoltEntity");

        context.runAtTick(75, () -> {
            context.assertTrue(fireTarget.getHealth() < fireTarget.getMaxHealth(),
                "Hostile Ignis Elemental ranged attack must damage target, health=" + fireTarget.getHealth());
            context.assertTrue(fireTarget.getFireTicks() > 0,
                "Ignis bolt must ignite target, fireTicks=" + fireTarget.getFireTicks());

            context.assertTrue(armorTarget.hasStatusEffect(StatusEffects.WITHER),
                "Perditio bolt must inflict Wither");
            context.assertTrue(helmet.getDamage() == 4,
                "Perditio bolt must corrode equipped armor by 4 durability, got " + helmet.getDamage());
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unboundAndWrongSaltElementalsEvaporate(TestContext context) {
        BlockPos origin = context.getAbsolutePos(new BlockPos(3, 2, 3));
        ElementalEntity ignis = context.spawnEntity(ModEntities.ELEMENTALS.get(EssenceType.IGNIS), 3, 2, 3);
        context.assertTrue(Math.abs(ignis.getMaxHealth() - 30.0f) < 0.01f, "Elemental max HP must be 30");

        // Same-element salt grounds & slows, but must NOT anchor against evaporation
        context.assertFalse(ignis.applyBindingSalt(EssenceType.IGNIS, 300, null),
            "Same-element salt must not bind an Elemental");
        context.assertTrue(ignis.isGroundedBySalt() && !ignis.hasNoGravity(),
            "Wrong salt still strips levitation");
        context.assertFalse(ignis.isProperlyBound(), "Wrong salt must leave Elemental unbound");

        ignis.damage(context.getWorld().getDamageSources().generic(), 100.0f);
        context.assertTrue(ignis.isDead(), "Elemental should die from lethal damage");

        List<ItemEntity> drops = context.getWorld().getEntitiesByClass(
            ItemEntity.class, new Box(origin).expand(5.0), e -> true);
        context.assertTrue(drops.isEmpty(), "Unbound Elemental must evaporate without dropping crystals, got " + drops);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void antipodeBoundElementalsDropHighStabilityCrystals(TestContext context) {
        BlockPos posA = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ElementalEntity ignis = context.spawnEntity(ModEntities.ELEMENTALS.get(EssenceType.IGNIS), 2, 2, 2);
        context.assertTrue(ignis.applyBindingSalt(EssenceType.AQUA, 300, null),
            "Aqua binding salt must anchor Ignis Elemental");
        ignis.damage(context.getWorld().getDamageSources().generic(), 100.0f);

        Item ignisCrystal = ModItems.CRYSTALS.get(EssenceType.IGNIS);
        List<ItemEntity> ignisDrops = context.getWorld().getEntitiesByClass(
            ItemEntity.class, new Box(posA).expand(4.0), e -> e.getStack().isOf(ignisCrystal));
        int ignisCount = ignisDrops.stream().mapToInt(e -> e.getStack().getCount()).sum();
        context.assertTrue(ignisCount >= 2 && ignisCount <= 4,
            "Bound Ignis Elemental must drop 2..4 Ignis crystals, got " + ignisCount);
        Float ignisStability = ignisDrops.get(0).getStack().get(ModDataComponents.ESSENCE_STABILITY);
        context.assertTrue(ignisStability != null && ignisStability >= 0.649f && ignisStability <= 0.751f,
            "Dropped Ignis crystal stability must be in [0.65, 0.75], got " + ignisStability);

        BlockPos posB = context.getAbsolutePos(new BlockPos(8, 2, 8));
        ElementalEntity ordo = context.spawnEntity(ModEntities.ELEMENTALS.get(EssenceType.ORDO), 8, 2, 8);
        context.assertTrue(ordo.applyBindingSalt(EssenceType.PERDITIO, 300, null),
            "Perditio binding salt must anchor Ordo Elemental");
        ordo.damage(context.getWorld().getDamageSources().generic(), 100.0f);

        Item ordoCrystal = ModItems.CRYSTALS.get(EssenceType.ORDO);
        List<ItemEntity> ordoDrops = context.getWorld().getEntitiesByClass(
            ItemEntity.class, new Box(posB).expand(4.0), e -> e.getStack().isOf(ordoCrystal));
        int ordoCount = ordoDrops.stream().mapToInt(e -> e.getStack().getCount()).sum();
        context.assertTrue(ordoCount >= 1 && ordoCount <= 2,
            "Bound Ordo Elemental must drop 1..2 Ordo crystals, got " + ordoCount);
        Float ordoStability = ordoDrops.get(0).getStack().get(ModDataComponents.ESSENCE_STABILITY);
        context.assertTrue(ordoStability != null && ordoStability >= 0.649f && ordoStability <= 0.751f,
            "Dropped Ordo crystal stability must be in [0.65, 0.75], got " + ordoStability);

        context.complete();
    }
}
