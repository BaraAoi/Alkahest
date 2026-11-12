package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.EssenceSeparatorBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.StabilizerCoreBlockEntity;
import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.block.entity.MatrixPedestalBlockEntity;
import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
import net.alkahest.block.entity.TransmutatorPillarBlockEntity;
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
    public static final BlockEntityType<StabilizerReceiverBlockEntity> STABILIZER_RECEIVER = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("stabilizer_receiver"), BlockEntityType.Builder.create(StabilizerReceiverBlockEntity::new, ModBlocks.STABILIZER_RECEIVER).build(null));
    public static final BlockEntityType<StabilizerCoreBlockEntity> STABILIZER_CORE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("stabilizer_core"), BlockEntityType.Builder.create(StabilizerCoreBlockEntity::new, ModBlocks.STABILIZER_CORE).build(null));
    public static final BlockEntityType<JarBlockEntity> JAR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("jar"), BlockEntityType.Builder.create(JarBlockEntity::new, ModBlocks.JAR).build(null));
    public static final BlockEntityType<Tier2DistilleryBlockEntity> DISTILLERY_TIER2 = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("distillery_tier2"), BlockEntityType.Builder.create(Tier2DistilleryBlockEntity::new, ModBlocks.DISTILLERY_TIER2).build(null));
    public static final BlockEntityType<DistilleryBellowsBlockEntity> DISTILLERY_BELLOWS = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("distillery_bellows"), BlockEntityType.Builder.create(DistilleryBellowsBlockEntity::new, ModBlocks.DISTILLERY_BELLOWS).build(null));
    public static final BlockEntityType<MatrixPedestalBlockEntity> MATRIX_PEDESTAL = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("matrix_pedestal"), BlockEntityType.Builder.create(MatrixPedestalBlockEntity::new, ModBlocks.MATRIX_PEDESTAL).build(null));
    public static final BlockEntityType<MatrixCoreBlockEntity> MATRIX_CORE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("matrix_core"), BlockEntityType.Builder.create(MatrixCoreBlockEntity::new, ModBlocks.MATRIX_CORE).build(null));
    public static final BlockEntityType<TransmutatorCoreBlockEntity> TRANSMUTATOR_CORE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("transmutator_core"), BlockEntityType.Builder.create(TransmutatorCoreBlockEntity::new, ModBlocks.TRANSMUTATOR_CORE).build(null));
    public static final BlockEntityType<TransmutatorPillarBlockEntity> TRANSMUTATOR_PILLAR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("transmutator_pillar"), BlockEntityType.Builder.create(TransmutatorPillarBlockEntity::new, ModBlocks.TRANSMUTATOR_PILLAR).build(null));
    public static final BlockEntityType<net.alkahest.block.entity.WaterFaucetBlockEntity> WATER_FAUCET = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("water_faucet"), BlockEntityType.Builder.create(net.alkahest.block.entity.WaterFaucetBlockEntity::new, ModBlocks.WATER_FAUCET).build(null));
    public static final BlockEntityType<CauldronBlockEntity> CAULDRON = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("alchemy_cauldron"), BlockEntityType.Builder.create(CauldronBlockEntity::new, ModBlocks.ALCHEMY_CAULDRON).build(null));
    public static final BlockEntityType<DistilleryBlockEntity> DISTILLERY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
        Alkahest.id("distillery"), BlockEntityType.Builder.create(DistilleryBlockEntity::new, ModBlocks.DISTILLERY).build(null));
    public static void register() { }
    private ModBlockEntities() { }
}
