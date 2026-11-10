package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.ElementalEntity;
import net.alkahest.essence.EssenceType;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Full-bright emissive overlay for Elemental eyes, inner energy core, runes and salt shackles.
 */
public final class ElementalGlowFeatureRenderer extends FeatureRenderer<ElementalEntity, ElementalEntityModel> {
    private static final Map<EssenceType, Identifier> GLOW_TEXTURES = createGlowMap();

    private static Map<EssenceType, Identifier> createGlowMap() {
        Map<EssenceType, Identifier> map = new EnumMap<>(EssenceType.class);
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() == EssenceType.Tier.PRIMA) {
                map.put(type, Alkahest.id("textures/entity/elemental/" + type.asString() + "_glow.png"));
            }
        }
        return Map.copyOf(map);
    }

    public ElementalGlowFeatureRenderer(FeatureRendererContext<ElementalEntity, ElementalEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       ElementalEntity entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (entity.isInvisible()) return;
        Identifier glow = GLOW_TEXTURES.getOrDefault(entity.essenceType(), GLOW_TEXTURES.get(EssenceType.IGNIS));
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(glow));
        getContextModel().render(
            matrices,
            consumer,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            OverlayTexture.DEFAULT_UV,
            0xFFFFFFFF
        );
    }
}
