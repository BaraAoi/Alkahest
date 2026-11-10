package net.alkahest.item.custom;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Base and Prima-charged Binding Salt.
 * Base salt can be charged in inventory (RMB with a Prima crystal) or on a crafting table;
 * throwing applies Crystallized (-70% speed for 15s) and binds opposite-element Elementals.
 */
public final class BindingSaltItem extends ReagentItem {
    private final EssenceType essenceType;

    public BindingSaltItem(EssenceType essenceType, Settings settings) {
        super(settings);
        this.essenceType = essenceType;
    }

    public EssenceType essenceType() {
        return essenceType;
    }

    public static Item chargedResult(ItemStack baseSalt, ItemStack crystalStack) {
        if (!baseSalt.isOf(ModItems.BINDING_SALT) || crystalStack.isEmpty()) return null;
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() != EssenceType.Tier.PRIMA) continue;
            Item crystal = ModItems.CRYSTALS.get(type);
            if (crystal != null && crystalStack.isOf(crystal)) {
                return ModItems.CHARGED_BINDING_SALTS.get(type);
            }
        }
        return null;
    }

    private boolean tryCharge(ItemStack baseSalt, ItemStack crystalStack, Slot slot, PlayerEntity player, ClickType click) {
        if (essenceType != null || click != ClickType.RIGHT || slot.inventory != player.getInventory() || !slot.canTakeItems(player)) {
            return false;
        }
        Item charged = chargedResult(baseSalt, crystalStack);
        if (charged == null || baseSalt.isEmpty() || crystalStack.isEmpty()) return false;
        baseSalt.decrement(1);
        crystalStack.decrement(1);
        slot.markDirty();
        ItemStack result = new ItemStack(charged);
        if (!player.giveItemStack(result) && !player.getWorld().isClient) {
            player.dropItem(result, false);
        }
        if (!player.getWorld().isClient) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.25f);
        }
        return true;
    }

    @Override
    public boolean onStackClicked(ItemStack stack, Slot slot, ClickType click, PlayerEntity player) {
        return tryCharge(stack, slot.getStack(), slot, player, click) || super.onStackClicked(stack, slot, click, player);
    }

    @Override
    public boolean onClicked(ItemStack stack, ItemStack cursor, Slot slot, ClickType click,
                             PlayerEntity player, StackReference cursorStackReference) {
        return tryCharge(stack, cursor, slot, player, click) || super.onClicked(stack, cursor, slot, click, player, cursorStackReference);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (essenceType == null) {
            tooltip.add(Text.translatable("tooltip.alkahest.binding_salt.base").formatted(Formatting.GRAY));
        } else {
            EssenceType opposite = essenceType.opposite();
            if (opposite != null) {
                tooltip.add(Text.translatable("tooltip.alkahest.binding_salt.charged",
                        essenceType.displayName(), opposite.displayName())
                    .styled(style -> style.withColor(essenceType.color())));
            }
        }
    }
}
