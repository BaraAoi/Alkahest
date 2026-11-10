package net.alkahest.entity;

import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModEffects;
import net.alkahest.Alkahest;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.FluidBlock;
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
import java.util.Map;
import java.util.LinkedHashMap;

/** Server-authoritative, saved zone. Only fire blocks created by this zone are removed at expiry. */
public class ReagentZoneEntity extends Entity {
    public enum Kind {
        FIRE(2.5f, 100), SLIME(3.0f, 120), NAPALM(3.5f, 200), MIASMA(4.0f, 240);
        final float radius; final int duration;
        Kind(float radius, int duration) { this.radius = radius; this.duration = duration; }
    }
    private static final RegistryKey<DamageType> NAPALM_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Alkahest.id("napalm"));
    private Kind kind = Kind.SLIME;
    private int remaining;
    private final List<BlockPos> fireBlocks = new ArrayList<>();
    // Water displaced by napalm is restored when the zone expires, unless the player replaced it with another block.
    private final Map<BlockPos, Integer> displacedWater = new LinkedHashMap<>();

    public ReagentZoneEntity(EntityType<? extends ReagentZoneEntity> type, World world) { super(type, world); noClip = true; }
    public static void spawn(ServerWorld world, Vec3d position, Kind kind) {
        ReagentZoneEntity zone = new ReagentZoneEntity(ModEntities.REAGENT_ZONE, world);
        zone.setPosition(position); zone.kind = kind; zone.remaining = kind.duration;
        if (world.spawnEntity(zone)) {
            if (kind == Kind.FIRE) zone.placeFire();
            if (kind == Kind.NAPALM) zone.placeNapalmFlames();
        }
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
    private void placeNapalmFlames() {
        BlockPos center = getBlockPos();
        int radius = (int) Math.ceil(kind.radius);
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dy * dy + dz * dz > kind.radius * kind.radius) continue;
            BlockPos pos = center.add(dx, dy, dz);
            BlockState existing = getWorld().getBlockState(pos);
            // Do not replace solid blocks or waterlogged blocks; vanilla fire can coexist with neither.
            if (!existing.isAir() && !existing.isOf(Blocks.WATER) && !existing.isOf(ModBlocks.NAPALM_FLAME)) continue;
            BlockState fire = AbstractFireBlock.getState(getWorld(), pos);
            if (!fire.canPlaceAt(getWorld(), pos)) continue;
            boolean hadWater = existing.isOf(Blocks.WATER);
            int level = hadWater ? existing.get(FluidBlock.LEVEL) : 0;
            if (getWorld().setBlockState(pos, fire)) {
                if (hadWater) displacedWater.putIfAbsent(pos.toImmutable(), level);
                if (!fireBlocks.contains(pos)) fireBlocks.add(pos.toImmutable());
            }
        }
    }
    @Override public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (remaining <= 0) { expire(); return; }
        int elapsed = kind.duration - remaining;
        if (kind == Kind.NAPALM && elapsed % 5 == 0) placeNapalmFlames();
        if (elapsed % 5 == 0) {
            if (kind == Kind.MIASMA) {
                var dust = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(.42f, .22f, .46f), 1.2f);
                world.spawnParticles(dust, getX(), getY() + .45, getZ(), 18, kind.radius / 2, .35, kind.radius / 2, .01);
            } else world.spawnParticles(kind == Kind.SLIME ? ParticleTypes.ITEM_SLIME : ParticleTypes.FLAME,
                getX(), getY() + 0.15, getZ(), 8, kind.radius / 2, 0.1, kind.radius / 2, 0.01);
        }
        if (elapsed % 10 == 0) {
            Box area = new Box(getX() - kind.radius, getY() - 1, getZ() - kind.radius,
                               getX() + kind.radius, getY() + 2, getZ() + kind.radius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> !e.isSpectator())) {
                double dx = target.getX() - getX(), dz = target.getZ() - getZ();
                if (dx * dx + dz * dz > kind.radius * kind.radius) continue;
                switch (kind) {
                    case FIRE -> { if (elapsed == 0) target.setOnFireFor(5); }
                    case SLIME -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1));
                    case MIASMA -> {
                        if (elapsed % 20 == 0) {
                            // Refresh only near expiry: constantly resetting duration can prevent vanilla's periodic damage ticks.
                            var wither = target.getStatusEffect(StatusEffects.WITHER);
                            if (wither == null || wither.getDuration() <= 20)
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1));
                            var poison = target.getStatusEffect(StatusEffects.POISON);
                            if (poison == null || poison.getDuration() <= 20)
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 75, 0));
                            for (var slot : new net.minecraft.entity.EquipmentSlot[]{
                                    net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                                    net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET})
                                target.getEquippedStack(slot).damage(5, target, slot);
                        }
                    }
                    case NAPALM -> {
                        // Eight seconds after leaving the zone, plus ordinary fire's remaining burn time.
                        target.addStatusEffect(new StatusEffectInstance(ModEffects.IN_NAPALM, 160, 0, false, true, true));
                        if (elapsed % 20 == 0)
                            target.damage(new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(NAPALM_DAMAGE)), 3.0f);
                    }
                }
            }
        }
        if (--remaining == 0) expire();
    }
    private void expire() {
        if (!getWorld().isClient) for (BlockPos pos : fireBlocks) {
            BlockState current = getWorld().getBlockState(pos);
            boolean fire = current.isOf(Blocks.FIRE) || current.isOf(Blocks.SOUL_FIRE) || current.isOf(ModBlocks.NAPALM_FLAME);
            if (kind == Kind.NAPALM && displacedWater.containsKey(pos) && (fire || current.isAir()))
                getWorld().setBlockState(pos, Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, displacedWater.get(pos)));
            else if (fire) getWorld().removeBlock(pos, false);
        }
        discard();
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) { }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Kind", kind.name()); nbt.putInt("Remaining", remaining);
        nbt.putLongArray("FireBlocks", fireBlocks.stream().mapToLong(BlockPos::asLong).toArray());
        nbt.putLongArray("WaterPositions", displacedWater.keySet().stream().mapToLong(BlockPos::asLong).toArray());
        nbt.putIntArray("WaterLevels", displacedWater.values().stream().mapToInt(Integer::intValue).toArray());
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        try { kind = Kind.valueOf(nbt.getString("Kind")); } catch (IllegalArgumentException e) { kind = Kind.SLIME; }
        remaining = Math.max(0, Math.min(kind.duration, nbt.getInt("Remaining")));
        fireBlocks.clear();
        for (long packed : nbt.getLongArray("FireBlocks")) fireBlocks.add(BlockPos.fromLong(packed));
        displacedWater.clear();
        long[] positions = nbt.getLongArray("WaterPositions");
        int[] levels = nbt.getIntArray("WaterLevels");
        for (int i = 0; i < Math.min(positions.length, levels.length); i++) {
            if (levels[i] >= 0 && levels[i] <= 15) displacedWater.put(BlockPos.fromLong(positions[i]), levels[i]);
        }
    }
}
