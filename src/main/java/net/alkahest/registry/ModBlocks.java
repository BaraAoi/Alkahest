package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.block.custom.DistilleryBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlocks {
    public static final Block ALCHEMY_CAULDRON = register("alchemy_cauldron", new AlchemyCauldronBlock(AbstractBlock.Settings.copy(Blocks.CAULDRON)));
    public static final Block DISTILLERY = register("distillery", new DistilleryBlock(AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK)));
    public static final Block SALT_ORE = register("salt_ore", new Block(AbstractBlock.Settings.copy(Blocks.STONE).strength(3.0f, 3.0f).requiresTool()));
    private static Block register(String name, Block block) {
        Registry.register(Registries.BLOCK, Alkahest.id(name), block);
        Registry.register(Registries.ITEM, Alkahest.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }
    public static void register() { }
    private ModBlocks() { }
}
