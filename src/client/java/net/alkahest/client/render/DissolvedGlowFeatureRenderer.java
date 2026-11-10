package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.DissolvedEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class DissolvedGlowFeatureRenderer extends FeatureRenderer<DissolvedEntity, DissolvedEntityModel> {
    private static final Identifier GLOW = Alkahest.id("textures/entity/dissolved/dissolved_glow.png");

    public DissolvedGlowFeatureRenderer(FeatureRendererContext<DissolvedEntity, DissolvedEntityModel> context) {
        super(context);
    }

    @Override
    public void render(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        DissolvedEntity entity,
        float limbAngle,
        float limbDistance,
        float tickDelta,
        float animationProgress,
        float headYaw,
        float headPitch
    ) {
        if (entity.isInvisible()) return;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW));
        getContextModel().render(
            matrices,
            consumer,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            OverlayTexture.DEFAULT_UV,
            0xFFFFFFFF
        );
    }
}
