package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.shield.ShieldImpactEffect;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import java.util.HashMap;
import java.util.Map;

/** Client-only visualization; actual protection always remains server-authoritative. */
public final class ShieldVisuals {
    private static final Identifier TEXTURE=Alkahest.id("textures/effect/shield_shell.png");
    private static final int LATITUDES=12, LONGITUDES=24;
    private static final Map<Integer,Long> IMPACTS=new HashMap<>();
    private static ClientWorld world;

    public static void receive(int id, boolean impact) {
        var current=MinecraftClient.getInstance().world;
        if (current==null) return;
        if (world!=current) { IMPACTS.clear(); world=current; }
        if (impact) IMPACTS.put(id,current.getTime());
        else IMPACTS.remove(id);
    }
    public static void tick(MinecraftClient client) {
        if (client.world!=world || client.player==null) { clear(); return; }
        long now=world.getTime();
        IMPACTS.entrySet().removeIf(e -> ShieldImpactEffect.opacity(now-e.getValue())<=0 || world.getEntityById(e.getKey())==null);
    }
    public static void clear() { IMPACTS.clear(); world=null; }
    public static void render(WorldRenderContext context) {
        var client=MinecraftClient.getInstance();
        if (world==null || context.world()!=world || client.player==null || context.matrixStack()==null || context.consumers()==null) return;
        float delta=context.tickCounter().getTickDelta(false);
        var camera=context.camera().getPos();
        for (var entry : IMPACTS.entrySet()) {
            float flash=ShieldImpactEffect.opacity(world.getTime()-entry.getValue()+delta);
            if (flash<=0 || !(world.getEntityById(entry.getKey()) instanceof PlayerEntity player)
                || player.isSpectator() || !player.isAlive() || (player!=client.player && player.isInvisibleTo(client.player))) continue;
            var pos=player.getLerpedPos(delta);
            if (pos.squaredDistanceTo(camera)>64*64) continue;
            float time=player.age+delta;
            float radius=1.14f+(1-flash)*.04f;
            int opacity=Math.round(255*flash);
            MatrixStack matrices=context.matrixStack(); matrices.push();
            matrices.translate(pos.x-camera.x,pos.y-camera.y+player.getHeight()*.5,pos.z-camera.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time*.55f));
            var out=context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
            var m=matrices.peek();
            for(int y=0;y<LATITUDES;y++) for(int x=0;x<LONGITUDES;x++) {
                float v0=(float)y/LATITUDES, v1=(float)(y+1)/LATITUDES;
                float u0=(float)x/LONGITUDES, u1=(float)(x+1)/LONGITUDES;
                vertex(out,m,u0,v0,radius,opacity); vertex(out,m,u0,v1,radius,opacity);
                vertex(out,m,u1,v1,radius,opacity); vertex(out,m,u1,v0,radius,opacity);
            }
            matrices.pop();
        }
    }
    private static void vertex(VertexConsumer out, MatrixStack.Entry matrix, float u, float v, float radius, int opacity) {
        double lon=u*Math.PI*2, lat=(v-.5)*Math.PI;
        float x=(float)(Math.cos(lat)*Math.cos(lon)), y=(float)Math.sin(lat), z=(float)(Math.cos(lat)*Math.sin(lon));
        out.vertex(matrix.getPositionMatrix(),x*radius,y*radius,z*radius).color(255,255,255,opacity)
            .texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(matrix,x,y,z);
    }
    private ShieldVisuals() { }
}
