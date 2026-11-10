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
 * The finished Matrix: three forged brass rings, drawn with the metal plate sprite and shaded per
 * face, plus the small pyramids that leave their corners once the cube wakes up.
 *
 * <p>A dormant cube is completely still: its rings are coiled tightly inside the core block, so the
 * only thing on show is the brick shell. Waking the Matrix throws the shell away and lays the halo
 * out: the rings spread to three clearly separate radii and lean a little against each other, while
 * eight pyramids drift around the cube, half of them riding high and half low, so neighbours never
 * touch and the whole field reads as a slow gyroscope instead of a spinning blur. Synthesis only
 * speeds that motion up.
 */
public final class MatrixCoreRenderer implements BlockEntityRenderer<MatrixCoreBlockEntity> {
    private static final Identifier METAL = Alkahest.id("textures/block/matrix_ring_metal.png");
    private static final int SEGMENTS = 44;
    private static final int PYRAMIDS = 8;
    /** Rings are split into three radii with a gap between them, so no two rings can intersect. */
    private static final float[][] RINGS = {{0.62f, 0.82f}, {0.90f, 1.08f}, {1.16f, 1.34f}};
    private static final float[] TILTS = {-6f, 9f, -12f};
    private static final float[] HEIGHTS = {-0.34f, 0.0f, 0.34f};
    /** Radians per tick. Slow enough to read as a glide, fast enough to see the pyramids travel. */
    private static final float PYRAMID_ORBIT = 0.018f;
    private static final float PYRAMID_ORBIT_WORKING = 0.055f;

    public MatrixCoreRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(MatrixCoreBlockEntity entity, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) return;
        BlockState state = entity.getCachedState();
        boolean awake = state.get(MatrixCoreBlock.AWAKE);
        boolean active = state.get(MatrixCoreBlock.ACTIVE);
        double time = (world.getTime() % 240000L) + tickDelta;
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(METAL));

        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        if (!awake) {
            // Dormant: a tight coil hidden inside the core block. Nothing turns, nothing floats.
            for (int i = 0; i < RINGS.length; i++) {
                matrices.push();
                matrices.translate(0, -0.07f + i * 0.07f, 0);
                ring(buffer, matrices.peek(), 0.26f + i * 0.02f, 0.38f + i * 0.02f, 0.03f);
                matrices.pop();
            }
        } else {
            float spin = active ? 2.4f : 0.45f;
            float lean = active ? 1.8f : 1f;
            for (int i = 0; i < RINGS.length; i++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (time * spin * (i % 2 == 0 ? 1 : -1))));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(TILTS[i] * lean));
                matrices.translate(0, HEIGHTS[i], 0);
                ring(buffer, matrices.peek(), RINGS[i][0], RINGS[i][1], 0.06f);
                matrices.pop();
            }
            double orbit = time * (active ? PYRAMID_ORBIT_WORKING : PYRAMID_ORBIT);
            float radius = active ? 1.72f : 1.85f;
            for (int i = 0; i < PYRAMIDS; i++) {
                double angle = orbit + i * (2 * Math.PI / PYRAMIDS);
                float x = (float) (Math.cos(angle) * radius);
                float z = (float) (Math.sin(angle) * radius);
                // Half of them ride high, half low, so neighbours never crowd each other.
                float base = (i % 2 == 0) ? 0.30f : -0.30f;
                float y = base + (float) Math.sin(orbit * 1.7 + i) * (active ? 0.16f : 0.05f);
                matrices.push();
                matrices.translate(x, y, z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(-angle)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(active ? 18f : 8f));
                pyramid(buffer, matrices.peek(), 0.13f, 0.30f);
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
            quad(buffer, entry, in0t, out0t, out1t, in1t, u0, u1, 0.1f, 0.9f, 1f);
            quad(buffer, entry, in1b, out1b, out0b, in0b, u1, u0, 0.1f, 0.9f, 0.58f);
            // Outer and inner walls, darker, so the ring looks milled rather than flat.
            quad(buffer, entry, out0b, out0t, out1t, out1b, u0, u1, 0f, 0.2f, 0.78f);
            quad(buffer, entry, in1b, in1t, in0t, in0b, u1, u0, 0f, 0.2f, 0.64f);
        }
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, float[] a, float[] b, float[] c, float[] d,
                             float u0, float u1, float v0, float v1, float shade) {
        vertex(buffer, entry, a, u0, v1, shade);
        vertex(buffer, entry, b, u0, v0, shade);
        vertex(buffer, entry, c, u1, v0, shade);
        vertex(buffer, entry, d, u1, v1, shade);
    }

    /** A small cast-metal pyramid that circles the core, one shade per face. */
    private static void pyramid(VertexConsumer buffer, MatrixStack.Entry entry, float half, float height) {
        float[][] base = {{-half, 0, -half}, {half, 0, -half}, {half, 0, half}, {-half, 0, half}};
        float[] apex = {0, height, 0};
        float[] shades = {0.95f, 0.76f, 0.86f, 0.68f};
        for (int i = 0; i < 4; i++) {
            float[] p0 = base[i];
            float[] p1 = base[(i + 1) % 4];
            tri(buffer, entry, p0, p1, apex, shades[i]);
            tri(buffer, entry, apex, p1, p0, shades[i] * 0.82f);
        }
        tri(buffer, entry, base[0], base[2], base[1], 0.5f);
        tri(buffer, entry, base[0], base[3], base[2], 0.5f);
    }

    private static void tri(VertexConsumer buffer, MatrixStack.Entry entry, float[] a, float[] b, float[] c, float shade) {
        vertex(buffer, entry, a, 0.3f, 0.5f, shade);
        vertex(buffer, entry, b, 0.7f, 0.5f, shade);
        vertex(buffer, entry, c, 0.5f, 0.95f, shade);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float[] position, float u, float v, float shade) {
        Matrix4f matrix = entry.getPositionMatrix();
        int tint = Math.max(0, Math.min(255, (int) (shade * 255)));
        buffer.vertex(matrix, position[0], position[1], position[2])
            .color(tint, tint, tint, 255)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(entry, 0f, 1f, 0f);
    }
}
