package net.alkahest.item.custom;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

/** A Terra-powered ring: its equipped-time counter belongs to the stack, not the player. */
public final class TerraRingItem extends TrinketItem {
    public static final int TICKS_PER_ESSENCE = 2400;
    public static final int MAX_CHARGE = 100;
    private final int level;
    public TerraRingItem(Settings settings, int level) {
        super(settings.maxCount(1).component(ModDataComponents.ESSENCE_CHARGE, 0)
            .component(ModDataComponents.RING_ACTIVE_TICKS, 0));
        this.level = level;
    }
    @Override public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player) || !player.isAlive() || player.isSpectator()) return;
        int charge = stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0);
        if (charge <= 0) return;
        // A short renewable vanilla effect avoids stripping effects granted by other sources.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 5, level - 1, true, false, true));
        int elapsed = stack.getOrDefault(ModDataComponents.RING_ACTIVE_TICKS, 0) + 1;
        if (elapsed >= TICKS_PER_ESSENCE) {
            stack.set(ModDataComponents.ESSENCE_CHARGE, charge - 1);
            elapsed = 0;
        }
        stack.set(ModDataComponents.RING_ACTIVE_TICKS, elapsed);
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.alkahest.ring_terra", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0), MAX_CHARGE).formatted(Formatting.GRAY));
    }
    @Override public boolean isItemBarVisible(ItemStack stack) { return stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) < MAX_CHARGE; }
    @Override public int getItemBarStep(ItemStack stack) { return Math.round(stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) * 13f / MAX_CHARGE); }
    @Override public int getItemBarColor(ItemStack stack) { return net.alkahest.essence.EssenceType.TERRA.color(); }
}
