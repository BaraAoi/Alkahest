package net.alkahest.client.render;

import net.alkahest.Alkahest;
import net.alkahest.entity.ElementalEntity;
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
 * Custom 3D articulated model for the 6 Prima Elementals:
 * horned elemental visage, armored mantle, spinning inner energy core,
 * floating obelisk arms, 4-pillar orbit ring, counter-rotating lower vortex,
 * and crossed salt shackles when grounded by Binding Salt.
 */
public final class ElementalEntityModel extends SinglePartEntityModel<ElementalEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Alkahest.id("elemental"), "main");

    private final ModelPart root;
    private final ModelPart bodyRoot;
    private final ModelPart head;
    private final ModelPart mantle;
    private final ModelPart core;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart orbitRing;
    private final ModelPart upperVortex;
    private final ModelPart lowerVortex;
    private final ModelPart shackleA;
    private final ModelPart shackleB;

    public ElementalEntityModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        this.bodyRoot = root.getChild("body_root");
        this.head = this.bodyRoot.getChild("head");
        this.mantle = this.bodyRoot.getChild("mantle");
        this.core = this.bodyRoot.getChild("core");
        this.leftArm = this.bodyRoot.getChild("left_arm");
        this.rightArm = this.bodyRoot.getChild("right_arm");
        this.orbitRing = this.bodyRoot.getChild("orbit_ring");
        this.upperVortex = this.bodyRoot.getChild("upper_vortex");
        this.lowerVortex = this.bodyRoot.getChild("lower_vortex");
        this.shackleA = this.bodyRoot.getChild("shackle_a");
        this.shackleB = this.bodyRoot.getChild("shackle_b");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        ModelPartData body = root.addChild("body_root", ModelPartBuilder.create(), ModelTransform.pivot(0.0f, 24.0f, 0.0f));

        // Head (8x8x8) + horns (2x5x4 each) + forehead crystal (2x4x2)
        body.addChild(
            "head",
            ModelPartBuilder.create()
                .uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f)
                .uv(32, 0).cuboid(-5.2f, -11.5f, -2.0f, 2.0f, 5.0f, 4.0f)
                .uv(44, 0).cuboid(3.2f, -11.5f, -2.0f, 2.0f, 5.0f, 4.0f)
                .uv(56, 0).cuboid(-1.0f, -10.2f, -4.6f, 2.0f, 4.0f, 2.0f),
            ModelTransform.pivot(0.0f, -18.5f, 0.0f)
        );

        // Upper armored mantle / chestplate (10x6x6)
        body.addChild(
            "mantle",
            ModelPartBuilder.create()
                .uv(0, 16).cuboid(-5.0f, -3.0f, -3.0f, 10.0f, 6.0f, 6.0f),
            ModelTransform.pivot(0.0f, -15.0f, 0.0f)
        );

        // Luminous inner core (4x4x4)
        body.addChild(
            "core",
            ModelPartBuilder.create()
                .uv(32, 16).cuboid(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
            ModelTransform.pivot(0.0f, -13.5f, 0.0f)
        );

        // Floating shoulder + obelisk forearm (left & mirrored right)
        body.addChild(
            "left_arm",
            ModelPartBuilder.create()
                .uv(0, 28).cuboid(-2.5f, -2.0f, -2.5f, 4.0f, 4.0f, 5.0f)
                .uv(18, 28).cuboid(-2.0f, 2.5f, -2.0f, 3.0f, 9.0f, 4.0f),
            ModelTransform.pivot(-6.5f, -16.5f, 0.0f)
        );
        body.addChild(
            "right_arm",
            ModelPartBuilder.create()
                .uv(0, 28).mirrored().cuboid(-1.5f, -2.0f, -2.5f, 4.0f, 4.0f, 5.0f)
                .uv(18, 28).mirrored().cuboid(-1.0f, 2.5f, -2.0f, 3.0f, 9.0f, 4.0f),
            ModelTransform.pivot(6.5f, -16.5f, 0.0f)
        );

        // Orbiting 4-shard ring around waist/chest
        ModelPartData ring = body.addChild("orbit_ring", ModelPartBuilder.create(), ModelTransform.pivot(0.0f, -11.5f, 0.0f));
        ring.addChild("north_shard", ModelPartBuilder.create().uv(32, 28).cuboid(-1.0f, -3.5f, -1.0f, 2.0f, 7.0f, 2.0f),
            ModelTransform.of(0.0f, 0.0f, -6.5f, 0.14f, 0.0f, 0.0f));
        ring.addChild("south_shard", ModelPartBuilder.create().uv(32, 28).cuboid(-1.0f, -3.5f, -1.0f, 2.0f, 7.0f, 2.0f),
            ModelTransform.of(0.0f, 0.0f, 6.5f, -0.14f, 0.0f, 0.0f));
        ring.addChild("west_shard", ModelPartBuilder.create().uv(32, 28).cuboid(-1.0f, -3.5f, -1.0f, 2.0f, 7.0f, 2.0f),
            ModelTransform.of(-6.5f, 0.0f, 0.0f, 0.0f, 0.0f, -0.14f));
        ring.addChild("east_shard", ModelPartBuilder.create().uv(32, 28).cuboid(-1.0f, -3.5f, -1.0f, 2.0f, 7.0f, 2.0f),
            ModelTransform.of(6.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.14f));

        // Counter-rotating lower vortex tail
        body.addChild(
            "upper_vortex",
            ModelPartBuilder.create().uv(0, 41).cuboid(-3.0f, -1.5f, -3.0f, 6.0f, 3.0f, 6.0f),
            ModelTransform.pivot(0.0f, -8.0f, 0.0f)
        );
        body.addChild(
            "lower_vortex",
            ModelPartBuilder.create().uv(24, 41).cuboid(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
            ModelTransform.pivot(0.0f, -3.8f, 0.0f)
        );

        // Crossed crystal salt shackles (shown when grounded by Binding Salt)
        body.addChild(
            "shackle_a",
            ModelPartBuilder.create().uv(0, 50).cuboid(-6.0f, -1.0f, -6.0f, 12.0f, 2.0f, 12.0f),
            ModelTransform.of(0.0f, -12.5f, 0.0f, 0.42f, 0.0f, 0.35f)
        );
        body.addChild(
            "shackle_b",
            ModelPartBuilder.create().uv(0, 50).cuboid(-6.0f, -1.0f, -6.0f, 12.0f, 2.0f, 12.0f),
            ModelTransform.of(0.0f, -12.5f, 0.0f, -0.42f, 0.78f, -0.35f)
        );

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(ElementalEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        this.getPart().traverse().forEach(ModelPart::resetTransform);

        boolean grounded = entity.isGroundedBySalt();
        boolean charging = entity.isChargingAttack();
        boolean properlyBound = entity.getBoundTicks() > 0;

        // Vertical hover bobbing vs heavy slump onto the ground when levitation is stripped
        if (grounded) {
            this.bodyRoot.pivotY = 26.8f;
            this.mantle.pitch = 0.16f;
        } else {
            this.bodyRoot.pivotY = 23.2f + MathHelper.sin(animationProgress * 0.11f) * 1.35f;
            this.mantle.pitch = MathHelper.clamp(limbDistance * 0.35f, 0.0f, 0.25f);
        }

        // Head tracking
        this.head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        this.head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE + (grounded ? 0.12f : 0.0f);

        // Spinning inner core
        float coreSpeed = grounded ? 0.04f : (charging ? 0.28f : 0.12f);
        this.core.yaw = animationProgress * coreSpeed;
        this.core.pitch = animationProgress * (coreSpeed * 0.7f);
        this.core.roll = animationProgress * (coreSpeed * 0.5f);
        float pulse = charging ? (1.18f + MathHelper.sin(animationProgress * 0.65f) * 0.12f)
            : (1.0f + MathHelper.sin(animationProgress * 0.15f) * 0.06f);
        this.core.xScale = pulse;
        this.core.yScale = pulse;
        this.core.zScale = pulse;

        // Arms: thrust forward and converge when charging a ranged elemental bolt
        if (charging) {
            float tremor = MathHelper.sin(animationProgress * 0.9f) * 0.06f;
            this.leftArm.pitch = -1.38f + tremor;
            this.rightArm.pitch = -1.38f - tremor;
            this.leftArm.yaw = 0.32f;
            this.rightArm.yaw = -0.32f;
            this.leftArm.roll = -0.1f;
            this.rightArm.roll = 0.1f;
        } else if (grounded) {
            this.leftArm.pitch = MathHelper.cos(limbAngle * 0.66f) * 0.45f * limbDistance + 0.18f;
            this.rightArm.pitch = MathHelper.cos(limbAngle * 0.66f + MathHelper.PI) * 0.45f * limbDistance + 0.18f;
            this.leftArm.roll = -0.06f;
            this.rightArm.roll = 0.06f;
        } else {
            this.leftArm.pitch = MathHelper.sin(animationProgress * 0.08f) * 0.12f - limbDistance * 0.25f;
            this.rightArm.pitch = -MathHelper.sin(animationProgress * 0.08f) * 0.12f - limbDistance * 0.25f;
            this.leftArm.roll = -0.16f - MathHelper.cos(animationProgress * 0.09f) * 0.06f;
            this.rightArm.roll = 0.16f + MathHelper.cos(animationProgress * 0.09f) * 0.06f;
        }

        // Orbiting ring & lower vortex: lively spin in flight, stalled & collapsed when grounded
        if (grounded) {
            this.orbitRing.yaw = animationProgress * 0.015f;
            this.orbitRing.pivotY = -8.5f;
            this.upperVortex.yaw = -animationProgress * 0.02f;
            this.lowerVortex.yaw = animationProgress * 0.02f;
        } else {
            float spinBoost = charging ? 2.1f : 1.0f;
            this.orbitRing.yaw = animationProgress * 0.11f * spinBoost;
            this.upperVortex.yaw = -animationProgress * 0.16f * spinBoost;
            this.lowerVortex.yaw = animationProgress * 0.22f * spinBoost;
            this.lowerVortex.roll = MathHelper.sin(animationProgress * 0.12f) * 0.08f;
        }

        // Salt shackles visibility & antipode resonance spin
        this.shackleA.visible = grounded;
        this.shackleB.visible = grounded && properlyBound;
        if (grounded) {
            this.shackleA.yaw = animationProgress * 0.04f;
            if (properlyBound) {
                this.shackleB.yaw = 0.78f - animationProgress * 0.07f;
            }
        }
    }
}
