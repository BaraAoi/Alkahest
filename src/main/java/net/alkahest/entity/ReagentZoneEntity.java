package net.alkahest.entity;

import net.alkahest.registry.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative, saved zone. Only fire blocks created by this zone are removed at expiry. */
public class ReagentZoneEntity extends Entity {
    public enum Kind {
        FIRE(2.5f, 100), SLIME(3.0f, 120), NAPALM(3.5f, 200);
        final float radius; final int duration;
        Kind(float radius, int duration) { this.radius = radius; this.duration = duration; }
    }
    private Kind kind = Kind.SLIME;
    private int remaining;
    private final List<BlockPos> fireBlocks = new ArrayList<>();

    public ReagentZoneEntity(EntityType<? extends ReagentZoneEntity> type, World world) { super(type, world); noClip = true; }
    public static void spawn(ServerWorld world, Vec3d position, Kind kind) {
        ReagentZoneEntity zone = new ReagentZoneEntity(ModEntities.REAGENT_ZONE, world);
        zone.setPosition(position); zone.kind = kind; zone.remaining = kind.duration;
        if (world.spawnEntity(zone) && kind == Kind.FIRE) zone.placeFire();
    }
    private void placeFire() {
        BlockPos center = getBlockPos();
        int radius = (int) Math.ceil(kind.radius);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > kind.radius * kind.radius) continue;
            BlockPos pos = center.add(dx, 0, dz);
            BlockState fire = Blocks.FIRE.getDefaultState();
            if (getWorld().isAir(pos) && fire.canPlaceAt(getWorld(), pos)) {
                getWorld().setBlockState(pos, fire);
                fireBlocks.add(pos.toImmutable());
            }
        }
    }
    @Override public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (remaining <= 0) { expire(); return; }
        int elapsed = kind.duration - remaining;
        if (elapsed % 5 == 0) {
            world.spawnParticles(kind == Kind.SLIME ? ParticleTypes.ITEM_SLIME : ParticleTypes.FLAME,
                getX(), getY() + 0.15, getZ(), 8, kind.radius / 2, 0.1, kind.radius / 2, 0.01);
        }
        if (elapsed % (kind == Kind.NAPALM ? 20 : 10) == 0) {
            Box area = new Box(getX() - kind.radius, getY() - 1, getZ() - kind.radius,
                               getX() + kind.radius, getY() + 2, getZ() + kind.radius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> !e.isSpectator())) {
                double dx = target.getX() - getX(), dz = target.getZ() - getZ();
                if (dx * dx + dz * dz > kind.radius * kind.radius) continue;
                switch (kind) {
                    case FIRE -> { if (elapsed == 0) target.setOnFireFor(5); }
                    case SLIME -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1));
                    case NAPALM -> target.damage(world.getDamageSources().inFire(), 3.0f);
                }
            }
        }
        if (--remaining == 0) expire();
    }
    private void expire() {
        if (!getWorld().isClient) for (BlockPos pos : fireBlocks) {
            if (getWorld().getBlockState(pos).isOf(Blocks.FIRE)) getWorld().removeBlock(pos, false);
        }
        discard();
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) { }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Kind", kind.name()); nbt.putInt("Remaining", remaining);
        nbt.putLongArray("FireBlocks", fireBlocks.stream().mapToLong(BlockPos::asLong).toArray());
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        try { kind = Kind.valueOf(nbt.getString("Kind")); } catch (IllegalArgumentException e) { kind = Kind.SLIME; }
        remaining = Math.max(0, Math.min(kind.duration, nbt.getInt("Remaining")));
        fireBlocks.clear();
        for (long packed : nbt.getLongArray("FireBlocks")) fireBlocks.add(BlockPos.fromLong(packed));
    }
}
