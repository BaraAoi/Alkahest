package net.alkahest.item.custom;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.essence.EssenceType;
import net.alkahest.network.ShieldPayload;
import net.alkahest.network.ShieldVisualPayload;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
    public static final int MAX_CHARGE = 100, REGEN_INTERVAL = 10;
    private final int level;
    public ShieldRingItem(Settings settings, int level) {
        super(settings.maxCount(1).component(ModDataComponents.ESSENCE_CHARGE, 0)
            .component(ModDataComponents.SHIELD_POINTS, 0f).component(ModDataComponents.SHIELD_DELAY, 0)
            .component(ModDataComponents.SHIELD_REGEN_TICKS, 0));
        if (level < 1 || level > 4) throw new IllegalArgumentException("Invalid shield ring level");
        this.level = level;
    }
    public int level() { return level; }
    public float maxShield() { return switch (level) { case 1 -> 2f; case 2 -> 4f; default -> 6f; }; }
    public int damageDelay() { return switch (level) { case 1 -> 240; case 2 -> 200; case 3 -> 180; default -> 120; }; }
    /** Only one shield ring may be worn, so two rings cannot silently double the barrier. */
    @Override public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        return TrinketsApi.getTrinketComponent(entity).map(c -> !c.isEquipped(s -> s.getItem() instanceof ShieldRingItem)).orElse(false);
    }
    @Override public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (!entity.getWorld().isClient) send(entity, points(stack), (int)maxShield(), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
    }
    @Override public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (!entity.getWorld().isClient) {
            send(entity, -1, 0, 0);
            sendVisual(entity, false);
        }
    }
    @Override public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player) || !player.isAlive() || player.isSpectator()) return;
        // 0.1.125 rings retained 6 HP under the same ID; enforce the new first-level cap.
        float safePoints = points(stack);
        if (safePoints != stack.getOrDefault(ModDataComponents.SHIELD_POINTS, 0f)) {
            stack.set(ModDataComponents.SHIELD_POINTS, safePoints);
            send(player, safePoints, (int)maxShield(), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
        }
        int delay = stack.getOrDefault(ModDataComponents.SHIELD_DELAY, 0);
        if (delay < damageDelay()) {
            stack.set(ModDataComponents.SHIELD_DELAY, delay + 1);
        } else if (points(stack) < maxShield() && stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) > 0) {
            int elapsed = stack.getOrDefault(ModDataComponents.SHIELD_REGEN_TICKS, 0) + 1;
            if (elapsed >= REGEN_INTERVAL) {
                stack.set(ModDataComponents.SHIELD_POINTS, Math.min(maxShield(), points(stack) + 1f));
                stack.set(ModDataComponents.ESSENCE_CHARGE, stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) - 1);
                elapsed = 0;
                send(player, points(stack), (int)maxShield(), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
            }
            stack.set(ModDataComponents.SHIELD_REGEN_TICKS, elapsed);
        } else if (stack.getOrDefault(ModDataComponents.SHIELD_REGEN_TICKS, 0) != 0) {
            stack.set(ModDataComponents.SHIELD_REGEN_TICKS, 0);
        }
        // Periodic correction handles reconnects and tracking changes without sending every tick.
        if (player.age % 20 == 0) send(player, points(stack), (int)maxShield(), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
    }
    public static float points(ItemStack stack) {
        float max = stack.getItem() instanceof ShieldRingItem ring ? ring.maxShield() : 0;
        return Math.max(0, Math.min(max, stack.getOrDefault(ModDataComponents.SHIELD_POINTS, 0f)));
    }
    /** Called after armor/resistance, before vanilla absorption; no effect on other absorption sources. */
    public static float absorb(PlayerEntity player, float amount) { return absorb(player, amount, true); }
    public static float absorb(PlayerEntity player, float amount, boolean mayAbsorb) {
        if (player.getWorld().isClient || player.isSpectator() || amount <= 0) return amount;
        var equipped = TrinketsApi.getTrinketComponent(player).map(c -> c.getEquipped(s -> s.getItem() instanceof ShieldRingItem)).orElse(List.of());
        if (equipped.isEmpty()) return amount;
        ItemStack stack = equipped.getFirst().getRight();
        stack.set(ModDataComponents.SHIELD_DELAY, 0);
        stack.set(ModDataComponents.SHIELD_REGEN_TICKS, 0);
        // Fall damage bypasses the barrier, but actual damage still restarts its cooldown.
        float absorbed = mayAbsorb ? Math.min(amount, points(stack)) : 0;
        stack.set(ModDataComponents.SHIELD_POINTS, points(stack) - absorbed);
        send(player, points(stack), (int)((ShieldRingItem)stack.getItem()).maxShield(), stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0));
        // Only a hit actually absorbed by the barrier creates the short impact effect,
        // including the hit that depletes its last point. Falls never create an effect.
        if (net.alkahest.shield.ShieldImpactEffect.shouldTrigger(absorbed)) sendVisual(player, true);
        return amount - absorbed;
    }
    private static void send(LivingEntity entity, float points, int max, int charge) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (ServerPlayNetworking.canSend(player, ShieldPayload.ID))
            ServerPlayNetworking.send(player, new ShieldPayload(points, max, charge));
    }
    private static void sendVisual(LivingEntity entity, boolean impact) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        // Events go to the wearer and observers, independently of periodic HUD updates.
        var visual = new ShieldVisualPayload(player.getId(), impact);
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player))
            if (ServerPlayNetworking.canSend(viewer, ShieldVisualPayload.ID)) ServerPlayNetworking.send(viewer, visual);
        if (ServerPlayNetworking.canSend(player, ShieldVisualPayload.ID)) ServerPlayNetworking.send(player, visual);
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.alkahest.ring_shield", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0), MAX_CHARGE, (int)maxShield()).formatted(Formatting.GRAY));
    }
    @Override public boolean isItemBarVisible(ItemStack stack) { return stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) < MAX_CHARGE; }
    @Override public int getItemBarStep(ItemStack stack) { return Math.round(stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) * 13f / MAX_CHARGE); }
    @Override public int getItemBarColor(ItemStack stack) { return EssenceType.ORDO.color(); }
}
