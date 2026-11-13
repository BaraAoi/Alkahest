package net.alkahest.client.render;

import net.alkahest.block.custom.StabilizerCoreBlock;
import net.alkahest.block.custom.StabilizerCrestBlock;
import net.alkahest.block.entity.StabilizerCoreBlockEntity;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Replaces only the active crest's crystal and gold cap; its base stays a normal block model. */
public final class StabilizerCoreRenderer implements BlockEntityRenderer<StabilizerCoreBlockEntity> {
    public StabilizerCoreRenderer(BlockEntityRendererFactory.Context context) { }
    @Override public void render(StabilizerCoreBlockEntity core, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertices, int light, int overlay) {
        var world=core.getWorld();
        if (world==null || !core.getCachedState().get(StabilizerCoreBlock.ACTIVE)
            || !core.getCachedState().get(StabilizerCoreBlock.FORMED)) return;
        var crest=world.getBlockState(core.getPos().up());
        if (!crest.isOf(ModBlocks.STABILIZER_CREST) || !crest.get(StabilizerCrestBlock.ROTATING)) return;
        // One turn in 240 ticks, with a seamless modulo and no world-state writes.
        float angle=((world.getTime()%240)+tickDelta)*1.5f;
        matrices.push();
        matrices.translate(.5,1.125,.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        var blocks=MinecraftClient.getInstance().getBlockRenderManager();
        matrices.push();
        matrices.translate(-.125,0,-.125);
        matrices.scale(.25f,.5f,.25f);
        blocks.renderBlockAsEntity(Blocks.AMETHYST_BLOCK.getDefaultState(),matrices,vertices,light,overlay);
        matrices.pop();
        matrices.push();
        matrices.translate(-.0625,.5,-.0625);
        matrices.scale(.125f,.25f,.125f);
        blocks.renderBlockAsEntity(Blocks.GOLD_BLOCK.getDefaultState(),matrices,vertices,light,overlay);
        matrices.pop();
        matrices.pop();
    }
    @Override public boolean rendersOutsideBoundingBox(StabilizerCoreBlockEntity core) { return true; }
}
