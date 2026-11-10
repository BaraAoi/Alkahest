package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.EssenceSeparatorBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<EssenceSeparatorBlockEntity> ESSENCE_SEPARATOR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("essence_separator"), BlockEntityType.Builder.create(EssenceSeparatorBlockEntity::new, ModBlocks.ESSENCE_SEPARATOR).build(null));
    public static final BlockEntityType<AlchemyPipeBlockEntity> ALCHEMY_PIPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("alchemy_pipe"), BlockEntityType.Builder.create(AlchemyPipeBlockEntity::new, ModBlocks.ALCHEMY_PIPE).build(null));
    public static final BlockEntityType<EssenceChargerBlockEntity> ESSENCE_CHARGER = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("essence_charger"), BlockEntityType.Builder.create(EssenceChargerBlockEntity::new, ModBlocks.ESSENCE_CHARGER).build(null));
    public static final BlockEntityType<JarBlockEntity> JAR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("jar"), BlockEntityType.Builder.create(JarBlockEntity::new, ModBlocks.JAR).build(null));
    public static final BlockEntityType<CauldronBlockEntity> CAULDRON = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("alchemy_cauldron"), BlockEntityType.Builder.create(CauldronBlockEntity::new, ModBlocks.ALCHEMY_CAULDRON).build(null));
    public static final BlockEntityType<DistilleryBlockEntity> DISTILLERY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("distillery"), BlockEntityType.Builder.create(DistilleryBlockEntity::new, ModBlocks.DISTILLERY).build(null));
    public static void register() { }
    private ModBlockEntities() { }
}
