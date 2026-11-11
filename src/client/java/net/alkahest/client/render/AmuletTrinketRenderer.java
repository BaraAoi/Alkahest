package net.alkahest.client.render;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.client.TrinketRenderer;
import net.alkahest.Alkahest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Necklace is attached to the animated torso; only the focus is shared with the machine. */
public final class AmuletTrinketRenderer implements TrinketRenderer {
    private static final Identifier CORD = Alkahest.id("textures/item/amulet_gold_3d.png");

    @Override public void render(ItemStack stack, SlotReference slot, EntityModel<? extends LivingEntity> contextModel,
                                 MatrixStack matrices, VertexConsumerProvider vertices, int light, LivingEntity entity,
                                 float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                                 float headYaw, float headPitch) {
        if (!(contextModel instanceof BipedEntityModel<?> biped) || entity.isInvisible()) return;
        matrices.push();
        // Use the real animated body part, including sneaking and swimming, rather than the head.
        biped.body.rotate(matrices);
        float front = entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty() ? -.16f : -.285f;
        VertexConsumer cord = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(CORD));
        MatrixStack.Entry entry = matrices.peek();
        strap(cord, entry, -.185f, .015f, -.025f, .29f, front, light);
        strap(cord, entry, .185f, .015f, .025f, .29f, front, light);
        // Switch layers only after both cord strips have been completely emitted.
        matrices.translate(0, .31f, front - .025f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        matrices.scale(.30f, .30f, .30f);
        ItemRenderer renderer = MinecraftClient.getInstance().getItemRenderer();
        var model = renderer.getModel(stack, entity.getWorld(), entity, 0);
        renderer.renderItem(stack, ModelTransformationMode.FIXED, false, matrices, vertices,
            light, OverlayTexture.DEFAULT_UV, model);
        matrices.pop();
    }

    private static void strap(VertexConsumer out, MatrixStack.Entry entry, float x0, float y0,
                              float x1, float y1, float z, int light) {
        float width = .012f;
        vertex(out,entry,x0-width,y0,z,0,0,light);
        vertex(out,entry,x1-width,y1,z,0,1,light);
        vertex(out,entry,x1+width,y1,z,1,1,light);
        vertex(out,entry,x0+width,y0,z,1,0,light);
    }

    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, int light) {
        out.vertex(entry.getPositionMatrix(),x,y,z).color(255,255,255,255).texture(u,v)
            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,0,0,-1);
    }
}
