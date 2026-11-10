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
 * Draws the halo of the Matrix: three rings and, once the cube is awake and working, a swarm of
 * small pyramids orbiting the core. A dormant Matrix keeps its rings flat, stacked in a row, as
 * if the corner pyramids were holding them; an awakened one lets them turn like angelic halos.
 */
public final class MatrixCoreRenderer implements BlockEntityRenderer<MatrixCoreBlockEntity> {
    private static final Identifier TEXTURE = Alkahest.id("textures/block/matrix_ring.png");
    private static final int SEGMENTS = 40;
    private static final int PYRAMIDS = 6;

    public MatrixCoreRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(MatrixCoreBlockEntity entity, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) return;
        BlockState state = entity.getCachedState();
        boolean awake = state.get(MatrixCoreBlock.AWAKE);
        boolean active = state.get(MatrixCoreBlock.ACTIVE);
        float time = (world.getTime() % 100000L) + tickDelta;
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        if (!awake) {
            // Dormant: the rings lie flat, one above another, held by the corner pyramids.
            for (int i = 0; i < 3; i++) {
                matrices.push();
                matrices.translate(0, -0.18f + i * 0.18f, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                ring(buffer, matrices.peek(), 0.62f, 0.78f, 0.55f);
                matrices.pop();
            }
        } else {
            float speed = active ? 1.6f : 0.5f;
            for (int i = 0; i < 3; i++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * speed * (i % 2 == 0 ? 1 : -1) * 0.6f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28f + i * 26f + (float) Math.sin(time * 0.03f + i) * 6f));
                matrices.translate(0, (i - 1) * 0.12f, 0);
                ring(buffer, matrices.peek(), 0.95f, 1.22f, active ? 0.85f : 0.5f);
                matrices.pop();
            }
            if (active) {
                float radius = 1.45f;
                for (int i = 0; i < PYRAMIDS; i++) {
                    double angle = Math.toRadians(time * 2.4 + i * (360.0 / PYRAMIDS));
                    float x = (float) (Math.cos(angle) * radius);
                    float z = (float) (Math.sin(angle) * radius);
                    float y = (float) (Math.sin(angle * 2.0) * 0.25);
                    matrices.push();
                    matrices.translate(x, y, z);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(-angle)));
                    pyramid(buffer, matrices.peek(), 0.12f, 0.26f);
                    matrices.pop();
                }
            }
        }
        matrices.pop();
    }

    @Override public boolean rendersOutsideBoundingBox(MatrixCoreBlockEntity entity) { return true; }

    @Override public int getRenderDistance() { return 128; }

    /** A flat annulus in the local XZ plane, emissive and readable from both sides. */
    private static void ring(VertexConsumer buffer, MatrixStack.Entry entry, float inner, float outer, float alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * 2 * Math.PI / SEGMENTS;
            double a1 = (i + 1) * 2 * Math.PI / SEGMENTS;
            float u0 = i / (float) SEGMENTS;
            float u1 = (i + 1) / (float) SEGMENTS;
            float[] p0i = {(float) (Math.cos(a0) * inner), 0, (float) (Math.sin(a0) * inner)};
            float[] p0o = {(float) (Math.cos(a0) * outer), 0, (float) (Math.sin(a0) * outer)};
            float[] p1o = {(float) (Math.cos(a1) * outer), 0, (float) (Math.sin(a1) * outer)};
            float[] p1i = {(float) (Math.cos(a1) * inner), 0, (float) (Math.sin(a1) * inner)};
            quad(buffer, entry, p0i, p0o, p1o, p1i, u0, u1, alpha);
            quad(buffer, entry, p1i, p1o, p0o, p0i, u1, u0, alpha);
        }
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, float[] a, float[] b, float[] c, float[] d,
                             float u0, float u1, float alpha) {
        vertex(buffer, entry, a, u0, 0f, alpha);
        vertex(buffer, entry, b, u0, 1f, alpha);
        vertex(buffer, entry, c, u1, 1f, alpha);
        vertex(buffer, entry, d, u1, 0f, alpha);
    }

    /** A tiny pyramid: four side triangles plus a base, drawn twice so it is visible from any side. */
    private static void pyramid(VertexConsumer buffer, MatrixStack.Entry entry, float half, float height) {
        float[][] base = {
            {-half, 0, -half}, {half, 0, -half}, {half, 0, half}, {-half, 0, half}
        };
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
        vertex(buffer, entry, a, 0.2f, 0.2f, 0.8f);
        vertex(buffer, entry, b, 0.8f, 0.2f, 0.8f);
        vertex(buffer, entry, c, 0.5f, 0.9f, 0.85f);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float[] position, float u, float v, float alpha) {
        Matrix4f matrix = entry.getPositionMatrix();
        buffer.vertex(matrix, position[0], position[1], position[2])
            .color(0.82f, 0.62f, 1f, alpha)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(entry, 0f, 1f, 0f);
    }
}
