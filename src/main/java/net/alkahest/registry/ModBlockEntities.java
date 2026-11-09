package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<CauldronBlockEntity> CAULDRON = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("alchemy_cauldron"), BlockEntityType.Builder.create(CauldronBlockEntity::new, ModBlocks.ALCHEMY_CAULDRON).build(null));
    public static final BlockEntityType<DistilleryBlockEntity> DISTILLERY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("distillery"), BlockEntityType.Builder.create(DistilleryBlockEntity::new, ModBlocks.DISTILLERY).build(null));
    public static void register() { }
    private ModBlockEntities() { }
}
