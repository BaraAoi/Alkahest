package net.alkahest.item.custom;

import net.alkahest.essence.EssenceType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

/** The source is only informational; the material has its own persistent item ID. */
public final class InfusedMaterialItem extends Item {
    private final EssenceType essence;
    public InfusedMaterialItem(EssenceType essence, Settings settings) {
        super(settings);
        this.essence = essence;
    }
    public static Item source(EssenceType type) {
        return switch (type) {
            case IGNIS -> Items.BLAZE_POWDER;
            case AQUA -> Items.LAPIS_LAZULI;
            case TERRA -> Items.IRON_INGOT;
            case AER -> Items.DIAMOND;
            case ORDO -> Items.GOLD_INGOT;
            case PERDITIO -> Items.AMETHYST_SHARD;
            default -> throw new IllegalArgumentException("Not a primal essence: " + type);
        };
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.alkahest.infused_material", new ItemStack(source(essence)).getName(), essence.displayName()).formatted(Formatting.GRAY));
    }
}
