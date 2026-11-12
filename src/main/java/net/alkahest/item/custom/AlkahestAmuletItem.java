package net.alkahest.item.custom;

import dev.emi.trinkets.api.TrinketItem;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/** Tier II universal focus: wearable necklace trinket and 5x-efficiency handheld activator recharged by Ordo. */
public class AlkahestAmuletItem extends TrinketItem {
    public static final int MAX_CHARGE = 100;
    public static final int USES_PER_CHARGE = 5;

    public AlkahestAmuletItem(Settings settings) {
        super(settings.maxCount(1)
            .component(ModDataComponents.ESSENCE_CHARGE, MAX_CHARGE)
            .component(ModDataComponents.AMULET_USES_STEP, 0));
    }

    public EssenceType rechargeType() {
        return EssenceType.ORDO;
    }

    public boolean canConsumeUse(ItemStack stack, PlayerEntity player) {
        return player.isCreative() || stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) > 0;
    }

    /** Consumes 1/5th of an Ordo charge; never breaks into a stick when depleted. */
    public boolean consumeUse(PlayerEntity player, Hand hand) {
        return consumeUseStack(player, player.getStackInHand(hand));
    }

    public boolean consumeUseStack(PlayerEntity player, ItemStack stack) {
        if (player.isCreative()) {
            return true;
        }
        int charge = stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0);
        if (charge <= 0) {
            return false;
        }
        int step = stack.getOrDefault(ModDataComponents.AMULET_USES_STEP, 0) + 1;
        if (step >= USES_PER_CHARGE) {
            stack.set(ModDataComponents.AMULET_USES_STEP, 0);
            stack.set(ModDataComponents.ESSENCE_CHARGE, charge - 1);
        } else {
            stack.set(ModDataComponents.AMULET_USES_STEP, step);
        }
        return true;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        var player = context.getPlayer();
        if (player != null && player.isSneaking()
            && context.getWorld().getBlockEntity(context.getBlockPos()) instanceof AlchemyPipeBlockEntity pipe) {
            if (!context.getWorld().isClient) pipe.cycleFilter(player);
            return ActionResult.success(context.getWorld().isClient);
        }
        return super.useOnBlock(context);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0) < MAX_CHARGE;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        int charge = stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0);
        return Math.round(charge * 13.0f / MAX_CHARGE);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xF5D77A;
    }
}
