package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class ModWorldGen {
    public static final RegistryKey<PlacedFeature> SALT_ORE = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Alkahest.id("salt_ore"));
    public static void register() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, SALT_ORE);
    }
    private ModWorldGen() { }
}
