package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.DissolvedEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public final class DissolvedEntityRenderer extends MobEntityRenderer<DissolvedEntity, DissolvedEntityModel> {
    private static final Identifier TEXTURE = Alkahest.id("textures/entity/dissolved/dissolved.png");

    public DissolvedEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new DissolvedEntityModel(context.getPart(DissolvedEntityModel.LAYER)), 0.45f);
        this.addFeature(new DissolvedGlowFeatureRenderer(this));
    }

    @Override
    public Identifier getTexture(DissolvedEntity entity) {
        return TEXTURE;
    }
}
