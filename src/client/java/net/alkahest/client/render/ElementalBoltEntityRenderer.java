package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.ElementalBoltEntity;
import net.alkahest.essence.EssenceType;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the spinning 3D crystalline bolt fired by Elementals.
 */
public final class ElementalBoltEntityRenderer extends EntityRenderer<ElementalBoltEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Alkahest.id("elemental_bolt"), "main");
    private static final Map<EssenceType, Identifier> TEXTURES = createTextureMap();

    private final ModelPart core;
    private final ModelPart shell;

    private static Map<EssenceType, Identifier> createTextureMap() {
        Map<EssenceType, Identifier> map = new EnumMap<>(EssenceType.class);
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() == EssenceType.Tier.PRIMA) {
                map.put(type, Alkahest.id("textures/entity/elemental/bolt_" + type.asString() + ".png"));
            }
        }
        return Map.copyOf(map);
    }

    public ElementalBoltEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        ModelPart root = context.getPart(LAYER);
        this.core = root.getChild("core");
        this.shell = root.getChild("shell");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("core",
            ModelPartBuilder.create().uv(0, 0).cuboid(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
            ModelTransform.NONE);
        root.addChild("shell",
            ModelPartBuilder.create().uv(0, 8).cuboid(-3.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f)
                .uv(0, 12).cuboid(-1.0f, -3.0f, -1.0f, 2.0f, 6.0f, 2.0f),
            ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(ElementalBoltEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float t = entity.age + tickDelta;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(entity)));

        matrices.push();
        matrices.translate(0.0f, 0.18f, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 22.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 16.0f));
        this.core.render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-t * 28.0f));
        this.shell.render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    @Override
    public Identifier getTexture(ElementalBoltEntity entity) {
        return TEXTURES.getOrDefault(entity.essenceType(), TEXTURES.get(EssenceType.IGNIS));
    }
}
