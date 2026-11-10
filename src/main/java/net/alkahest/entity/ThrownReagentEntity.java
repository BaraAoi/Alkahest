package net.alkahest.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.item.custom.BindingSaltItem;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.Set;

public class ThrownReagentEntity extends ThrownItemEntity {
    public ThrownReagentEntity(EntityType<? extends ThrownReagentEntity> type, World world) { super(type, world); }
    @Override protected Item getDefaultItem() { return ModItems.FIRE_DUST; }

    @Override protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!(getWorld() instanceof ServerWorld world)) return;
        ItemStack reagent = getStack();
        if (reagent.getItem() instanceof BindingSaltItem bindingSalt) {
            Entity owner = getOwner();
            EssenceType saltType = bindingSalt.essenceType();
            float radius = 2.75f;
            Set<LivingEntity> targets = new LinkedHashSet<>();
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity direct && !direct.isSpectator()) {
                targets.add(direct);
            }
            for (LivingEntity nearby : world.getEntitiesByClass(
                    LivingEntity.class,
                    new Box(hit.getPos(), hit.getPos()).expand(radius),
                    entity -> !entity.isSpectator() && (owner == null || entity != owner))) {
                targets.add(nearby);
            }
            for (LivingEntity target : targets) {
                if (target instanceof ElementalEntity elemental) {
                    elemental.applyBindingSalt(saltType, 300, owner);
                } else {
                    target.addStatusEffect(new StatusEffectInstance(ModEffects.CRYSTALLIZED, 300, 0), owner);
                }
            }
            int color = saltType != null ? saltType.color() : 0xDCEEFF;
            world.spawnParticles(ElementalEntity.dustOf(color, 1.25f),
                hit.getPos().x, hit.getPos().y + 0.15, hit.getPos().z, 34, 0.75, 0.45, 0.75, 0.04);
            world.spawnParticles(ParticleTypes.WAX_OFF,
                hit.getPos().x, hit.getPos().y + 0.2, hit.getPos().z, 16, 0.55, 0.35, 0.55, 0.02);
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9f, 1.15f);
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.55f, 1.35f);
        } else if (reagent.isOf(ModItems.LIGHTNING_IN_A_BOTTLE)) {
            var bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt != null) {
                bolt.setPosition(hit.getPos());
                if (getOwner() instanceof ServerPlayerEntity player) bolt.setChanneler(player);
                world.spawnEntity(bolt);
            }
        } else if (reagent.isOf(ModItems.ACID_BOMB)) {
            float radius = 3.5f;
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(getPos(), getPos()).expand(radius),
                    entity -> !entity.isSpectator() && entity.squaredDistanceTo(this) <= radius * radius)) {
                Entity owner = getOwner();
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    target.getEquippedStack(slot).damage(35, target, slot);
                }
                target.damage(owner != null ? world.getDamageSources().indirectMagic(this, owner) : world.getDamageSources().magic(), 6.0f);
            }
            world.spawnParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 55, 1.0, 0.6, 1.0, 0.15);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.32f, 0.9f, 0.12f), 1.3f),
                getX(), getY(), getZ(), 45, 1.1, 0.6, 1.1, 0.05);
            world.spawnParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 16, 0.6, 0.3, 0.6, 0.05);
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
        } else {
            ReagentZoneEntity.Kind kind = reagent.isOf(ModItems.FIRE_DUST) ? ReagentZoneEntity.Kind.FIRE
                : reagent.isOf(ModItems.CAUSTIC_SLIME) ? ReagentZoneEntity.Kind.SLIME
                : reagent.isOf(ModItems.NAPALM) ? ReagentZoneEntity.Kind.NAPALM
                : reagent.isOf(ModItems.MIASMA) ? ReagentZoneEntity.Kind.MIASMA : null;
            if (kind != null) ReagentZoneEntity.spawn(world, getPos(), kind);
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
        discard();
    }
}
