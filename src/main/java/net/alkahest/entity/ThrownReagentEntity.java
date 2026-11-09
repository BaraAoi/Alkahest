package net.alkahest.entity;

import net.alkahest.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class ThrownReagentEntity extends ThrownItemEntity {
    public ThrownReagentEntity(EntityType<? extends ThrownReagentEntity> type, World world) { super(type, world); }
    @Override protected Item getDefaultItem() { return ModItems.FIRE_DUST; }
    @Override protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!(getWorld() instanceof ServerWorld world)) return;
        ItemStack reagent = getStack();
        if (reagent.isOf(ModItems.ACID_BOMB)) {
            float radius = 3.5f;
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(getPos(), getPos()).expand(radius),
                    entity -> !entity.isSpectator() && entity.squaredDistanceTo(this) <= radius * radius)) {
                Entity owner = getOwner();
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    target.getEquippedStack(slot).damage(35, target, slot);
                }
                target.damage(owner != null ? world.getDamageSources().indirectMagic(this, owner) : world.getDamageSources().magic(), 6.0f);
            }
            world.spawnParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 40, 0.7, 0.4, 0.7, 0.2);
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.4f);
        } else {
            ReagentZoneEntity.Kind kind = reagent.isOf(ModItems.FIRE_DUST) ? ReagentZoneEntity.Kind.FIRE
                : reagent.isOf(ModItems.CAUSTIC_SLIME) ? ReagentZoneEntity.Kind.SLIME
                : reagent.isOf(ModItems.NAPALM) ? ReagentZoneEntity.Kind.NAPALM : null;
            if (kind != null) ReagentZoneEntity.spawn(world, getPos(), kind);
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
        discard();
    }
}
