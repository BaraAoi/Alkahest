package net.alkahest.mixin;

import net.alkahest.research.PracticalResearchStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe actual successful inventory insertion immediately, including pickups and quick transfers. */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryResearchMixin {
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"))
    private void alkahest$observeInserted(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && player instanceof ServerPlayerEntity serverPlayer)
            PracticalResearchStorage.scan(serverPlayer);
    }
}
