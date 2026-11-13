package net.alkahest.mixin;

import net.alkahest.item.custom.ShieldRingItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldMixin {
    private static final TagKey<DamageType> ALKAHEST_FALL = TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.ofVanilla("is_fall"));
    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void alkahest$shieldAfterMitigation(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof PlayerEntity player && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))
            cir.setReturnValue(ShieldRingItem.absorb(player, cir.getReturnValueF(), !source.isIn(ALKAHEST_FALL)));
    }
}
