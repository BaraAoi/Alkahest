package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.block.custom.AlchemicalMossBlock;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.block.custom.DistilleryBlock;
import net.alkahest.block.custom.EssenceChargerBlock;
import net.alkahest.block.custom.EssenceSeparatorBlock;
import net.alkahest.block.custom.JarBlock;
import net.alkahest.block.custom.Tier2DistilleryBlock;
import net.alkahest.block.custom.AlchemicalBrickBlock;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.block.custom.MatrixPedestalBlock;
import net.alkahest.block.custom.MatrixPillarBlock;
import net.alkahest.block.custom.TransmutatorCoreBlock;
import net.alkahest.block.custom.TransmutatorAssemblyBlock;
import net.alkahest.block.custom.TransmutatorPillarBlock;
import net.alkahest.block.custom.DistilleryBellowsBlock;
import net.alkahest.block.custom.StabilizerCoreBlock;
import net.alkahest.block.custom.StabilizerCrestBlock;
import net.alkahest.block.custom.StabilizerReceiverBlock;
import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.item.custom.JarItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block ALCHEMY_CAULDRON = register("alchemy_cauldron", new AlchemyCauldronBlock(AbstractBlock.Settings.copy(Blocks.CAULDRON)));
    public static final Block WATER_FAUCET = register("water_faucet", new net.alkahest.block.custom.WaterFaucetBlock(
        AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().noCollision().strength(2.0f)));
    public static final Block JAR = jar();
    public static final Block ALCHEMY_PIPE = register("alchemy_pipe", new AlchemyPipeBlock(AbstractBlock.Settings.copy(Blocks.GLASS).strength(0.8f).nonOpaque()));
    public static final Block ESSENCE_SEPARATOR = register("essence_separator", new EssenceSeparatorBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque()));
    public static final Block ESSENCE_CHARGER = register("essence_charger", new EssenceChargerBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque()));
    public static final Block DISTILLERY = register("distillery", new DistilleryBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().luminance(state -> state.get(DistilleryBlock.WORKING) ? 5 : 0)));
    public static final Block STABILIZER_RECEIVER = register("stabilizer_receiver", new StabilizerReceiverBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque()));
    public static final Block STABILIZER_CORE = register("stabilizer_core", new StabilizerCoreBlock(
        AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().luminance(state -> state.get(StabilizerCoreBlock.ACTIVE) ? 7 : 0)));
    public static final Block STABILIZER_CREST = register("stabilizer_crest", new StabilizerCrestBlock(
        AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK).nonOpaque().luminance(state -> 3)));
    public static final Block DISTILLERY_TIER2 = distilleryStorage("distillery_tier2", new Tier2DistilleryBlock(
        AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().luminance(state -> state.get(Tier2DistilleryBlock.WORKING) ? 6 : 0)));
    public static final Block DISTILLERY_BELLOWS = distilleryStorage("distillery_bellows",
        new DistilleryBellowsBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));
    public static final Block TRANSMUTATOR_CORE = register("transmutator_core", new TransmutatorCoreBlock(
        AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().requiresTool().strength(3.5f, 6f)
            .luminance(state -> state.get(TransmutatorCoreBlock.WORKING) ? 7 : 0)));
    public static final Block TRANSMUTATOR_PILLAR = register("transmutator_pillar", new TransmutatorPillarBlock(
        AbstractBlock.Settings.copy(Blocks.QUARTZ_BLOCK).nonOpaque().requiresTool().strength(2.5f, 5f)
            .luminance(state -> state.get(TransmutatorPillarBlock.CHARGED) ? 5 : 0)));
    public static final Block TRANSMUTATOR_CHAMBER = register("transmutator_chamber", new TransmutatorAssemblyBlock(
        AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque().requiresTool().strength(2.5f, 5f)
            .luminance(state -> state.get(TransmutatorAssemblyBlock.AWAKE) ? 5 : 0), 1));
    public static final Block TRANSMUTATOR_CROWN = register("transmutator_crown", new TransmutatorAssemblyBlock(
        AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().requiresTool().strength(2.5f, 5f)
            .luminance(state -> state.get(TransmutatorAssemblyBlock.AWAKE) ? 4 : 0), 2));
    public static final Block MATRIX_CORE = register("matrix_core", new MatrixCoreBlock(
        AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK).nonOpaque().requiresTool().strength(4f, 8f)
            .luminance(state -> state.get(MatrixCoreBlock.ACTIVE) ? 9 : 0)));
    public static final Block MATRIX_PEDESTAL = register("matrix_pedestal", new MatrixPedestalBlock(
        AbstractBlock.Settings.copy(Blocks.QUARTZ_BLOCK).nonOpaque().requiresTool().strength(1.5f, 4f)));
    public static final Block MATRIX_PILLAR = register("matrix_pillar", new MatrixPillarBlock(
        AbstractBlock.Settings.copy(Blocks.QUARTZ_BLOCK).nonOpaque().requiresTool().strength(3f, 6f)
            .luminance(state -> state.get(MatrixPillarBlock.ACTIVE) ? 6 : 0)));
    public static final Block ALCHEMICAL_BRICK = register("alchemical_brick", new AlchemicalBrickBlock(
        AbstractBlock.Settings.copy(Blocks.DEEPSLATE_TILES).requiresTool().strength(2.5f, 6f)
            .luminance(state -> state.get(AlchemicalBrickBlock.ACTIVE) ? 3 : 0)));
    public static final Block SALT_ORE = register("salt_ore", new Block(AbstractBlock.Settings.copy(Blocks.STONE).strength(3.0f, 3.0f).requiresTool()));
    public static final Block ALCHEMICAL_MOSS = register("alchemical_moss", new AlchemicalMossBlock(
        AbstractBlock.Settings.create().noCollision().nonOpaque().breakInstantly()
            .sounds(BlockSoundGroup.MOSS_CARPET).luminance(state -> 7)));
    // Temporary visual flames; the zone entity owns their lifetime and damage.
    public static final Block NAPALM_FLAME = Registry.register(Registries.BLOCK, Alkahest.id("napalm_flame"),
        new Block(AbstractBlock.Settings.create().noCollision().nonOpaque().breakInstantly().dropsNothing()
            .replaceable().luminance(state -> 14)));
    private static Block jar() {
        Block block = Registry.register(Registries.BLOCK, Alkahest.id("jar"), new JarBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque()));
        Registry.register(Registries.ITEM, Alkahest.id("jar"), new JarItem(block, new Item.Settings()));
        return block;
    }
    private static Block distilleryStorage(String name, Block block) {
        Registry.register(Registries.BLOCK, Alkahest.id(name), block);
        Registry.register(Registries.ITEM, Alkahest.id(name), new net.alkahest.item.custom.DistilleryStorageItem(block, new Item.Settings()));
        return block;
    }
    private static Block register(String name, Block block) {
        Registry.register(Registries.BLOCK, Alkahest.id(name), block);
        Registry.register(Registries.ITEM, Alkahest.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }
    public static void register() { }
    private ModBlocks() { }
}
