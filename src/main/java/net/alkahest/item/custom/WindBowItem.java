package net.alkahest.item.custom;

import net.alkahest.entity.WindGustEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import java.util.List;

/** An Aer-powered bow creates its own gusts; it never consumes or requires arrows. */
public final class WindBowItem extends Item {
    public static final int MAX_CHARGE=100, DRAW_TICKS=20, MAX_USE=72000;
    public WindBowItem(Settings settings) {
        super(settings.maxCount(1).component(ModDataComponents.ESSENCE_CHARGE,0)
            .component(ModDataComponents.WIND_BOW_SHOT_STEP,0));
    }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack=user.getStackInHand(hand);
        if (!user.isCreative() && stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE,0)<=0)
            return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }
    @Override public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient || !(user instanceof PlayerEntity player) || !player.isAlive() || player.isSpectator()) return;
        int drawn=MAX_USE-remainingUseTicks;
        if (drawn<5 || (!player.isCreative() && stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE,0)<=0)) return;
        float power=Math.min(1f,drawn/(float)DRAW_TICKS);
        var gust=new WindGustEntity(world,player,false);
        gust.setVelocity(player,player.getPitch(),player.getYaw(),0,1.1f+power*.5f,0);
        if (!world.spawnEntity(gust)) return;
        if (!player.isCreative()) consumeShot(stack);
        world.playSound(null,player.getBlockPos(),SoundEvents.ENTITY_BREEZE_SHOOT,SoundCategory.PLAYERS,.75f,1.1f);
    }
    /** After two successful shots exactly one Aer is consumed; partial use is saved on the bow. */
    public static void consumeShot(ItemStack stack) {
        int charge=stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE,0);
        if (charge<=0) return;
        int step=stack.getOrDefault(ModDataComponents.WIND_BOW_SHOT_STEP,0);
        if (step==0) stack.set(ModDataComponents.WIND_BOW_SHOT_STEP,1);
        else { stack.set(ModDataComponents.WIND_BOW_SHOT_STEP,0); stack.set(ModDataComponents.ESSENCE_CHARGE,charge-1); }
    }
    @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return MAX_USE; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }
    @Override public boolean isItemBarVisible(ItemStack stack) { return stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE,0)<MAX_CHARGE; }
    @Override public int getItemBarStep(ItemStack stack) { return Math.round(stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE,0)*13f/MAX_CHARGE); }
    @Override public int getItemBarColor(ItemStack stack) { return EssenceType.AER.color(); }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.alkahest.bow_aer",stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE,0),MAX_CHARGE).formatted(Formatting.GRAY));
    }
}
