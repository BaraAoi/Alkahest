package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.DissolvedEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Custom 3D articulated model for the Dissolved mutant:
 * hunched melting torso, exposed pulsing acid core, crystal protrusion on skull,
 * oversized dripping left claw, withered right arm and shambling legs.
 */
public final class DissolvedEntityModel extends SinglePartEntityModel<DissolvedEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Alkahest.id("dissolved"), "main");

    private final ModelPart root;
    private final ModelPart bodyRoot;
    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart core;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public DissolvedEntityModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        this.bodyRoot = root.getChild("body_root");
        this.head = this.bodyRoot.getChild("head");
        this.torso = this.bodyRoot.getChild("torso");
        this.core = this.bodyRoot.getChild("core");
        this.leftArm = this.bodyRoot.getChild("left_arm");
        this.rightArm = this.bodyRoot.getChild("right_arm");
        this.leftLeg = this.bodyRoot.getChild("left_leg");
        this.rightLeg = this.bodyRoot.getChild("right_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        ModelPartData body = root.addChild("body_root", ModelPartBuilder.create(), ModelTransform.pivot(0.0f, 24.0f, 0.0f));

        // Tilted skull (8x8x8) + jagged unstable crystal shard (3x5x3) + dripping jaw tendril (6x3x2)
        body.addChild(
            "head",
            ModelPartBuilder.create()
                .uv(0, 0).cuboid(-4.0f, -8.0f, -4.5f, 8.0f, 8.0f, 8.0f)
                .uv(32, 0).cuboid(0.8f, -11.5f, -1.5f, 3.0f, 5.0f, 3.0f)
                .uv(44, 0).cuboid(-3.0f, 0.0f, -4.2f, 6.0f, 3.0f, 2.0f),
            ModelTransform.of(0.0f, -20.0f, -1.2f, 0.12f, 0.0f, -0.08f)
        );

        // Hunched corroded torso (8x11x5)
        body.addChild(
            "torso",
            ModelPartBuilder.create()
                .uv(0, 16).cuboid(-4.0f, 0.0f, -2.5f, 8.0f, 11.0f, 5.0f),
            ModelTransform.of(0.0f, -20.5f, -0.5f, 0.18f, 0.0f, 0.0f)
        );

        // Exposed pulsing essence core inside ribcage (4x4x4)
        body.addChild(
            "core",
            ModelPartBuilder.create()
                .uv(26, 16).cuboid(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
            ModelTransform.pivot(0.4f, -15.0f, -1.2f)
        );

        // Heavy elongated left acid claw (4x13x4)
        body.addChild(
            "left_arm",
            ModelPartBuilder.create()
                .uv(0, 32).cuboid(-3.0f, -1.5f, -2.0f, 4.0f, 13.0f, 4.0f),
            ModelTransform.pivot(-5.0f, -19.0f, -0.5f)
        );

        // Shorter melted right arm (3x10x3)
        body.addChild(
            "right_arm",
            ModelPartBuilder.create()
                .uv(16, 32).cuboid(-0.5f, -1.5f, -1.5f, 3.0f, 10.0f, 3.0f),
            ModelTransform.pivot(4.5f, -18.5f, -0.3f)
        );

        // Shambling legs (4x10x4 each)
        body.addChild(
            "left_leg",
            ModelPartBuilder.create()
                .uv(0, 49).cuboid(-2.0f, 0.0f, -2.0f, 4.0f, 10.0f, 4.0f),
            ModelTransform.pivot(-2.1f, -10.0f, 0.5f)
        );
        body.addChild(
            "right_leg",
            ModelPartBuilder.create()
                .uv(16, 49).cuboid(-2.0f, 0.0f, -2.0f, 4.0f, 10.0f, 4.0f),
            ModelTransform.pivot(2.1f, -10.0f, 0.5f)
        );

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(
        DissolvedEntity entity,
        float limbAngle,
        float limbDistance,
        float animationProgress,
        float headYaw,
        float headPitch
    ) {
        this.getPart().traverse().forEach(ModelPart::resetTransform);

        this.head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        this.head.pitch = 0.12f + headPitch * MathHelper.RADIANS_PER_DEGREE;
        this.head.roll = -0.08f + MathHelper.sin(animationProgress * 0.09f) * 0.05f;

        // Breathing / sloshing core pulse
        float pulse = 1.0f + MathHelper.sin(animationProgress * 0.22f) * 0.12f;
        this.core.xScale = pulse;
        this.core.yScale = pulse;
        this.core.zScale = pulse;
        this.core.yaw = animationProgress * 0.08f;
        this.core.roll = animationProgress * 0.06f;

        // Shambling gait + aggressive reach
        this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.68f) * 1.15f * limbDistance;
        this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.68f + (float) Math.PI) * 1.15f * limbDistance;

        float swing = this.handSwingProgress > 0.0f
            ? -MathHelper.sin(this.handSwingProgress * (float) Math.PI) * 1.45f
            : 0.0f;
        this.leftArm.pitch = -0.45f + MathHelper.cos(limbAngle * 0.68f + (float) Math.PI) * 0.65f * limbDistance + swing;
        this.rightArm.pitch = -0.30f + MathHelper.cos(limbAngle * 0.68f) * 0.55f * limbDistance + swing * 0.7f;
        this.leftArm.roll = -0.08f - MathHelper.cos(animationProgress * 0.08f) * 0.05f;
        this.rightArm.roll = 0.10f + MathHelper.cos(animationProgress * 0.08f) * 0.05f;
    }
}
