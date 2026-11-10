package net.alkahest.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * Hostile hovering manifestation of a Prima essence (Ignis, Aqua, Terra, Aer, Ordo, Perditio).
 * Fires elemental ranged bolts from the air; Binding Salt strips its levitation, forces it
 * to crash to the ground, and (when charged with the antipode essence) anchors its crystal drops.
 */
public class ElementalEntity extends HostileEntity {
    private static final TrackedData<Integer> BOUND_TICKS = DataTracker.registerData(
        ElementalEntity.class, TrackedDataHandlerRegistry.INTEGER
    );
    private static final TrackedData<Boolean> GROUNDED = DataTracker.registerData(
        ElementalEntity.class, TrackedDataHandlerRegistry.BOOLEAN
    );
    private static final TrackedData<Boolean> CHARGING = DataTracker.registerData(
        ElementalEntity.class, TrackedDataHandlerRegistry.BOOLEAN
    );

    private final EssenceType essenceType;
    private final BirdNavigation airNavigation;
    private final MobNavigation groundNavigation;

    public ElementalEntity(EntityType<? extends ElementalEntity> entityType, World world, EssenceType essenceType) {
        super(entityType, world);
        this.essenceType = essenceType;
        this.airNavigation = createAirNavigation(world);
        this.groundNavigation = new MobNavigation(this, world);
        this.navigation = this.airNavigation;
        this.moveControl = new ElementalMoveControl(this);
        this.setNoGravity(true);
        this.experiencePoints = 12;
    }

    private BirdNavigation createAirNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(true);
        return nav;
    }

    public static DefaultAttributeContainer.Builder createElementalAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
            .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    public static boolean canSpawn(EntityType<ElementalEntity> type, ServerWorldAccess world,
                                   SpawnReason reason, BlockPos pos, Random random) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason == SpawnReason.NATURAL && type == ModEntities.ELEMENTALS.get(EssenceType.AER)
            && pos.getY() < 95 && !world.getBiome(pos).matchesKey(net.alkahest.registry.ModWorldGen.ALCHEMICAL_WASTES)) {
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

    public boolean isGroundedBySalt() {
        return dataTracker.get(GROUNDED);
    }

    private void setGroundedBySalt(boolean grounded) {
        dataTracker.set(GROUNDED, grounded);
    }

    public boolean isChargingAttack() {
        return dataTracker.get(CHARGING);
    }

    public void setChargingAttack(boolean charging) {
        dataTracker.set(CHARGING, charging);
    }

    public boolean isProperlyBound() {
        return getBoundTicks() > 0 && hasStatusEffect(ModEffects.CRYSTALLIZED);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(BOUND_TICKS, 0);
        builder.add(GROUNDED, false);
        builder.add(CHARGING, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new ElementalCombatGoal(this));
        this.goalSelector.add(2, new IdleHoverGoal(this));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return createAirNavigation(world);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (!isGroundedBySalt()) {
            return false;
        }
        return super.handleFallDamage(fallDistance, damageMultiplier * 0.3f, damageSource);
    }

    /** Strips levitation immediately so the Elemental drops to the ground under gravity. */
    public void groundFromLevitation() {
        setGroundedBySalt(true);
        setNoGravity(false);
        if (this.navigation != groundNavigation) {
            this.airNavigation.stop();
            this.navigation = groundNavigation;
        }
        Vec3d v = getVelocity();
        if (!isOnGround()) {
            setVelocity(v.x * 0.25, Math.min(v.y, -0.32), v.z * 0.25);
            this.velocityModified = true;
        }
    }

    private void restoreLevitation() {
        setGroundedBySalt(false);
        setNoGravity(true);
        if (this.navigation != airNavigation) {
            this.groundNavigation.stop();
            this.navigation = airNavigation;
        }
        if (isOnGround()) {
            addVelocity(0.0, 0.26, 0.0);
            this.velocityModified = true;
        }
    }

    public ElementalBoltEntity shootElementalBolt(LivingEntity target) {
        if (getWorld().isClient) return null;
        double sx = getX();
        double sy = getBodyY(0.62);
        double sz = getZ();
        double dx = target.getX() - sx;
        double dy = target.getBodyY(0.5) - sy;
        double dz = target.getZ() - sz;

        ElementalBoltEntity bolt = new ElementalBoltEntity(ModEntities.ELEMENTAL_BOLT, this, getWorld(), essenceType);
        bolt.setPosition(sx, sy, sz);
        float speed = essenceType == EssenceType.AER ? 1.45f : 1.15f;
        bolt.setVelocity(dx, dy, dz, speed, 0.25f);
        getWorld().spawnEntity(bolt);

        if (getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(dustOf(essenceType.color(), 1.2f),
                sx, sy, sz, 12, 0.2, 0.2, 0.2, 0.03);
            SoundEvent castSound = essenceType == EssenceType.IGNIS
                ? SoundEvents.ITEM_FIRECHARGE_USE
                : SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            serverWorld.playSound(null, getBlockPos(), castSound, SoundCategory.HOSTILE, 0.9f, 0.9f + random.nextFloat() * 0.3f);
        }
        return bolt;
    }

    @Override
    public void tick() {
        super.tick();
        if (essenceType == EssenceType.AQUA && getAir() < getMaxAir()) {
            setAir(getMaxAir());
        }

        if (getWorld().isClient) {
            if (age % 2 == 0) {
                DustParticleEffect dust = dustOf(essenceType.color(), isChargingAttack() ? 1.35f : 1.05f);
                getWorld().addParticle(
                    dust,
                    getX() + (random.nextDouble() - 0.5) * 0.75,
                    getY() + 0.35 + random.nextDouble() * 0.95,
                    getZ() + (random.nextDouble() - 0.5) * 0.75,
                    0.0, isGroundedBySalt() ? -0.02 : 0.02, 0.0
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

        boolean salted = hasStatusEffect(ModEffects.CRYSTALLIZED);
        if (salted) {
            if (!isGroundedBySalt() || hasNoGravity()) {
                groundFromLevitation();
            } else if (!isOnGround() && hurtTime == 0 && getVelocity().y > 0.02) {
                Vec3d v = getVelocity();
                setVelocity(v.x, -0.08, v.z);
            }
        } else if (isGroundedBySalt()) {
            restoreLevitation();
        }

        int bound = getBoundTicks();
        if (bound > 0) {
            if (!salted) {
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
     * Applies Crystallized (-70% speed), strips levitation so the Elemental falls to the ground,
     * and checks whether the thrown salt carries the antipode Prima essence to anchor crystal drops.
     */
    public boolean applyBindingSalt(EssenceType saltType, int durationTicks, Entity source) {
        addStatusEffect(new StatusEffectInstance(ModEffects.CRYSTALLIZED, durationTicks, 0), source);
        groundFromLevitation();

        EssenceType required = essenceType.opposite();
        if (saltType != null && saltType == required) {
            setBoundTicks(durationTicks);
            if (getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(dustOf(required.color(), 1.35f),
                    getX(), getBodyY(0.5), getZ(), 28, 0.55, 0.55, 0.55, 0.04);
                serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.HOSTILE, 1.0f, 0.85f);
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
                SoundCategory.HOSTILE, 1.0f, 1.05f);
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
            SoundCategory.HOSTILE, 0.9f, 1.35f);
        serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
            SoundCategory.HOSTILE, 0.7f, 1.2f);

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
        nbt.putBoolean("GroundedBySalt", isGroundedBySalt());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setBoundTicks(nbt.getInt("BoundTicks"));
        if (nbt.getBoolean("GroundedBySalt")) {
            groundFromLevitation();
        }
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

    /** Switches seamlessly between 3D levitation and grounded walking when Binding Salt hits. */
    private static final class ElementalMoveControl extends MoveControl {
        private final ElementalEntity elemental;

        ElementalMoveControl(ElementalEntity elemental) {
            super(elemental);
            this.elemental = elemental;
        }

        @Override
        public void tick() {
            if (elemental.isGroundedBySalt()) {
                elemental.setNoGravity(false);
                super.tick();
                return;
            }

            elemental.setNoGravity(true);
            if (this.state != State.MOVE_TO) {
                elemental.setVelocity(elemental.getVelocity().multiply(0.82));
                return;
            }

            Vec3d delta = new Vec3d(
                this.targetX - elemental.getX(),
                this.targetY - elemental.getY(),
                this.targetZ - elemental.getZ()
            );
            double dist = delta.length();
            if (dist < 0.35) {
                this.state = State.WAIT;
                elemental.setVelocity(elemental.getVelocity().multiply(0.65));
                return;
            }

            double flySpeed = elemental.getAttributeValue(EntityAttributes.GENERIC_FLYING_SPEED);
            Vec3d step = delta.normalize().multiply(this.speed * flySpeed * 0.32);
            elemental.setVelocity(elemental.getVelocity().multiply(0.75).add(step));

            if (elemental.getTarget() == null) {
                float yaw = (float) (MathHelper.atan2(step.z, step.x) * (180.0 / Math.PI)) - 90.0f;
                elemental.setYaw(wrapDegrees(elemental.getYaw(), yaw, 14.0f));
                elemental.bodyYaw = elemental.getYaw();
            }
        }
    }

    /** Gentle hovering patrol when no hostile target is nearby and levitation is active. */
    private static final class IdleHoverGoal extends FlyGoal {
        private final ElementalEntity elemental;

        IdleHoverGoal(ElementalEntity elemental) {
            super(elemental, 0.85);
            this.elemental = elemental;
        }

        @Override
        public boolean canStart() {
            return !elemental.isGroundedBySalt() && elemental.getTarget() == null && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return !elemental.isGroundedBySalt() && elemental.getTarget() == null && super.shouldContinue();
        }
    }

    /** Aerial strafing + ranged bolt volleys + close-quarters melee fallback. */
    private static final class ElementalCombatGoal extends Goal {
        private final ElementalEntity elemental;
        private int attackCooldown = 25;
        private int meleeCooldown = 0;
        private int strafeTimer = 0;
        private float strafeSign = 1.0f;

        ElementalCombatGoal(ElementalEntity elemental) {
            this.elemental = elemental;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public boolean canStart() {
            LivingEntity target = elemental.getTarget();
            return target != null && target.isAlive() && !target.isSpectator();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            elemental.setChargingAttack(false);
            attackCooldown = 25;
        }

        @Override
        public void tick() {
            LivingEntity target = elemental.getTarget();
            if (target == null) return;

            elemental.getLookControl().lookAt(target, 30.0f, 30.0f);
            double dx = target.getX() - elemental.getX();
            double dz = target.getZ() - elemental.getZ();
            float faceYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            elemental.setYaw(faceYaw);
            elemental.bodyYaw = faceYaw;

            double distSq = elemental.squaredDistanceTo(target);
            boolean canSee = elemental.getVisibilityCache().canSee(target);

            if (elemental.isGroundedBySalt()) {
                elemental.getNavigation().startMovingTo(target, 1.0);
            } else {
                if (--strafeTimer <= 0) {
                    strafeTimer = 35 + elemental.random.nextInt(30);
                    if (elemental.random.nextFloat() < 0.45f) {
                        strafeSign = -strafeSign;
                    }
                }
                double horizDist = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                double nx = dx / horizDist;
                double nz = dz / horizDist;
                // Perpendicular vector for aerial circling
                double px = -nz * strafeSign;
                double pz = nx * strafeSign;

                double radial = horizDist > 11.0 ? 2.5 : (horizDist < 5.5 ? -2.2 : 0.0);
                double goalX = elemental.getX() + nx * radial + px * 2.4;
                double goalZ = elemental.getZ() + nz * radial + pz * 2.4;
                double goalY = target.getY() + 1.9 + Math.sin(elemental.age * 0.08) * 0.6;
                elemental.getMoveControl().moveTo(goalX, goalY, goalZ, 1.15);
            }

            if (meleeCooldown > 0) meleeCooldown--;
            if (distSq <= 4.8 && meleeCooldown <= 0) {
                meleeCooldown = 20;
                elemental.tryAttack(target);
            }

            if (canSee && distSq <= 22.0 * 22.0) {
                attackCooldown--;
                if (attackCooldown == 15) {
                    elemental.setChargingAttack(true);
                    if (elemental.getWorld() instanceof ServerWorld serverWorld) {
                        serverWorld.playSound(null, elemental.getBlockPos(),
                            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 0.7f, 1.55f);
                    }
                } else if (attackCooldown <= 0) {
                    elemental.shootElementalBolt(target);
                    elemental.setChargingAttack(false);
                    attackCooldown = elemental.isGroundedBySalt() ? 58 : 42;
                }
            } else {
                elemental.setChargingAttack(false);
                if (attackCooldown < 18) attackCooldown = 18;
            }
        }
    }
}
