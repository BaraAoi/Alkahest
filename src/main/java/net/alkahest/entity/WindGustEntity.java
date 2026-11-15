package net.alkahest.entity;

import net.alkahest.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Server-authoritative homing gust. Only the primary impact may create two child gusts. */
public final class WindGustEntity extends ThrownEntity {
    private static final TrackedData<Boolean> CHILD=DataTracker.registerData(WindGustEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    public static final int LIFETIME=80, CHILD_LIFETIME=35;
    private UUID targetId, excludedId;
    private int life;
    public WindGustEntity(EntityType<? extends WindGustEntity> type, World world) { super(type,world); setNoGravity(true); }
    public WindGustEntity(World world, LivingEntity owner, boolean child) {
        super(ModEntities.WIND_GUST,owner,world); setNoGravity(true); dataTracker.set(CHILD,child);
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(CHILD,false); }
    public boolean child() { return dataTracker.get(CHILD); }
    public UUID targetId() { return targetId; }
    public void target(LivingEntity target) { targetId=target==null ? null : target.getUuid(); }
    public void exclude(Entity entity) { excludedId=entity==null ? null : entity.getUuid(); }
    private boolean validTarget(LivingEntity target) {
        Entity owner=getOwner();
        return target instanceof HostileEntity && target.isAlive() && !target.isSpectator() && target!=owner
            && (excludedId==null || !excludedId.equals(target.getUuid()))
            && (owner==null || !owner.isTeammate(target));
    }
    private boolean clearPath(LivingEntity target) {
        var end=target.getBoundingBox().getCenter();
        return getWorld().raycast(new RaycastContext(getPos(),end,RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,this)).getType()==HitResult.Type.MISS;
    }
    public List<HostileEntity> nearbyTargets(double radius, boolean forwardOnly) {
        Vec3d direction=getVelocity().normalize();
        Box box=getBoundingBox().expand(radius);
        return getWorld().getEntitiesByClass(HostileEntity.class,box,t -> {
            Vec3d delta=t.getBoundingBox().getCenter().subtract(getPos());
            return validTarget(t) && delta.lengthSquared() <= radius*radius && clearPath(t)
                && (!forwardOnly || delta.lengthSquared()>.001 && direction.dotProduct(delta.normalize())>.65);
        }).stream().sorted(Comparator.comparingDouble((HostileEntity e)->e.squaredDistanceTo(this)).thenComparingInt(Entity::getId)).toList();
    }
    @Override protected boolean canHit(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity==getOwner()
            || (excludedId!=null && excludedId.equals(entity.getUuid()))
            || (getOwner()!=null && getOwner().isTeammate(entity))) return false;
        if (child() && !validTarget(living)) return false;
        return super.canHit(entity);
    }
    @Override public void tick() {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld server) {
            life++;
            if (life>(child()?CHILD_LIFETIME:LIFETIME)) { discard(); return; }
            LivingEntity target=null;
            if (targetId!=null && server.getEntity(targetId) instanceof LivingEntity entity && validTarget(entity)
                && squaredDistanceTo(entity)<=24*24 && clearPath(entity)) target=entity;
            if (target==null && !child()) {
                var candidates=nearbyTargets(20,true);
                if (!candidates.isEmpty()) { target=candidates.getFirst(); targetId=target.getUuid(); }
                else targetId=null;
            }
            if (target==null && child()) { discard(); return; }
            if (target!=null) {
                Vec3d velocity=getVelocity();
                Vec3d desired=target.getBoundingBox().getCenter().subtract(getPos()).normalize();
                double speed=Math.max(1.05,Math.min(1.65,velocity.length()));
                Vec3d current=velocity.lengthSquared()<.001?desired:velocity.normalize();
                Vec3d blended=current.multiply(child()?.68:.82).add(desired.multiply(child()?.32:.18));
                if (blended.lengthSquared()>.0001) setVelocity(blended.normalize().multiply(speed));
            }
        }
        Vec3d before=getVelocity();
        super.tick();
        if (isRemoved()) return;
        // Flight is wind, not a falling arrow; negate drag including underwater drag.
        if (before.lengthSquared()>.0001) setVelocity(before);
        if (getWorld().isClient) trail();
    }
    private void trail() {
        Vec3d v=getVelocity().normalize();
        Vec3d a=v.crossProduct(new Vec3d(0,1,0));
        if (a.lengthSquared()<.001) a=v.crossProduct(new Vec3d(1,0,0));
        a=a.normalize(); Vec3d b=v.crossProduct(a).normalize();
        for (int i=0;i<3;i++) {
            double angle=age*.7+i*2.094, r=child()?.13:.22;
            Vec3d off=a.multiply(Math.cos(angle)*r).add(b.multiply(Math.sin(angle)*r)).subtract(v.multiply(i*.18));
            getWorld().addParticle(ParticleTypes.CLOUD,getX()+off.x,getY()+off.y,getZ()+off.z,-v.x*.012,-v.y*.012,-v.z*.012);
        }
    }
    @Override protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (!(getWorld() instanceof ServerWorld server) || !(hit.getEntity() instanceof LivingEntity victim)) return;
        boolean damaged=victim.damage(server.getDamageSources().thrown(this,getOwner()),child()?3f:6f);
        if (damaged && !child()) {
            exclude(victim);
            var targets=nearbyTargets(10,false);
            if (!targets.isEmpty() && getOwner() instanceof LivingEntity owner) {
                Vec3d velocity=getVelocity();
                Vec3d side=velocity.crossProduct(new Vec3d(0,1,0));
                if (side.lengthSquared()<.001) side=new Vec3d(1,0,0);
                side=side.normalize();
                for (int i=0;i<2;i++) {
                    var other=targets.get(Math.min(i,targets.size()-1));
                    var fragment=new WindGustEntity(server,owner,true);
                    fragment.refreshPositionAndAngles(getX(),getY(),getZ(),getYaw(),getPitch());
                    fragment.exclude(victim); fragment.target(other);
                    Vec3d direction=other.getBoundingBox().getCenter().subtract(getPos()).normalize().add(side.multiply(i==0?.35:-.35)).normalize();
                    fragment.setVelocity(direction.multiply(1.1));
                    server.spawnEntity(fragment);
                }
            }
        }
    }
    @Override protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld server) {
            server.spawnParticles(ParticleTypes.CLOUD,getX(),getY(),getZ(),child()?9:22,.25,.25,.25,.06);
            server.playSound(null,getBlockPos(),SoundEvents.ENTITY_BREEZE_SHOOT,SoundCategory.PLAYERS,.45f,child()?1.5f:1.2f);
            discard();
        }
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt); nbt.putBoolean("Child",child()); nbt.putInt("Life",life);
        if (targetId!=null) nbt.putUuid("WindTarget",targetId);
        if (excludedId!=null) nbt.putUuid("WindExcluded",excludedId);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt); dataTracker.set(CHILD,nbt.getBoolean("Child"));
        life=Math.max(0,Math.min(LIFETIME+1,nbt.getInt("Life")));
        targetId=nbt.containsUuid("WindTarget")?nbt.getUuid("WindTarget"):null;
        excludedId=nbt.containsUuid("WindExcluded")?nbt.getUuid("WindExcluded"):null;
        setNoGravity(true);
    }
}
