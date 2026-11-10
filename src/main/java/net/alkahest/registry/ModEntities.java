package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.entity.ElementalBoltEntity;
import net.alkahest.entity.ElementalEntity;
import net.alkahest.entity.ReagentZoneEntity;
import net.alkahest.entity.ThrownReagentEntity;
import net.alkahest.essence.EssenceType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;

import java.util.EnumMap;
import java.util.Map;

public final class ModEntities {
    public static final EntityType<ThrownReagentEntity> THROWN_REAGENT = Registry.register(
        Registries.ENTITY_TYPE, Alkahest.id("thrown_reagent"),
        FabricEntityTypeBuilder.<ThrownReagentEntity>create(SpawnGroup.MISC, ThrownReagentEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f)).trackRangeBlocks(64).trackedUpdateRate(10).build());
    public static final EntityType<ReagentZoneEntity> REAGENT_ZONE = Registry.register(
        Registries.ENTITY_TYPE, Alkahest.id("reagent_zone"),
        FabricEntityTypeBuilder.<ReagentZoneEntity>create(SpawnGroup.MISC, ReagentZoneEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f)).trackRangeBlocks(64).trackedUpdateRate(20).build());
    public static final EntityType<ElementalBoltEntity> ELEMENTAL_BOLT = Registry.register(
        Registries.ENTITY_TYPE, Alkahest.id("elemental_bolt"),
        FabricEntityTypeBuilder.<ElementalBoltEntity>create(SpawnGroup.MISC, ElementalBoltEntity::new)
            .dimensions(EntityDimensions.fixed(0.35f, 0.35f)).trackRangeBlocks(64).trackedUpdateRate(2).build());
    public static final Map<EssenceType, EntityType<ElementalEntity>> ELEMENTALS = elementals();

    private static Map<EssenceType, EntityType<ElementalEntity>> elementals() {
        Map<EssenceType, EntityType<ElementalEntity>> map = new EnumMap<>(EssenceType.class);
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() != EssenceType.Tier.PRIMA) continue;
            var builder = FabricEntityTypeBuilder.<ElementalEntity>create(
                    SpawnGroup.MONSTER,
                    (entityType, world) -> new ElementalEntity(entityType, world, type)
                )
                .dimensions(EntityDimensions.fixed(0.85f, 1.55f))
                .trackRangeBlocks(64)
                .trackedUpdateRate(3);
            if (type == EssenceType.IGNIS) {
                builder.fireImmune();
            }
            EntityType<ElementalEntity> registered = Registry.register(
                Registries.ENTITY_TYPE,
                Alkahest.id("elemental_" + type.asString()),
                builder.build()
            );
            FabricDefaultAttributeRegistry.register(registered, ElementalEntity.createElementalAttributes());
            SpawnRestriction.register(
                registered,
                SpawnLocationTypes.UNRESTRICTED,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                ElementalEntity::canSpawn
            );
            map.put(type, registered);
        }
        return Map.copyOf(map);
    }

    public static void register() { }
    private ModEntities() { }
}
