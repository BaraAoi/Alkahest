package net.alkahest.client.render;

import net.alkahest.entity.ElementalEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModItems;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders a self-luminous multi-crystal core with three orbital shards
 * and an antipode crystal seal cage whenever the Elemental is bound.
 */
public final class ElementalEntityRenderer extends EntityRenderer<ElementalEntity> {
    private final ItemRenderer itemRenderer;

    public ElementalEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.35f;
        this.shadowOpacity = 0.45f;
    }

    @Override
    public void render(ElementalEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        EssenceType type = entity.essenceType();
        Item coreItem = ModItems.CRYSTALS.get(type);
        if (coreItem == null) {
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        ItemStack coreStack = new ItemStack(coreItem);
        float time = entity.age + tickDelta;
        float bob = MathHelper.sin(time * 0.11f) * 0.08f;
        int fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.packUv(
            OverlayTexture.getU(0.0f),
            OverlayTexture.getV(entity.hurtTime > 0 || entity.deathTime > 0)
        );

        matrices.push();
        matrices.translate(0.0f, 0.68f + bob, 0.0f);

        // Outer luminous crystal core
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 3.2f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(time * 0.07f) * 10.0f));
        matrices.scale(1.35f, 1.35f, 1.35f);
        itemRenderer.renderItem(coreStack, ModelTransformationMode.FIXED, fullLight, overlay,
            matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();

        // Cross-plane inner crystal core for volumetric 3D silhouette
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 3.2f + 90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.cos(time * 0.07f) * 10.0f));
        matrices.scale(1.2f, 1.2f, 1.2f);
        itemRenderer.renderItem(coreStack, ModelTransformationMode.FIXED, fullLight, overlay,
            matrices, vertexConsumers, entity.getWorld(), entity.getId() + 1);
        matrices.pop();

        // Three tilted orbital shards of its own Prima essence
        for (int i = 0; i < 3; i++) {
            float angle = time * 4.8f + i * 120.0f;
            float rad = angle * MathHelper.RADIANS_PER_DEGREE;
            float ox = MathHelper.cos(rad) * 0.48f;
            float oz = MathHelper.sin(rad) * 0.48f;
            float oy = MathHelper.sin(rad + i * 1.1f) * 0.18f;

            matrices.push();
            matrices.translate(ox, oy, oz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-angle * 1.5f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25.0f * (i - 1)));
            matrices.scale(0.62f, 0.62f, 0.62f);
            itemRenderer.renderItem(coreStack, ModelTransformationMode.FIXED, fullLight, overlay,
                matrices, vertexConsumers, entity.getWorld(), entity.getId() + 10 + i);
            matrices.pop();
        }

        // Antipode crystal cage while bound against evaporation
        if (entity.getBoundTicks() > 0 && type.opposite() != null) {
            Item oppositeItem = ModItems.CRYSTALS.get(type.opposite());
            if (oppositeItem != null) {
                ItemStack sealStack = new ItemStack(oppositeItem);
                for (int i = 0; i < 4; i++) {
                    float sealAngle = -time * 6.5f + i * 90.0f;
                    float rad = sealAngle * MathHelper.RADIANS_PER_DEGREE;
                    float sx = MathHelper.cos(rad) * 0.62f;
                    float sz = MathHelper.sin(rad) * 0.62f;
                    float sy = (i % 2 == 0 ? 0.22f : -0.22f) * MathHelper.cos(time * 0.15f);

                    matrices.push();
                    matrices.translate(sx, sy, sz);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sealAngle + 90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0f));
                    matrices.scale(0.58f, 0.58f, 0.58f);
                    itemRenderer.renderItem(sealStack, ModelTransformationMode.FIXED, fullLight, overlay,
                        matrices, vertexConsumers, entity.getWorld(), entity.getId() + 30 + i);
                    matrices.pop();
                }
            }
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, fullLight);
    }

    @Override
    public Identifier getTexture(ElementalEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
