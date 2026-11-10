package net.alkahest.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Hovering manifestation of a Prima essence (Ignis, Aqua, Terra, Aer, Ordo, Perditio).
 * Neutral by default; evaporates on death unless bound by opposite-element Binding Salt.
 */
public class ElementalEntity extends PathAwareEntity {
    private static final TrackedData<Integer> BOUND_TICKS = DataTracker.registerData(
        ElementalEntity.class, TrackedDataHandlerRegistry.INTEGER
    );
    private final EssenceType essenceType;

    public ElementalEntity(EntityType<? extends ElementalEntity> entityType, World world, EssenceType essenceType) {
        super(entityType, world);
        this.essenceType = essenceType;
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createElementalAttributes() {
        return PathAwareEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
            .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    public static boolean canSpawn(EntityType<ElementalEntity> type, ServerWorldAccess world,
                                   SpawnReason reason, BlockPos pos, Random random) {
        if (reason == SpawnReason.NATURAL && type == ModEntities.ELEMENTALS.get(EssenceType.AER) && pos.getY() < 95) {
            return false;
        }
        boolean allowFluid = type == ModEntities.ELEMENTALS.get(EssenceType.AQUA);
        if (!allowFluid && !world.getFluidState(pos).isEmpty()) {
            return false;
        }
        return world.isSpaceEmpty(type.getSpawnBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    public EssenceType essenceType() {
        return essenceType;
    }

    public int getBoundTicks() {
        return dataTracker.get(BOUND_TICKS);
    }

    public void setBoundTicks(int ticks) {
        dataTracker.set(BOUND_TICKS, Math.max(0, ticks));
    }

    public boolean isProperlyBound() {
        return getBoundTicks() > 0 && hasStatusEffect(ModEffects.CRYSTALLIZED);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(BOUND_TICKS, 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(2, new FlyGoal(this, 1.0));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation navigation = new BirdNavigation(this, world);
        navigation.setCanPathThroughDoors(false);
        navigation.setCanSwim(true);
        return navigation;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (essenceType == EssenceType.AQUA && getAir() < getMaxAir()) {
            setAir(getMaxAir());
        }
        if (getWorld().isClient) {
            if (age % 2 == 0) {
                DustParticleEffect dust = dustOf(essenceType.color(), 1.15f);
                getWorld().addParticle(
                    dust,
                    getX() + (random.nextDouble() - 0.5) * 0.75,
                    getY() + 0.35 + random.nextDouble() * 0.85,
                    getZ() + (random.nextDouble() - 0.5) * 0.75,
                    0.0, 0.02, 0.0
                );
                if (getBoundTicks() > 0 && essenceType.opposite() != null) {
                    DustParticleEffect seal = dustOf(essenceType.opposite().color(), 0.95f);
                    getWorld().addParticle(
                        seal,
                        getX() + (random.nextDouble() - 0.5) * 0.95,
                        getY() + 0.3 + random.nextDouble() * 0.9,
                        getZ() + (random.nextDouble() - 0.5) * 0.95,
                        0.0, 0.01, 0.0
                    );
                }
            }
            return;
        }

        int bound = getBoundTicks();
        if (bound > 0) {
            if (!hasStatusEffect(ModEffects.CRYSTALLIZED)) {
                setBoundTicks(0);
            } else {
                setBoundTicks(bound - 1);
            }
        }

        if (age % 20 == 0 && getWorld() instanceof ServerWorld serverWorld) {
            for (ServerPlayerEntity player : serverWorld.getEntitiesByClass(
                    ServerPlayerEntity.class, getBoundingBox().expand(14.0), p -> !p.isSpectator())) {
                EarlyResearch.discoverBinding(player, serverWorld, getBlockPos());
            }
        }
    }

    /**
     * Applies the Crystallized slow and checks whether the thrown salt carries the antipode Prima essence.
     * Returns true when the Elemental becomes anchored against evaporation on death.
     */
    public boolean applyBindingSalt(EssenceType saltType, int durationTicks, Entity source) {
        addStatusEffect(new StatusEffectInstance(ModEffects.CRYSTALLIZED, durationTicks, 0), source);
        EssenceType required = essenceType.opposite();
        if (saltType != null && saltType == required) {
            setBoundTicks(durationTicks);
            if (getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(dustOf(required.color(), 1.35f),
                    getX(), getBodyY(0.5), getZ(), 28, 0.55, 0.55, 0.55, 0.04);
                serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.NEUTRAL, 1.0f, 0.85f);
            }
            if (source instanceof ServerPlayerEntity player) {
                player.sendMessage(Text.translatable("message.alkahest.elemental_bound", essenceType.displayName()), true);
            }
            return true;
        }
        if (source instanceof ServerPlayerEntity player && required != null) {
            player.sendMessage(Text.translatable("message.alkahest.elemental_wrong_salt",
                essenceType.displayName(), required.displayName()), true);
        }
        return false;
    }

    @Override
    protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
        super.dropLoot(damageSource, causedByPlayer);
        if (!(getWorld() instanceof ServerWorld serverWorld)) return;

        if (isProperlyBound()) {
            Item crystal = ModItems.CRYSTALS.get(essenceType);
            if (crystal != null) {
                int count = (essenceType == EssenceType.ORDO || essenceType == EssenceType.PERDITIO)
                    ? (1 + random.nextInt(2))
                    : (2 + random.nextInt(3));
                float stability = (65 + random.nextInt(11)) / 100.0f;
                ItemStack drop = new ItemStack(crystal, count);
                drop.set(ModDataComponents.ESSENCE_STABILITY, stability);
                dropStack(drop);
            }
            serverWorld.spawnParticles(dustOf(essenceType.color(), 1.3f),
                getX(), getBodyY(0.5), getZ(), 26, 0.45, 0.45, 0.45, 0.05);
            serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
                SoundCategory.NEUTRAL, 1.0f, 1.05f);
            return;
        }

        DustParticleEffect plume = dustOf(essenceType.color(), 1.4f);
        for (int step = 0; step < 8; step++) {
            serverWorld.spawnParticles(plume,
                getX(), getY() + 0.35 + step * 0.45, getZ(),
                6, 0.18, 0.16, 0.18, 0.02);
        }
        serverWorld.spawnParticles(ParticleTypes.CLOUD,
            getX(), getY() + 1.0, getZ(), 14, 0.3, 0.6, 0.3, 0.03);
        serverWorld.playSound(null, getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP,
            SoundCategory.NEUTRAL, 0.9f, 1.35f);
        serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
            SoundCategory.NEUTRAL, 0.7f, 1.2f);

        EssenceType opposite = essenceType.opposite();
        String oppositeName = opposite != null ? opposite.displayName() : "?";
        Text notice = Text.translatable("message.alkahest.elemental_evaporated", essenceType.displayName(), oppositeName)
            .formatted(Formatting.GRAY, Formatting.ITALIC);
        for (ServerPlayerEntity player : serverWorld.getEntitiesByClass(
                ServerPlayerEntity.class, new Box(getBlockPos()).expand(16.0), p -> !p.isSpectator())) {
            player.sendMessage(notice, false);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("BoundTicks", getBoundTicks());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setBoundTicks(nbt.getInt("BoundTicks"));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLOCK_AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK;
    }

    public static DustParticleEffect dustOf(int rgb, float scale) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new DustParticleEffect(new Vector3f(r, g, b), scale);
    }
}
