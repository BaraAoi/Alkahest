package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.WindGustEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Three layered curling ribbons: a luminous, tapered air vortex instead of an arrow model. */
public final class WindGustRenderer extends EntityRenderer<WindGustEntity> {
    private static final Identifier WIND=Alkahest.id("textures/effect/wind_ribbon.png");
    private static final int SEGMENTS=24;
    public WindGustRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public void render(WindGustEntity gust, float yaw, float delta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light) {
        Vec3d axis=gust.getVelocity().normalize();
        if (axis.lengthSquared()<.001) axis=new Vec3d(0,0,1);
        Vec3d a=axis.crossProduct(new Vec3d(0,1,0));
        if (a.lengthSquared()<.001) a=axis.crossProduct(new Vec3d(1,0,0));
        a=a.normalize(); Vec3d b=axis.crossProduct(a).normalize();
        float t=gust.age+delta;
        float size=gust.child()?.70f:1f;
        VertexConsumer out=consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WIND));
        var mat=matrices.peek();
        for (int strand=0;strand<3;strand++) for (int i=0;i<SEGMENTS;i++) {
            float x0=i/(float)SEGMENTS, x1=(i+1f)/SEGMENTS;
            Vec3d p0=point(axis,a,b,t,strand,x0,size), p1=point(axis,a,b,t,strand,x1,size);
            float w0=(.06f+.055f*(float)Math.sin(Math.PI*x0))*size;
            float w1=(.06f+.055f*(float)Math.sin(Math.PI*x1))*size;
            Vec3d n0=radial(a,b,t,strand,x0), n1=radial(a,b,t,strand,x1);
            int alpha=strand==0?225:185;
            vertex(out,mat,p0.add(n0.multiply(w0)),x0,0,alpha);
            vertex(out,mat,p0.subtract(n0.multiply(w0)),x0,1,alpha);
            vertex(out,mat,p1.subtract(n1.multiply(w1)),x1,1,alpha);
            vertex(out,mat,p1.add(n1.multiply(w1)),x1,0,alpha);
        }
        super.render(gust,yaw,delta,matrices,consumers,light);
    }
    private static Vec3d radial(Vec3d a,Vec3d b,float t,int strand,float u) {
        double angle=t*.30+strand*2*Math.PI/3+u*Math.PI*3.3;
        return a.multiply(Math.cos(angle)).add(b.multiply(Math.sin(angle)));
    }
    private static Vec3d point(Vec3d axis,Vec3d a,Vec3d b,float t,int strand,float u,float size) {
        // Front narrows to a point, rear opens into the rotating wake.
        double radius=(.03+.27*u)*size;
        return axis.multiply((.28-1.18*u)*size).add(radial(a,b,t,strand,u).multiply(radius));
    }
    private static void vertex(VertexConsumer out,MatrixStack.Entry matrix,Vec3d p,float u,float v,int alpha) {
        out.vertex(matrix.getPositionMatrix(),(float)p.x,(float)p.y,(float)p.z).color(210,246,255,alpha)
            .texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(matrix,0,1,0);
    }
    @Override public Identifier getTexture(WindGustEntity entity) { return WIND; }
}
