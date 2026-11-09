package net.alkahest;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModDataComponents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class AlkahestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Float stability = stack.get(ModDataComponents.ESSENCE_STABILITY);
            if (stability != null) lines.add(Text.translatable("message.alkahest.stability", Math.round(stability * 100)));
            var player = MinecraftClient.getInstance().player;
            if (player == null || !TrinketsApi.getTrinketComponent(player)
                .map(component -> component.isEquipped(ModItems.ALCHEMISTS_MONOCLE)).orElse(false)) return;
            for (var essence : ItemEssenceMap.get(stack)) {
                lines.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount())
                    .styled(style -> style.withColor(essence.type().color())));
            }
        });
    }
}
