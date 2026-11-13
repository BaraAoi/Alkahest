package net.alkahest.item.custom;

import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

/** Portable bellows or blocked tier-II core: show preserved contents without a monocle. */
public final class DistilleryStorageItem extends BlockItem {
    public DistilleryStorageItem(Block block, Settings settings) { super(block, settings); }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (stack.getOrDefault(ModDataComponents.DISTILLERY_REDSTONE, false))
            tooltip.add(Text.translatable("tooltip.alkahest.distillery_redstone").formatted(Formatting.GRAY));
        var contents = stack.getOrDefault(ModDataComponents.DISTILLERY_CONTENTS, JarContentsComponent.EMPTY);
        if (contents.total() == 0) return;
        tooltip.add(Text.translatable("tooltip.alkahest.distillery_saved_contents").formatted(Formatting.GRAY));
        for (var essence : contents.contents())
            tooltip.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount() + " · " + Math.round(essence.stability() * 100) + "%")
                .styled(style -> style.withColor(essence.type().color())));
    }
}
