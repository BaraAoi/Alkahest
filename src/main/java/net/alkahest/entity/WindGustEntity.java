package net.alkahest.entity;

import net.alkahest.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.AdvancedExplosionBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import java.util.Optional;
import java.util.function.Function;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-authoritative homing gust. Only the primary impact may create two child gusts. */
public final class WindGustEntity extends ThrownEntity {
    private static final TrackedData<Boolean> CHILD=DataTracker.registerData(WindGustEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    public static final int LIFETIME=200, CHILD_LIFETIME=35;
    public static final double MAIN_MIN_SPEED=2.2, MAIN_MAX_SPEED=2.8, CHILD_SPEED=1.6;
    // Identical behavior to the player's vanilla wind charge: knockback and block triggers, no blast damage.
    private static final AdvancedExplosionBehavior WIND_BURST=new AdvancedExplosionBehavior(
        true,false,Optional.of(1.22f),Registries.BLOCK.getEntryList(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));
    public static final double MAIN_RANGE=64, CHILD_RANGE=10;
    public static final float MAIN_DAMAGE=16f, CHILD_DAMAGE=8f;
    private UUID targetId, excludedId;
    private int life;
    public WindGustEntity(EntityType<? extends WindGustEntity> type, World world) { super(type,world); setNoGravity(true); }
    public WindGustEntity(World world, LivingEntity owner, boolean child) {
        super(ModEntities.WIND_GUST,owner,world); setNoGravity(true); dataTracker.set(CHILD,child);
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(CHILD,false); }
    public boolean child() { return dataTracker.get(CHILD); }
    public UUID targetId() { return targetId; }
    public void target(Entity target) { targetId=target==null ? null : target.getUuid(); }
    public void exclude(Entity entity) { excludedId=entity==null ? null : root(entity).getUuid(); }
    /** Dragon hitboxes are independent entities, but one dragon is one target. */
    private static Entity root(Entity entity) { return entity instanceof EnderDragonPart part ? part.owner : entity; }
    private boolean validTarget(Entity entity) {
        Entity target=root(entity), owner=getOwner();
        return (target instanceof HostileEntity || target instanceof EnderDragonEntity || target instanceof EndCrystalEntity)
            && !(target instanceof EndermanEntity) && target.isAlive() && !target.isSpectator() && target!=owner
            && (excludedId==null || !excludedId.equals(target.getUuid()))
            && (owner==null || !owner.isTeammate(target));
    }
    private boolean visible(Vec3d end) {
        return getWorld().raycast(new RaycastContext(getPos(),end,RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private boolean inFront(Vec3d point) {
        Vec3d delta=point.subtract(getPos()), direction=getVelocity().normalize();
        return delta.lengthSquared()>.001 && direction.dotProduct(delta.normalize())>.65;
    }
    /** Target an actual visible dragon hitbox rather than its unhittable parent entity. */
    private Vec3d aimPoint(Entity target, boolean forwardOnly) {
        if (target instanceof EnderDragonEntity dragon) {
            Vec3d best=null; double nearest=Double.POSITIVE_INFINITY;
            for (var part : dragon.getBodyParts()) {
                Vec3d point=part.getBoundingBox().getCenter(); double distance=point.squaredDistanceTo(getPos());
                if (distance<nearest && (!forwardOnly || inFront(point)) && visible(point)) { best=point; nearest=distance; }
            }
            return best;
        }
        Vec3d point=target.getBoundingBox().getCenter();
        return (!forwardOnly || inFront(point)) && visible(point) ? point : null;
    }
    public List<Entity> nearbyTargets(double radius, boolean forwardOnly) {
        Box box=getBoundingBox().expand(radius);
        List<Entity> found=new ArrayList<>();
        found.addAll(getWorld().getEntitiesByClass(HostileEntity.class,box,this::validTarget));
        found.addAll(getWorld().getEntitiesByClass(EnderDragonEntity.class,box,this::validTarget));
        found.addAll(getWorld().getEntitiesByClass(EndCrystalEntity.class,box,this::validTarget));
        // Cache the chosen visible point once per candidate, not once per comparator invocation.
        var points=new java.util.HashMap<Entity,Vec3d>();
        for (var entity:found) { var point=aimPoint(entity,forwardOnly); if (point!=null && point.squaredDistanceTo(getPos())<=radius*radius) points.put(entity,point); }
        found.removeIf(e -> !points.containsKey(e));
        found.sort(Comparator.comparingDouble((Entity e)->points.get(e).squaredDistanceTo(getPos())).thenComparingInt(Entity::getId));
        return found;
    }
    @Override protected boolean canHit(Entity entity) {
        Entity victim=root(entity);
        if (!victim.isAlive() || victim==getOwner() || (excludedId!=null && excludedId.equals(victim.getUuid()))
            || (getOwner()!=null && getOwner().isTeammate(victim))) return false;
        if (child() && !validTarget(victim)) return false;
        if (!(entity instanceof LivingEntity) && !(entity instanceof EndCrystalEntity) && !(entity instanceof EnderDragonPart)) return false;
        return super.canHit(entity);
    }
    @Override public void tick() {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld server) {
            life++;
            if (life>(child()?CHILD_LIFETIME:LIFETIME)) { discard(); return; }
            Entity target=null; Vec3d destination=null;
            if (targetId!=null) {
                var entity=server.getEntity(targetId);
                if (entity!=null && validTarget(entity)) {
                    var point=aimPoint(entity,false);
                    if (point!=null && point.squaredDistanceTo(getPos())<=MAIN_RANGE*MAIN_RANGE) { target=entity; destination=point; }
                }
            }
            if (target==null && !child()) {
                targetId=null;
                // Avoid a 64-block search on every tick when nothing is ahead.
                if (life%4==1) {
                    var candidates=nearbyTargets(MAIN_RANGE,true);
                    if (!candidates.isEmpty()) { target=candidates.getFirst(); targetId=target.getUuid(); destination=aimPoint(target,true); }
                }
            }
            // Unassigned fragments briefly fan out for a visible split, then dissipate.
            if (target==null && child() && life>6) { discard(); return; }
            if (target!=null) {
                Vec3d velocity=getVelocity();
                Vec3d desired=destination.subtract(getPos()).normalize();
                double speed=child() ? CHILD_SPEED : Math.max(MAIN_MIN_SPEED,Math.min(MAIN_MAX_SPEED,velocity.length()));
                Vec3d current=velocity.lengthSquared()<.001?desired:velocity.normalize();
                Vec3d blended=current.multiply(child()?.60:.70).add(desired.multiply(child()?.40:.30));
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
        if (!(getWorld() instanceof ServerWorld server)) return;
        Entity struck=hit.getEntity(); Entity victim=root(struck);
        boolean damaged=struck.damage(server.getDamageSources().thrown(this,getOwner()),child()?CHILD_DAMAGE:MAIN_DAMAGE);
        if (damaged && !child()) {
            exclude(victim);
            var targets=nearbyTargets(CHILD_RANGE,false);
            if (getOwner() instanceof LivingEntity owner) {
                Vec3d velocity=getVelocity();
                Vec3d side=velocity.crossProduct(new Vec3d(0,1,0));
                if (side.lengthSquared()<.001) side=new Vec3d(1,0,0);
                side=side.normalize();
                for (int i=0;i<2;i++) {
                    var other=targets.isEmpty() ? null : targets.get(Math.min(i,targets.size()-1));
                    var fragment=new WindGustEntity(server,owner,true);
                    fragment.refreshPositionAndAngles(getX(),getY(),getZ(),getYaw(),getPitch());
                    fragment.exclude(victim); fragment.target(other);
                    Vec3d point=other==null ? null : aimPoint(other,false);
                    Vec3d direction=(point==null ? velocity.normalize() : point.subtract(getPos()).normalize())
                        .add(side.multiply(i==0?.35:-.35)).normalize();
                    fragment.setVelocity(direction.multiply(CHILD_SPEED));
                    server.spawnEntity(fragment);
                }
            }
        }
    }
    @Override protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld server) {
            Vec3d pos=hit.getPos();
            if (hit instanceof BlockHitResult block) pos=pos.add(Vec3d.of(block.getSide().getVector()).multiply(.25));
            server.createExplosion(this,null,WIND_BURST,pos.x,pos.y,pos.z,1.2f,false,World.ExplosionSourceType.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,ParticleTypes.GUST_EMITTER_LARGE,SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST);
            discard();
        }
    }
    // A wind burst must not throw newly spawned fragments off their paths.
    @Override public void addVelocity(double x, double y, double z) { }
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
