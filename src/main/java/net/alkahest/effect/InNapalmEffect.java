package net.alkahest.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;

/** Keeps the victim burning during exposure and for a short time afterwards. */
public final class InNapalmEffect extends StatusEffect {
    public InNapalmEffect() { super(StatusEffectCategory.HARMFUL, 0xF05A19); }
    @Override public boolean canApplyUpdateEffect(int duration, int amplifier) { return true; }
    @Override public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!entity.getWorld().isClient && !entity.isFireImmune() && !entity.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            if (entity.getFireTicks() < 40) entity.setOnFireFor(3.0f);
        }
        return true;
    }
}
