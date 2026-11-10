package net.alkahest;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModItems;
import net.alkahest.network.OpenJournalPayload;
import net.alkahest.client.gui.JournalScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.alkahest.registry.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
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
        ClientPlayNetworking.registerGlobalReceiver(OpenJournalPayload.ID, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new JournalScreen(payload.flags()))));
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.NAPALM_FLAME, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.JAR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ALCHEMY_PIPE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ESSENCE_SEPARATOR, RenderLayer.getCutout());
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(ModBlocks.JAR.asItem(), Alkahest.id("fill"),
            (stack, world, entity, seed) -> net.alkahest.essence.JarVisuals.level(net.alkahest.item.custom.JarItem.contents(stack)) / 10.0f);
        ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 0
            ? net.alkahest.essence.JarVisuals.color(net.alkahest.item.custom.JarItem.contents(stack)) : 0xffffff, ModBlocks.JAR);
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> tint == 0 && world != null && pos != null
            && world.getBlockEntity(pos) instanceof net.alkahest.block.entity.JarBlockEntity jar
                ? net.alkahest.essence.JarVisuals.color(jar.contents()) : 0xffffff, ModBlocks.JAR);
        EntityRendererRegistry.register(ModEntities.THROWN_REAGENT, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.REAGENT_ZONE, EmptyEntityRenderer::new);
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) ->
            state.get(AlchemyCauldronBlock.DIRTY) ? 0x74552B :
                world != null && pos != null ? BiomeColors.getWaterColor(world, pos) : 0x3F76E4,
            ModBlocks.ALCHEMY_CAULDRON);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> LoggerFactory.getLogger("Alkahest").info("Alkahest client started"));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.getItem() instanceof CrystalWandItem) lines.add(Text.translatable("message.alkahest.wand_charge", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0)));
            if (stack.getItem() instanceof net.alkahest.item.custom.JarItem) {
                var contents = net.alkahest.item.custom.JarItem.contents(stack);
                lines.add(Text.translatable("message.alkahest.jar_total", contents.total()));
                if (!net.alkahest.item.custom.JarItem.singleType(contents)) lines.add(Text.translatable("message.alkahest.jar_legacy").formatted(net.minecraft.util.Formatting.GOLD));
                for (var essence : contents.contents()) lines.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount() + " · " + Math.round(essence.stability() * 100) + "%")
                    .styled(style -> style.withColor(essence.type().color())));
            }
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
