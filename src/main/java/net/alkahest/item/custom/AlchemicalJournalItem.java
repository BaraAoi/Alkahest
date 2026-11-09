package net.alkahest.item.custom;

import net.alkahest.network.OpenJournalPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** An early in-game guide. Research and hypothesis progression are not yet implemented. */
public class AlchemicalJournalItem extends Item {
    public AlchemicalJournalItem(Settings settings) { super(settings); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity player) ServerPlayNetworking.send(player, new OpenJournalPayload());
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }
}
