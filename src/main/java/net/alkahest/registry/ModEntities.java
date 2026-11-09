package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.entity.ReagentZoneEntity;
import net.alkahest.entity.ThrownReagentEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<ThrownReagentEntity> THROWN_REAGENT = Registry.register(
        Registries.ENTITY_TYPE, Alkahest.id("thrown_reagent"),
        FabricEntityTypeBuilder.<ThrownReagentEntity>create(SpawnGroup.MISC, ThrownReagentEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f)).trackRangeBlocks(64).trackedUpdateRate(10).build());
    public static final EntityType<ReagentZoneEntity> REAGENT_ZONE = Registry.register(
        Registries.ENTITY_TYPE, Alkahest.id("reagent_zone"),
        FabricEntityTypeBuilder.<ReagentZoneEntity>create(SpawnGroup.MISC, ReagentZoneEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f)).trackRangeBlocks(64).trackedUpdateRate(20).build());
    public static void register() { }
    private ModEntities() { }
}
