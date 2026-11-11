package net.alkahest.mixin;

import net.alkahest.loot.MidasLootState;
import net.alkahest.registry.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One roll per death, enclosing the actual loot and equipment calls rather than rerolling loot. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMidasLootMixin implements MidasLootState {
    @Unique private boolean alkahest$duplicate;
    @Unique private boolean alkahest$copying;

    @Override public boolean alkahest$duplicateDrops() { return alkahest$duplicate; }
    @Override public boolean alkahest$copyingDrop() { return alkahest$copying; }
    @Override public void alkahest$setCopyingDrop(boolean value) { alkahest$copying = value; }

    @Inject(method = "drop", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V"))
    private void alkahest$beginMidasLoot(ServerWorld world, DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity)(Object)this;
        alkahest$duplicate = victim instanceof MobEntity
            && source.getAttacker() instanceof PlayerEntity player
            && player.hasStatusEffect(ModEffects.MIDAS_TOUCH)
            && victim.getRandom().nextBoolean();
    }

    // Do not duplicate a mob's carried container inventory (e.g. a donkey chest).
    @Inject(method = "drop", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;dropEquipment(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V",
        shift = At.Shift.AFTER))
    private void alkahest$endMidasLoot(ServerWorld world, DamageSource source, CallbackInfo ci) {
        alkahest$duplicate = false;
    }

    @Inject(method = "drop", at = @At("RETURN"))
    private void alkahest$clearMidasLoot(ServerWorld world, DamageSource source, CallbackInfo ci) {
        alkahest$duplicate = false;
        alkahest$copying = false;
    }
}
