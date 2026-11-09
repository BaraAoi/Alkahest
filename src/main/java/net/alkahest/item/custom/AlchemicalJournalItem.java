package net.alkahest.item.custom;

import net.alkahest.network.OpenJournalPayload;
import net.alkahest.research.EarlyResearch;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Opens the guide and the first, server-persisted research. */
public class AlchemicalJournalItem extends Item {
    public AlchemicalJournalItem(Settings settings) { super(settings); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity player) {
            EarlyResearch.openJournal(player);
            ServerPlayNetworking.send(player, new OpenJournalPayload(EarlyResearch.flags(player)));
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }
}
