package net.alkahest.item.custom;

import net.alkahest.registry.ModDataComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AlkahestVialItem extends Item {
    public static final int MAX_CHARGES = 32;

    public AlkahestVialItem(Settings settings) {
        super(settings.maxCount(1).component(ModDataComponents.SOLVENT_CHARGES, MAX_CHARGES));
    }

    public static int charges(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOLVENT_CHARGES, 0);
    }

    /** Consumes one distillation charge and returns the updated stack (or an empty glass bottle at 0). */
    public static ItemStack consumeCharge(ItemStack stack) {
        int remaining = charges(stack) - 1;
        if (remaining <= 0) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        ItemStack next = stack.copy();
        next.set(ModDataComponents.SOLVENT_CHARGES, remaining);
        return next;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return charges(stack) < MAX_CHARGES;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(charges(stack) * 13.0f / MAX_CHARGES);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xA855F7;
    }
}
