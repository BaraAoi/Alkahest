package net.alkahest.entity;

import net.alkahest.essence.EssenceType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ranged projectile fired by hostile Prima Elementals.
 * Each Prima essence inflicts a distinct elemental hazard on impact.
 */
public class ElementalBoltEntity extends ThrownEntity {
    private static final TrackedData<Integer> ESSENCE_ORDINAL = DataTracker.registerData(
        ElementalBoltEntity.class, TrackedDataHandlerRegistry.INTEGER
    );

    public ElementalBoltEntity(EntityType<? extends ElementalBoltEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public ElementalBoltEntity(EntityType<? extends ElementalBoltEntity> type, LivingEntity owner,
                               World world, EssenceType essenceType) {
        super(type, owner, world);
        this.setNoGravity(true);
        setEssenceType(essenceType);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ESSENCE_ORDINAL, EssenceType.IGNIS.ordinal());
    }

    public EssenceType essenceType() {
        int idx = dataTracker.get(ESSENCE_ORDINAL);
        EssenceType[] values = EssenceType.values();
        if (idx < 0 || idx >= values.length || values[idx].tier() != EssenceType.Tier.PRIMA) {
            return EssenceType.IGNIS;
        }
        return values[idx];
    }

    public void setEssenceType(EssenceType type) {
        if (type != null && type.tier() == EssenceType.Tier.PRIMA) {
            dataTracker.set(ESSENCE_ORDINAL, type.ordinal());
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        if (entity instanceof ElementalEntity) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    public void tick() {
        Vec3d steadyVelocity = getVelocity();
        super.tick();
        if (isRemoved()) return;

        // Keep bolt speed steady over ranged combat distances
        if (!isTouchingWater() && steadyVelocity.lengthSquared() > 0.01) {
            setVelocity(steadyVelocity);
        }

        EssenceType type = essenceType();
        if (getWorld().isClient) {
            getWorld().addParticle(ElementalEntity.dustOf(type.color(), 1.0f),
                getX(), getY() + 0.1, getZ(), 0.0, 0.0, 0.0);
            if (type == EssenceType.IGNIS && random.nextBoolean()) {
                getWorld().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 0.0, 0.01, 0.0);
            } else if (type == EssenceType.AQUA && random.nextBoolean()) {
                getWorld().addParticle(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 0.0, 0.01, 0.0);
            } else if (type == EssenceType.AER && random.nextBoolean()) {
                getWorld().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 0.0, 0.0, 0.0);
            }
            return;
        }

        if (type == EssenceType.IGNIS && isTouchingWater()) {
            if (getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 10, 0.15, 0.15, 0.15, 0.02);
                serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.HOSTILE, 0.6f, 1.3f);
            }
            discard();
            return;
        }

        if (age > 80) {
            discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!(getWorld() instanceof ServerWorld serverWorld)) return;
        Entity hitEntity = entityHitResult.getEntity();
        Entity owner = getOwner();
        DamageSource source = owner instanceof LivingEntity livingOwner
            ? serverWorld.getDamageSources().mobProjectile(this, livingOwner)
            : serverWorld.getDamageSources().magic();

        EssenceType type = essenceType();
        float damage = switch (type) {
            case TERRA -> 6.5f;
            case AER -> 4.5f;
            default -> 5.0f;
        };

        boolean damaged = hitEntity.damage(source, damage);
        if (damaged && hitEntity instanceof LivingEntity target) {
            switch (type) {
                case IGNIS -> target.setOnFireFor(5.0f);
                case AQUA -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1), owner);
                    target.setFrozenTicks(Math.min(target.getMinFreezeDamageTicks() + 60, target.getFrozenTicks() + 100));
                }
                case TERRA -> {
                    Vec3d push = getVelocity().multiply(1.0, 0.0, 1.0);
                    if (push.lengthSquared() > 0.001) {
                        Vec3d norm = push.normalize();
                        target.takeKnockback(0.65, -norm.x, -norm.z);
                    }
                }
                case AER -> {
                    Vec3d gust = getVelocity().multiply(1.0, 0.0, 1.0);
                    if (gust.lengthSquared() > 0.001) {
                        Vec3d norm = gust.normalize();
                        target.takeKnockback(1.15, -norm.x, -norm.z);
                    }
                    target.addVelocity(0.0, 0.34, 0.0);
                    target.velocityModified = true;
                }
                case ORDO -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 1), owner);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), owner);
                }
                case PERDITIO -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 0), owner);
                    for (EquipmentSlot slot : new EquipmentSlot[]{
                            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        target.getEquippedStack(slot).damage(4, target, slot);
                    }
                }
                default -> { }
            }
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (getWorld() instanceof ServerWorld serverWorld) {
            EssenceType type = essenceType();
            serverWorld.spawnParticles(ElementalEntity.dustOf(type.color(), 1.2f),
                getX(), getY(), getZ(), 18, 0.25, 0.25, 0.25, 0.04);
            serverWorld.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                SoundCategory.HOSTILE, 0.8f, 1.2f);
            discard();
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Essence", essenceType().asString());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        String id = nbt.getString("Essence");
        for (EssenceType t : EssenceType.values()) {
            if (t.tier() == EssenceType.Tier.PRIMA && t.asString().equals(id)) {
                setEssenceType(t);
                break;
            }
        }
    }
}
