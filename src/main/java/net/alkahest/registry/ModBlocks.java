package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.block.custom.DistilleryBlock;
import net.alkahest.block.custom.EssenceChargerBlock;
import net.alkahest.block.custom.EssenceSeparatorBlock;
import net.alkahest.block.custom.JarBlock;
import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.item.custom.JarItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlocks {
    public static final Block ALCHEMY_CAULDRON = register("alchemy_cauldron", new AlchemyCauldronBlock(AbstractBlock.Settings.copy(Blocks.CAULDRON)));
    public static final Block JAR = jar();
    public static final Block ALCHEMY_PIPE = register("alchemy_pipe", new AlchemyPipeBlock(AbstractBlock.Settings.copy(Blocks.GLASS).strength(0.8f).nonOpaque()));
    public static final Block ESSENCE_SEPARATOR = register("essence_separator", new EssenceSeparatorBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque()));
    public static final Block ESSENCE_CHARGER = register("essence_charger", new EssenceChargerBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque()));
    public static final Block DISTILLERY = register("distillery", new DistilleryBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().luminance(state -> state.get(DistilleryBlock.WORKING) ? 5 : 0)));
    public static final Block SALT_ORE = register("salt_ore", new Block(AbstractBlock.Settings.copy(Blocks.STONE).strength(3.0f, 3.0f).requiresTool()));
    // Temporary visual flames; the zone entity owns their lifetime and damage.
    public static final Block NAPALM_FLAME = Registry.register(Registries.BLOCK, Alkahest.id("napalm_flame"),
        new Block(AbstractBlock.Settings.create().noCollision().nonOpaque().breakInstantly().dropsNothing()
            .replaceable().luminance(state -> 14)));
    private static Block jar() {
        Block block = Registry.register(Registries.BLOCK, Alkahest.id("jar"), new JarBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque()));
        Registry.register(Registries.ITEM, Alkahest.id("jar"), new JarItem(block, new Item.Settings()));
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
