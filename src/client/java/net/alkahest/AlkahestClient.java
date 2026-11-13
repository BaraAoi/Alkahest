package net.alkahest;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.client.gui.JournalScreen;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.item.custom.AlkahestVialItem;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.network.OpenJournalPayload;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.LoggerFactory;

public final class AlkahestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        dev.emi.trinkets.api.client.TrinketRendererRegistry.registerRenderer(
            ModItems.ALKAHEST_AMULET, new net.alkahest.client.render.AmuletTrinketRenderer());
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(net.alkahest.registry.ModScreenHandlers.DISTILLERY,
            net.alkahest.client.gui.DistilleryScreen::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(net.alkahest.registry.ModScreenHandlers.TIER2_DISTILLERY,
            net.alkahest.client.gui.Tier2DistilleryScreen::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(net.alkahest.registry.ModScreenHandlers.TRANSMUTATOR,
            net.alkahest.client.gui.TransmutatorScreen::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            net.alkahest.registry.ModBlockEntities.TRANSMUTATOR_CORE, net.alkahest.client.render.TransmutatorCoreRenderer::new);
        net.alkahest.client.gui.AtmosphereHud.register();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            net.alkahest.client.gui.AtmosphereHud::tick);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, tickCounter) -> {
            net.alkahest.client.gui.ShieldHud.render(context);
            net.alkahest.client.gui.MonocleHud.render(context);
            net.alkahest.client.gui.AtmosphereHud.render(context);
        });
        ClientPlayNetworking.registerGlobalReceiver(net.alkahest.network.AtmospherePayload.ID, (payload, context) ->
            context.client().execute(() -> net.alkahest.client.gui.AtmosphereHud.receive(payload.data())));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            net.alkahest.client.assembly.AssemblyPreview::tick);
        ClientPlayNetworking.registerGlobalReceiver(net.alkahest.network.AssemblyPreviewPayload.ID, (payload, context) ->
            context.client().execute(() -> net.alkahest.client.assembly.AssemblyPreview.open(payload.pos())));
        ClientPlayNetworking.registerGlobalReceiver(net.alkahest.network.ShieldPayload.ID, (payload, context) ->
            context.client().execute(() -> net.alkahest.client.gui.ShieldHud.receive(payload.points(), payload.max(), payload.charge())));
        ClientPlayNetworking.registerGlobalReceiver(OpenJournalPayload.ID, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new JournalScreen(payload.flags()))));
        ClientPlayNetworking.registerGlobalReceiver(net.alkahest.network.EssenceMapPayload.ID, (payload, context) ->
            context.client().execute(() -> net.alkahest.essence.RecipeEssenceService.receive(payload.chunk())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            net.alkahest.client.gui.ShieldHud.receive(-1, 0, 0);
            net.alkahest.essence.RecipeEssenceService.clearClient();
            net.alkahest.client.gui.AtmosphereHud.clear();
            net.alkahest.client.assembly.AssemblyPreview.clear();
        });
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.NAPALM_FLAME, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ALCHEMICAL_MOSS, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.JAR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ALCHEMY_PIPE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ESSENCE_SEPARATOR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.STABILIZER_RECEIVER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DISTILLERY_TIER2, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DISTILLERY_BELLOWS, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TRANSMUTATOR_PILLAR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TRANSMUTATOR_CHAMBER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TRANSMUTATOR_CROWN, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TRANSMUTATOR_CORE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MATRIX_CORE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MATRIX_PILLAR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MATRIX_PEDESTAL, RenderLayer.getCutout());
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            net.alkahest.registry.ModBlockEntities.MATRIX_CORE, net.alkahest.client.render.MatrixCoreRenderer::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
            net.alkahest.registry.ModBlockEntities.MATRIX_PEDESTAL, net.alkahest.client.render.MatrixPedestalRenderer::new);
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(ModBlocks.JAR.asItem(), Alkahest.id("fill"),
            (stack, world, entity, seed) -> net.alkahest.essence.JarVisuals.level(net.alkahest.item.custom.JarItem.contents(stack)) / 10.0f);
        ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 0
            ? net.alkahest.essence.JarVisuals.color(net.alkahest.item.custom.JarItem.contents(stack)) : 0xffffff, ModBlocks.JAR);
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> tint == 0 && world != null && pos != null
            && world.getBlockEntity(pos) instanceof net.alkahest.block.entity.JarBlockEntity jar
                ? net.alkahest.essence.JarVisuals.color(jar.contents()) : 0xffffff, ModBlocks.JAR);
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> tint == 0 && world != null && pos != null
            && world.getBlockEntity(pos) instanceof net.alkahest.block.entity.StabilizerReceiverBlockEntity receiver
                ? net.alkahest.essence.JarVisuals.color(receiver.contents()) : 0xffffff, ModBlocks.STABILIZER_RECEIVER);
        // Pipes glow in the colour of the essence they are moving right now.
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> tint == 0 && world != null && pos != null
            && world.getBlockEntity(pos) instanceof net.alkahest.block.entity.AlchemyPipeBlockEntity pipe
                && pipe.flowing() != null ? pipe.flowing().color() : 0xffffff, ModBlocks.ALCHEMY_PIPE);
        // The bellows are glass flasks: the stored essence colours the liquid inside.
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> tint == 0 && world != null && pos != null
            && world.getBlockEntity(pos) instanceof net.alkahest.block.entity.DistilleryBellowsBlockEntity bellows
                && !bellows.empty() ? bellows.storedType().color() : 0xffffff, ModBlocks.DISTILLERY_BELLOWS);
        EntityRendererRegistry.register(ModEntities.THROWN_REAGENT, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.REAGENT_ZONE, EmptyEntityRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(
            net.alkahest.client.render.ElementalEntityModel.LAYER,
            net.alkahest.client.render.ElementalEntityModel::getTexturedModelData
        );
        EntityModelLayerRegistry.registerModelLayer(
            net.alkahest.client.render.ElementalBoltEntityRenderer.LAYER,
            net.alkahest.client.render.ElementalBoltEntityRenderer::getTexturedModelData
        );
        EntityModelLayerRegistry.registerModelLayer(
            net.alkahest.client.render.DissolvedEntityModel.LAYER,
            net.alkahest.client.render.DissolvedEntityModel::getTexturedModelData
        );
        EntityRendererRegistry.register(ModEntities.ELEMENTAL_BOLT, net.alkahest.client.render.ElementalBoltEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DISSOLVED, net.alkahest.client.render.DissolvedEntityRenderer::new);
        for (var elementalType : ModEntities.ELEMENTALS.values()) {
            EntityRendererRegistry.register(elementalType, net.alkahest.client.render.ElementalEntityRenderer::new);
        }
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> {
            if (state.get(AlchemyCauldronBlock.DIRTY)) return 0x74552B;
            if (world != null && pos != null && world.getBlockEntity(pos) instanceof net.alkahest.block.entity.CauldronBlockEntity cauldron) {
                int color = cauldron.essenceColor();
                if (color >= 0) return color;
            }
            return world != null && pos != null ? BiomeColors.getWaterColor(world, pos) : 0x3F76E4;
        }, ModBlocks.ALCHEMY_CAULDRON);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> LoggerFactory.getLogger("Alkahest").info("Alkahest client started"));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.getItem() instanceof CrystalWandItem) {
                lines.add(Text.translatable("message.alkahest.wand_charge", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0)));
            }
            if (stack.getItem() instanceof AlkahestAmuletItem) {
                lines.add(Text.translatable("message.alkahest.amulet_charge", stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0))
                    .formatted(Formatting.GOLD));
            }
            if (stack.getItem() instanceof AlkahestVialItem) {
                lines.add(Text.translatable("message.alkahest.solvent_charges", AlkahestVialItem.charges(stack), AlkahestVialItem.MAX_CHARGES)
                    .formatted(Formatting.LIGHT_PURPLE));
            }
            if (stack.getItem() instanceof net.alkahest.item.custom.JarItem) {
                var contents = net.alkahest.item.custom.JarItem.contents(stack);
                lines.add(Text.translatable("message.alkahest.jar_total", contents.total()));
                if (!net.alkahest.item.custom.JarItem.singleType(contents)) lines.add(Text.translatable("message.alkahest.jar_legacy").formatted(Formatting.GOLD));
                for (var essence : contents.contents()) lines.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount() + " · " + Math.round(essence.stability() * 100) + "%")
                    .styled(style -> style.withColor(essence.type().color())));
            }
            Float stability = stack.get(ModDataComponents.ESSENCE_STABILITY);
            if (stability != null) lines.add(Text.translatable("message.alkahest.stability", Math.round(stability * 100)));
            var player = MinecraftClient.getInstance().player;
            if (player == null || !TrinketsApi.getTrinketComponent(player)
                .map(component -> component.isEquipped(ModItems.ALCHEMISTS_MONOCLE)).orElse(false)) return;
            for (var essence : ItemEssenceMap.get(stack, MinecraftClient.getInstance().world)) {
                lines.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount())
                    .styled(style -> style.withColor(essence.type().color())));
            }
        });
    }
}
