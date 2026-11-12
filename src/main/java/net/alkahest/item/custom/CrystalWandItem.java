package net.alkahest.item.custom;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.item.ItemUsageContext;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;

/** The basic wand currently activates the cauldron; other wand powers are not implemented yet. */
public class CrystalWandItem extends Item {
    private final EssenceType type;
    public CrystalWandItem(EssenceType type) {
        super(new Settings().maxCount(1).component(ModDataComponents.ESSENCE_CHARGE, 100));
        this.type = type;
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        // Sneak-use configures the optional filter without consuming a charge.
        var player = context.getPlayer();
        if (player != null && player.isSneaking() && context.getWorld().getBlockEntity(context.getBlockPos()) instanceof AlchemyPipeBlockEntity pipe) {
            if (!context.getWorld().isClient) pipe.cycleFilter(player);
            return ActionResult.success(context.getWorld().isClient);
        }
        return super.useOnBlock(context);
    }
    public EssenceType essenceType() { return type; }
    public void consumeUse(PlayerEntity player, Hand hand) {
        if (player.isCreative()) return;
        ItemStack stack = player.getStackInHand(hand);
        int charge = stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0);
        if (charge <= 1) player.setStackInHand(hand, new ItemStack(Items.STICK));
        else stack.set(ModDataComponents.ESSENCE_CHARGE, charge - 1);
    }
}
