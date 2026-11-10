package net.alkahest.client.render;

import net.alkahest.block.entity.MatrixPedestalBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/** Shows the item standing on a pedestal, slowly spinning and bobbing above the plinth. */
public final class MatrixPedestalRenderer implements BlockEntityRenderer<MatrixPedestalBlockEntity> {
    private final ItemRenderer itemRenderer;

    public MatrixPedestalRenderer(BlockEntityRendererFactory.Context context) {
        this.itemRenderer = MinecraftClient.getInstance().getItemRenderer();
    }

    @Override public void render(MatrixPedestalBlockEntity entity, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack held = entity.held();
        if (held.isEmpty()) return;
        World world = entity.getWorld();
        float time = world == null ? 0 : (world.getTime() % 100000L) + tickDelta;
        float bob = (float) Math.sin(time * 0.08) * 0.05f;

        matrices.push();
        matrices.translate(0.5, 1.15 + bob, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 1.5f));
        matrices.scale(0.75f, 0.75f, 0.75f);
        BakedModel model = itemRenderer.getModel(held, world, null, 0);
        itemRenderer.renderItem(held, ModelTransformationMode.GROUND, false, matrices, vertexConsumers,
            light, OverlayTexture.DEFAULT_UV, model);
        matrices.pop();
    }

    @Override public boolean rendersOutsideBoundingBox(MatrixPedestalBlockEntity entity) { return true; }
}
