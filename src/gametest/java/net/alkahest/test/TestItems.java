package net.alkahest.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Isolated test-mod entries, never present in the distributed mod. */
public final class TestItems implements ModInitializer {
    public static final Item CRAFTED = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "crafted_sample"), new Item(new Item.Settings()));
    public static final Item TEMPLATE = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "smithing_template"), new Item(new Item.Settings()));
    public static final Item SMITHED = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "smithing_result"), new Item(new Item.Settings()));
    public static final Item COMPLEX = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "complex_result"), new Item(new Item.Settings()));
    public static final Item UNRESOLVED_SMITH = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "unknown_smithing_result"), new Item(new Item.Settings()));
    public static final Item UNKNOWN = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "unknown_raw"), new Item(new Item.Settings()));
    @Override public void onInitialize() { }
}
