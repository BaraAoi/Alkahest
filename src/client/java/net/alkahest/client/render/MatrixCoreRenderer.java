package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.minecraft.client.MinecraftClient;
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
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
    private static final Identifier VOID = Alkahest.id("textures/block/matrix_void.png");
    private static final int SEGMENTS = 44;
    private static final int PYRAMIDS = 8;
    /** Rings are split into three radii with a gap between them, so no two rings can intersect. */
    private static final float[][] RINGS = {{0.62f, 0.82f}, {0.90f, 1.08f}, {1.16f, 1.34f}};
    private static final float[] TILTS = {-6f, 9f, -12f};
    private static final float[] HEIGHTS = {-0.34f, 0.0f, 0.34f};
    /** Radians per tick. Slow enough to read as a glide, fast enough to see the pyramids travel. */
    private static final float PYRAMID_ORBIT = 0.018f;
    private static final float PYRAMID_ORBIT_WORKING = 0.055f;

    private final ItemRenderer itemRenderer;

    public MatrixCoreRenderer(BlockEntityRendererFactory.Context context) {
        this.itemRenderer = MinecraftClient.getInstance().getItemRenderer();
    }

    @Override public void render(MatrixCoreBlockEntity entity, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) return;
        BlockState state = entity.getCachedState();
        boolean awake = state.get(MatrixCoreBlock.AWAKE);
        boolean active = state.get(MatrixCoreBlock.ACTIVE);
        double time = (world.getTime() % 240000L) + tickDelta;
        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        if (awake) lining(vertexConsumers.getBuffer(RenderLayer.getEntitySolid(VOID)), matrices.peek(), entity.getPos());
        // Switching render layers can flush and close the previous consumer. Obtain the metal
        // consumer only after the lining is finished; never reuse it across that layer switch.
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(METAL));
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

        // The item the player put in floats in the hollow of the cube and is drunk by the essence.
        ItemStack held = entity.getStack(MatrixCoreBlockEntity.BASE);
        if (!held.isEmpty()) {
            float bob = (float) Math.sin(time * 0.06) * 0.04f;
            matrices.push();
            matrices.translate(0.5f, 1.05f + bob, 0.5f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) time * 1.2f));
            matrices.scale(0.8f, 0.8f, 0.8f);
            BakedModel model = itemRenderer.getModel(held, world, null, 0);
            itemRenderer.renderItem(held, ModelTransformationMode.GROUND, false, matrices, vertexConsumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, model);
            matrices.pop();
        }
    }

    @Override public boolean rendersOutsideBoundingBox(MatrixCoreBlockEntity entity) { return true; }

    @Override public int getRenderDistance() { return 160; }

    /**
     * The altar is hollow once the shell turns invisible, and a hollow cube is a window: the world
     * behind it, the ground below it, even the inside of the terrain shine straight through. To make
     * the whole 3x3x3 behave like one big solid core, the far inner walls are painted with a dark
     * lining. Only the walls that face away from the camera are drawn, so the near side stays open,
     * the halo floats in front of the lining and nothing can be seen through the cube.
     */
    private static void lining(VertexConsumer buffer, MatrixStack.Entry entry, BlockPos pos) {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double camX = camera.x - (pos.getX() + 0.5);
        double camY = camera.y - (pos.getY() + 0.5);
        double camZ = camera.z - (pos.getZ() + 0.5);
        float half = 1.5f;
        int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int axis = 0; axis < 3; axis++) {
            int first = (axis + 1) % 3, second = (axis + 2) % 3;
            for (int sign = -1; sign <= 1; sign += 2) {
                double nx = axis == 0 ? sign : 0, ny = axis == 1 ? sign : 0, nz = axis == 2 ? sign : 0;
                double dx = nx * half - camX, dy = ny * half - camY, dz = nz * half - camZ;
                if (nx * dx + ny * dy + nz * dz < 0) continue; // the near wall stays open
                float[][] quad = new float[4][3];
                for (int i = 0; i < 4; i++) {
                    quad[i][axis] = sign * half;
                    quad[i][first] = corners[i][0] * half;
                    quad[i][second] = corners[i][1] * half;
                }
                vertexRgb(buffer, entry, quad[0], 0f, 1f, 255, 255, 255);
                vertexRgb(buffer, entry, quad[1], 1f, 1f, 255, 255, 255);
                vertexRgb(buffer, entry, quad[2], 1f, 0f, 224, 218, 236);
                vertexRgb(buffer, entry, quad[3], 0f, 0f, 224, 218, 236);
            }
        }
    }

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

    /**
     * A small cast-metal pyramid that circles the core, one shade per face.
     *
     * <p>Entity layers draw in QUADS mode, so a face has to be sent as four vertices: the two
     * bottom corners and the apex written twice. Sending three vertices (as an earlier version
     * did) shifts the whole vertex stream and smears neighbouring pyramids into each other.
     */
    private static void pyramid(VertexConsumer buffer, MatrixStack.Entry entry, float half, float height) {
        float[][] base = {{-half, 0, -half}, {half, 0, -half}, {half, 0, half}, {-half, 0, half}};
        float[] apex = {0, height, 0};
        float[] shades = {0.98f, 0.72f, 0.86f, 0.62f};
        for (int i = 0; i < 4; i++) {
            float[] left = base[i];
            float[] right = base[(i + 1) % 4];
            float u0 = 0.02f + i * 0.24f, u1 = u0 + 0.22f;
            vertex(buffer, entry, right, u0, 0.95f, shades[i]);
            vertex(buffer, entry, left, u1, 0.95f, shades[i]);
            vertex(buffer, entry, apex, u1, 0.12f, shades[i]);
            vertex(buffer, entry, apex, u0, 0.12f, shades[i]);
        }
        vertex(buffer, entry, base[0], 0.05f, 0.55f, 0.45f);
        vertex(buffer, entry, base[1], 0.45f, 0.55f, 0.45f);
        vertex(buffer, entry, base[2], 0.45f, 0.95f, 0.45f);
        vertex(buffer, entry, base[3], 0.05f, 0.95f, 0.45f);
    }

    private static void vertexRgb(VertexConsumer buffer, MatrixStack.Entry entry, float[] position,
                                  float u, float v, int red, int green, int blue) {
        Matrix4f matrix = entry.getPositionMatrix();
        buffer.vertex(matrix, position[0], position[1], position[2])
            .color(red, green, blue, 255)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(entry, 0f, 1f, 0f);
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
