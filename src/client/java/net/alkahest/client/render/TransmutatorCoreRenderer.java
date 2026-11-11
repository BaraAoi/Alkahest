package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.block.custom.TransmutatorAssemblyBlock;
import net.alkahest.block.custom.TransmutatorCoreBlock;
import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.BlockState;
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

/** Two small, forged bands rotate inside the awakened tower, not around the old platform. */
public final class TransmutatorCoreRenderer implements BlockEntityRenderer<TransmutatorCoreBlockEntity> {
    private static final Identifier METAL = Alkahest.id("textures/block/matrix_ring_metal.png");
    private static final int SEGMENTS = 32;

    public TransmutatorCoreRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(TransmutatorCoreBlockEntity core, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertices, int light, int overlay) {
        World world = core.getWorld();
        if (world == null) return;
        BlockState chamber = world.getBlockState(core.getPos().up());
        if (!chamber.isOf(ModBlocks.TRANSMUTATOR_CHAMBER) || !chamber.get(TransmutatorAssemblyBlock.AWAKE)
            || !world.getBlockState(core.getPos().up(2)).isOf(ModBlocks.TRANSMUTATOR_CROWN)) return;
        boolean working = core.getCachedState().get(TransmutatorCoreBlock.WORKING);
        float time = (world.getTime() % 240000L) + tickDelta;
        float speed = working ? 1.1f : .32f;
        // Request the consumer once and do not switch render layers while writing its quads.
        VertexConsumer buffer = vertices.getBuffer(RenderLayer.getEntitySolid(METAL));
        matrices.push();
        matrices.translate(.5f, 1.5f, .5f);
        for (int i = 0; i < 2; i++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * speed * (i == 0 ? 1f : -.8f)));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(i == 0 ? 23f : -30f));
            ring(buffer, matrices.peek(), i == 0 ? .29f : .34f, i == 0 ? .325f : .375f, .014f, light);
            matrices.pop();
        }
        matrices.pop();
    }

    @Override public boolean rendersOutsideBoundingBox(TransmutatorCoreBlockEntity core) { return true; }
    @Override public int getRenderDistance() { return 96; }

    private static void ring(VertexConsumer out, MatrixStack.Entry entry, float inner, float outer, float half, int light) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a = i * 2 * Math.PI / SEGMENTS, b = (i + 1) * 2 * Math.PI / SEGMENTS;
            float ax = (float)Math.cos(a), az = (float)Math.sin(a), bx = (float)Math.cos(b), bz = (float)Math.sin(b);
            float u0 = i / (float)SEGMENTS, u1 = (i + 1) / (float)SEGMENTS;
            float[] it0={ax*inner,half,az*inner}, ot0={ax*outer,half,az*outer};
            float[] it1={bx*inner,half,bz*inner}, ot1={bx*outer,half,bz*outer};
            float[] ib0={ax*inner,-half,az*inner}, ob0={ax*outer,-half,az*outer};
            float[] ib1={bx*inner,-half,bz*inner}, ob1={bx*outer,-half,bz*outer};
            quad(out,entry,it0,ot0,ot1,it1,u0,u1,1f,light);
            quad(out,entry,ib1,ob1,ob0,ib0,u1,u0,.57f,light);
            quad(out,entry,ob0,ot0,ot1,ob1,u0,u1,.78f,light);
            quad(out,entry,ib1,it1,it0,ib0,u1,u0,.65f,light);
        }
    }

    private static void quad(VertexConsumer out, MatrixStack.Entry entry, float[] a, float[] b, float[] c, float[] d,
                             float u0, float u1, float shade, int light) {
        vertex(out,entry,a,u0,1,shade,light);
        vertex(out,entry,b,u0,0,shade,light);
        vertex(out,entry,c,u1,0,shade,light);
        vertex(out,entry,d,u1,1,shade,light);
    }
    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, float[] p, float u, float v, float shade, int light) {
        int color = Math.max(0, Math.min(255, (int)(shade * 255)));
        out.vertex(entry.getPositionMatrix(),p[0],p[1],p[2]).color(color,color,color,255)
            .texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,0,1,0);
    }
}
