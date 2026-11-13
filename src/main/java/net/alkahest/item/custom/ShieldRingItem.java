package net.alkahest.item.custom;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.essence.EssenceType;
import net.alkahest.network.ShieldPayload;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

/** A separate six-HP barrier, not vanilla absorption and not natural regeneration. */
public final class ShieldRingItem extends TrinketItem {
    public static final float MAX_SHIELD = 6f;
    public static final int MAX_CHARGE = 100, DAMAGE_DELAY = 120, REGEN_INTERVAL = 10;
    public ShieldRingItem(Settings settings) {
        super(settings.maxCount(1).component(ModDataComponents.ESSENCE_CHARGE, 0)
            .component(ModDataComponents.SHIELD_POINTS, 0f).component(ModDataComponents.SHIELD_DELAY, 0)
            .component(ModDataComponents.SHIELD_REGEN_TICKS, 0));
    }
    /** Only one shield ring may be worn, so two rings cannot silently double the barrier. */
    @Override public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        return TrinketsApi.getTrinketComponent(entity).map(c -> !c.isEquipped(ModItems.RING_SHIELD)).orElse(false);
    }
    @Override public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (!entity.getWorld().isClient) send(entity, points(stack), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
    }
    @Override public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (!entity.getWorld().isClient) send(entity, -1, 0);
    }
    @Override public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player) || !player.isAlive() || player.isSpectator()) return;
        int delay = stack.getOrDefault(ModDataComponents.SHIELD_DELAY, 0);
        if (delay < DAMAGE_DELAY) {
            stack.set(ModDataComponents.SHIELD_DELAY, delay + 1);
        } else if (points(stack) < MAX_SHIELD && stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) > 0) {
            int elapsed = stack.getOrDefault(ModDataComponents.SHIELD_REGEN_TICKS, 0) + 1;
            if (elapsed >= REGEN_INTERVAL) {
                stack.set(ModDataComponents.SHIELD_POINTS, Math.min(MAX_SHIELD, points(stack) + 1f));
                stack.set(ModDataComponents.ESSENCE_CHARGE, stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) - 1);
                elapsed = 0;
                send(player, points(stack), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
            }
            stack.set(ModDataComponents.SHIELD_REGEN_TICKS, elapsed);
        } else if (stack.getOrDefault(ModDataComponents.SHIELD_REGEN_TICKS, 0) != 0) {
            stack.set(ModDataComponents.SHIELD_REGEN_TICKS, 0);
        }
        // Periodic correction handles reconnects and tracking changes without sending every tick.
        if (player.age % 20 == 0) send(player, points(stack), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
    }
    public static float points(ItemStack stack) { return stack.getOrDefault(ModDataComponents.SHIELD_POINTS, 0f); }
    /** Called after armor/resistance, before vanilla absorption; no effect on other absorption sources. */
    public static float absorb(PlayerEntity player, float amount) {
        if (player.getWorld().isClient || player.isSpectator() || amount <= 0) return amount;
        var equipped = TrinketsApi.getTrinketComponent(player).map(c -> c.getEquipped(ModItems.RING_SHIELD)).orElse(List.of());
        if (equipped.isEmpty()) return amount;
        ItemStack stack = equipped.getFirst().getRight();
        stack.set(ModDataComponents.SHIELD_DELAY, 0);
        stack.set(ModDataComponents.SHIELD_REGEN_TICKS, 0);
        float absorbed = Math.min(amount, points(stack));
        stack.set(ModDataComponents.SHIELD_POINTS, points(stack) - absorbed);
        send(player, points(stack), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
        return amount - absorbed;
    }
    private static void send(LivingEntity entity, float points, int charge) {
        if (entity instanceof ServerPlayerEntity player && ServerPlayNetworking.canSend(player, ShieldPayload.ID))
            ServerPlayNetworking.send(player, new ShieldPayload(points, charge));
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.alkahest.ring_shield", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0), MAX_CHARGE).formatted(Formatting.GRAY));
    }
    @Override public boolean isItemBarVisible(ItemStack stack) { return stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) < MAX_CHARGE; }
    @Override public int getItemBarStep(ItemStack stack) { return Math.round(stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) * 13f / MAX_CHARGE); }
    @Override public int getItemBarColor(ItemStack stack) { return EssenceType.ORDO.color(); }
}
