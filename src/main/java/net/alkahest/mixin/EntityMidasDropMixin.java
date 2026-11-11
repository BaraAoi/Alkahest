package net.alkahest.mixin;

import net.alkahest.loot.MidasLootState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Copies each successfully emitted stack, including components, exactly once. */
@Mixin(Entity.class)
public abstract class EntityMidasDropMixin {
    @Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;", at = @At("RETURN"))
    private void alkahest$copyMidasDrop(ItemStack stack, float offset, CallbackInfoReturnable<ItemEntity> cir) {
        Entity self = (Entity)(Object)this;
        ItemEntity dropped = cir.getReturnValue();
        if (self.getWorld().isClient || dropped == null || dropped.getStack().isEmpty()
            || !(self instanceof MidasLootState state) || !state.alkahest$duplicateDrops() || state.alkahest$copyingDrop()) return;
        state.alkahest$setCopyingDrop(true);
        try {
            self.dropStack(dropped.getStack().copy(), offset);
        } finally {
            state.alkahest$setCopyingDrop(false);
        }
    }
}
