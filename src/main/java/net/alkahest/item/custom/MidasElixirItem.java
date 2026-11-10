package net.alkahest.item.custom;

import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.registry.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Drinkable early elixir; the Matrix speed bonus is the first implemented Midas ability. */
public final class MidasElixirItem extends Item {
    public MidasElixirItem(Settings settings) { super(settings.maxCount(1)); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        player.setCurrentHand(hand);
        return TypedActionResult.consume(player.getStackInHand(hand));
    }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.alkahest.midas_elixir.effect").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.alkahest.midas_elixir.scope").formatted(Formatting.GRAY));
    }

    @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 32; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.DRINK; }

    @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) user.addStatusEffect(new StatusEffectInstance(ModEffects.MIDAS_TOUCH, MidasTouchEffect.DURATION));
        if (user instanceof PlayerEntity player) {
            player.incrementStat(Stats.USED.getOrCreateStat(this));
            if (player.isCreative()) return stack;
        }
        stack.decrement(1);
        if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
        if (user instanceof PlayerEntity player && !player.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE)))
            player.dropItem(new ItemStack(Items.GLASS_BOTTLE), false);
        return stack;
    }
}
