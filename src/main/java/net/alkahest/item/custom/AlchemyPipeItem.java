package net.alkahest.item.custom;

import net.alkahest.essence.EssenceType;
import net.alkahest.block.entity.PipeValveMode;
import net.alkahest.block.entity.PipeDirection;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Pipe stacks retain their optional segment filter; unconfigured pipes remain stackable. */
public final class AlchemyPipeItem extends BlockItem {
    public AlchemyPipeItem(Block block, Settings settings) { super(block, settings); }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        EssenceType filter = stack.get(ModDataComponents.PIPE_FILTER);
        if (filter == EssenceType.VACUUM) {
            tooltip.add(Text.translatable("tooltip.alkahest.pipe_invalid_filter").formatted(Formatting.RED));
        } else if (filter != null) {
            tooltip.add(Text.translatable("message.alkahest.pipe_filter",
                Text.literal(filter.displayName()).styled(style -> style.withColor(filter.color()))).formatted(Formatting.GRAY));
        }
        PipeValveMode mode = stack.getOrDefault(ModDataComponents.PIPE_VALVE, PipeValveMode.OPEN);
        if (mode != PipeValveMode.OPEN)
            tooltip.add(Text.translatable("message.alkahest.pipe_valve_item", mode.displayText()).formatted(Formatting.GRAY));
        PipeDirection direction = stack.getOrDefault(ModDataComponents.PIPE_DIRECTION, PipeDirection.AUTO);
        if (direction != PipeDirection.AUTO)
            tooltip.add(Text.translatable("message.alkahest.pipe_direction_status", direction.displayText()).formatted(Formatting.GRAY));
    }
}
