package net.alkahest.effect;

import net.alkahest.Alkahest;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * Encases the target in an alchemical salt lattice (-70% ground and flying speed).
 * Required together with opposite-element binding salt to prevent Elemental evaporation.
 */
public final class CrystallizedEffect extends StatusEffect {
    public CrystallizedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8FE8FF);
        addAttributeModifier(
            EntityAttributes.GENERIC_MOVEMENT_SPEED,
            Alkahest.id("crystallized_movement"),
            -0.70,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        addAttributeModifier(
            EntityAttributes.GENERIC_FLYING_SPEED,
            Alkahest.id("crystallized_flying"),
            -0.70,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.WAX_OFF,
                entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                4, entity.getWidth() * 0.45, entity.getHeight() * 0.35, entity.getWidth() * 0.45, 0.02
            );
        }
        return true;
    }
}
