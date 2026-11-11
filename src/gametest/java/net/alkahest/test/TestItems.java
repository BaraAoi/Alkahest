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
    public static final Item UNKNOWN = Registry.register(Registries.ITEM,
        Identifier.of("alkahest_gametest", "unknown_raw"), new Item(new Item.Settings()));
    @Override public void onInitialize() { }
}
