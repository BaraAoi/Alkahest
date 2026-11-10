package net.alkahest.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Corrosive mutant born from failed Alkahest synthesis or haunting the Alchemical Wastes at night.
 * Spec §7.2: 25 HP, 4 melee damage, leaves an acid puddle every 3 seconds (60 ticks),
 * drops Caustic Slime and an unstable Prima crystal (0.10 stability).
 */
public class DissolvedEntity extends HostileEntity {
    private static final DustParticleEffect ACID_DRIP = new DustParticleEffect(new Vector3f(0.52f, 0.92f, 0.28f), 0.9f);
    private static final DustParticleEffect VIOLET_DRIP = new DustParticleEffect(new Vector3f(0.58f, 0.24f, 0.88f), 0.85f);
    private static final List<EssenceType> PRIMA_TYPES = List.of(
        EssenceType.IGNIS, EssenceType.AQUA, EssenceType.TERRA,
        EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO
    );

    public DissolvedEntity(EntityType<? extends DissolvedEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createDissolvedAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 25.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
            .add(EntityAttributes.GENERIC_ARMOR, 2.0);
    }

    public static boolean canSpawn(
        EntityType<DissolvedEntity> type,
        ServerWorldAccess world,
        SpawnReason reason,
        BlockPos pos,
        Random random
    ) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return HostileEntity.canSpawnInDark(type, world, reason, pos, random);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.85));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        if (effect.equals(StatusEffects.POISON) || effect.getEffectType() == StatusEffects.POISON
            || effect.getEffectType() == StatusEffects.WITHER) {
            return false;
        }
        return super.canHaveStatusEffect(effect);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (random.nextInt(3) == 0) {
                DustParticleEffect particle = random.nextBoolean() ? ACID_DRIP : VIOLET_DRIP;
                getWorld().addParticle(
                    particle,
                    getX() + (random.nextDouble() - 0.5) * 0.7,
                    getY() + 0.25 + random.nextDouble() * 1.15,
                    getZ() + (random.nextDouble() - 0.5) * 0.7,
                    0.0, -0.02, 0.0
                );
            }
            return;
        }

        if (isAlive() && age > 0 && age % 60 == 0 && getWorld() instanceof ServerWorld serverWorld) {
            spawnAcidPuddle(serverWorld);
        }
    }

    public void spawnAcidPuddle(ServerWorld serverWorld) {
        Box near = new Box(getPos(), getPos()).expand(1.1);
        boolean alreadyHere = !serverWorld.getEntitiesByClass(
            ReagentZoneEntity.class, near, z -> z.isAlive() && z.kind() == ReagentZoneEntity.Kind.ACID_PUDDLE
        ).isEmpty();
        if (!alreadyHere) {
            ReagentZoneEntity.spawn(serverWorld, getPos(), ReagentZoneEntity.Kind.ACID_PUDDLE);
            serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_PLACE,
                SoundCategory.HOSTILE, 0.55f, 0.75f);
        }
    }

    @Override
    protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
        super.dropLoot(damageSource, causedByPlayer);
        if (!(getWorld() instanceof ServerWorld serverWorld)) return;

        dropStack(new ItemStack(ModItems.CAUSTIC_SLIME, 1 + random.nextInt(2)));

        EssenceType prima = PRIMA_TYPES.get(random.nextInt(PRIMA_TYPES.size()));
        Item crystal = ModItems.CRYSTALS.get(prima);
        if (crystal != null) {
            ItemStack unstable = new ItemStack(crystal, 1);
            unstable.set(ModDataComponents.ESSENCE_STABILITY, 0.10f);
            dropStack(unstable);
        }

        serverWorld.spawnParticles(ParticleTypes.ITEM_SLIME,
            getX(), getBodyY(0.5), getZ(), 20, 0.35, 0.4, 0.35, 0.05);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_SLIME_SQUISH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_HUSK_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SLIME_DEATH;
    }
}
