package net.alkahest;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModItems;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.color.world.BiomeColors;
import net.alkahest.registry.ModDataComponents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.LoggerFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class AlkahestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) ->
            world != null && pos != null ? BiomeColors.getWaterColor(world, pos) : 0x3F76E4,
            ModBlocks.ALCHEMY_CAULDRON);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> LoggerFactory.getLogger("Alkahest").info("Alkahest client started"));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.getItem() instanceof CrystalWandItem) lines.add(Text.translatable("message.alkahest.wand_charge", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0)));
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
