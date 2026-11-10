package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.ElementalEntity;
import net.alkahest.essence.EssenceType;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the articulated 3D Elemental model with per-element body & emissive glow textures.
 */
public final class ElementalEntityRenderer extends MobEntityRenderer<ElementalEntity, ElementalEntityModel> {
    private static final Map<EssenceType, Identifier> TEXTURES = createTextureMap();

    private static Map<EssenceType, Identifier> createTextureMap() {
        Map<EssenceType, Identifier> map = new EnumMap<>(EssenceType.class);
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() == EssenceType.Tier.PRIMA) {
                map.put(type, Alkahest.id("textures/entity/elemental/" + type.asString() + ".png"));
            }
        }
        return Map.copyOf(map);
    }

    public ElementalEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new ElementalEntityModel(context.getPart(ElementalEntityModel.LAYER)), 0.42f);
        this.addFeature(new ElementalGlowFeatureRenderer(this));
    }

    @Override
    protected int getBlockLight(ElementalEntity entity, BlockPos pos) {
        return entity.isGroundedBySalt() ? Math.max(9, super.getBlockLight(entity, pos)) : 15;
    }

    @Override
    public Identifier getTexture(ElementalEntity entity) {
        return TEXTURES.getOrDefault(entity.essenceType(), TEXTURES.get(EssenceType.IGNIS));
    }
}
