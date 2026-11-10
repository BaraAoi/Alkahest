package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * The finished Matrix: three forged brass rings, textured with the metal plate sprite, plus the
 * small pyramids that leave their corners once the cube wakes up.
 *
 * <p>A dormant cube keeps its rings flat and stacked in a row, resting on the corner pyramids.
 * Waking it up swaps the rough shell for this halo: the rings tilt, turn at different speeds and
 * the pyramids circle the core, holding the field. Everything is drawn with the metal texture, so
 * the halo reads as cast metal rather than as glass.
 */
public final class MatrixCoreRenderer implements BlockEntityRenderer<MatrixCoreBlockEntity> {
    private static final Identifier METAL = Alkahest.id("textures/block/matrix_ring_metal.png");
    private static final int SEGMENTS = 44;
    private static final int PYRAMIDS = 8;

    public MatrixCoreRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(MatrixCoreBlockEntity entity, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) return;
        BlockState state = entity.getCachedState();
        boolean awake = state.get(MatrixCoreBlock.AWAKE);
        boolean active = state.get(MatrixCoreBlock.ACTIVE);
        float time = (world.getTime() % 100000L) + tickDelta;
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(METAL));

        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        if (!awake) {
            // Dormant: the rings lie in a row, one above another, as if on a rack.
            for (int i = 0; i < 3; i++) {
                matrices.push();
                matrices.translate(0, -0.16f + i * 0.16f, 0);
                ring(buffer, matrices.peek(), 0.42f, 0.60f, 0.045f);
                matrices.pop();
            }
        } else {
            float speed = active ? 1.8f : 0.55f;
            for (int i = 0; i < 3; i++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * speed * (i % 2 == 0 ? 1 : -1)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24f * (i + 1) - 24f
                    + (float) Math.sin(time * 0.02f + i) * 4f));
                matrices.translate(0, (i - 1) * 0.28f, 0);
                ring(buffer, matrices.peek(), 0.78f + i * 0.06f, 1.00f + i * 0.06f, 0.07f);
                matrices.pop();
            }
            float radius = 1.5f;
            for (int i = 0; i < PYRAMIDS; i++) {
                double angle = Math.toRadians(time * 2.2 + i * (360.0 / PYRAMIDS));
                float x = (float) (Math.cos(angle) * radius);
                float z = (float) (Math.sin(angle) * radius);
                float y = (float) Math.sin(angle * 2.0) * 0.32f;
                matrices.push();
                matrices.translate(x, y, z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(-angle)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(active ? 26f : 8f));
                pyramid(buffer, matrices.peek(), 0.17f, 0.34f);
                matrices.pop();
            }
        }
        matrices.pop();
    }

    @Override public boolean rendersOutsideBoundingBox(MatrixCoreBlockEntity entity) { return true; }

    @Override public int getRenderDistance() { return 160; }

    /** A real metal ring: two faces and two walls, so it has visible thickness. */
    private static void ring(VertexConsumer buffer, MatrixStack.Entry entry, float inner, float outer, float half) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * 2 * Math.PI / SEGMENTS;
            double a1 = (i + 1) * 2 * Math.PI / SEGMENTS;
            float u0 = i / (float) SEGMENTS;
            float u1 = (i + 1) / (float) SEGMENTS;
            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            float[] in0t = {cos0 * inner, half, sin0 * inner};
            float[] out0t = {cos0 * outer, half, sin0 * outer};
            float[] out1t = {cos1 * outer, half, sin1 * outer};
            float[] in1t = {cos1 * inner, half, sin1 * inner};
            float[] in0b = {cos0 * inner, -half, sin0 * inner};
            float[] out0b = {cos0 * outer, -half, sin0 * outer};
            float[] out1b = {cos1 * outer, -half, sin1 * outer};
            float[] in1b = {cos1 * inner, -half, sin1 * inner};

            // Top and bottom faces.
            quad(buffer, entry, in0t, out0t, out1t, in1t, u0, u1, 0.1f, 0.9f);
            quad(buffer, entry, in1b, out1b, out0b, in0b, u1, u0, 0.1f, 0.9f);
            // Outer and inner walls.
            quad(buffer, entry, out0b, out0t, out1t, out1b, u0, u1, 0f, 0.2f);
            quad(buffer, entry, in1b, in1t, in0t, in0b, u1, u0, 0f, 0.2f);
        }
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, float[] a, float[] b, float[] c, float[] d,
                             float u0, float u1, float v0, float v1) {
        vertex(buffer, entry, a, u0, v1);
        vertex(buffer, entry, b, u0, v0);
        vertex(buffer, entry, c, u1, v0);
        vertex(buffer, entry, d, u1, v1);
    }

    /** A small cast-metal pyramid that circles the core. */
    private static void pyramid(VertexConsumer buffer, MatrixStack.Entry entry, float half, float height) {
        float[][] base = {{-half, 0, -half}, {half, 0, -half}, {half, 0, half}, {-half, 0, half}};
        float[] apex = {0, height, 0};
        for (int i = 0; i < 4; i++) {
            float[] p0 = base[i];
            float[] p1 = base[(i + 1) % 4];
            tri(buffer, entry, p0, p1, apex);
            tri(buffer, entry, apex, p1, p0);
        }
        tri(buffer, entry, base[0], base[2], base[1]);
        tri(buffer, entry, base[0], base[3], base[2]);
    }

    private static void tri(VertexConsumer buffer, MatrixStack.Entry entry, float[] a, float[] b, float[] c) {
        vertex(buffer, entry, a, 0.3f, 0.5f);
        vertex(buffer, entry, b, 0.7f, 0.5f);
        vertex(buffer, entry, c, 0.5f, 0.95f);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float[] position, float u, float v) {
        Matrix4f matrix = entry.getPositionMatrix();
        buffer.vertex(matrix, position[0], position[1], position[2])
            .color(255, 255, 255, 255)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(entry, 0f, 1f, 0f);
    }
}
