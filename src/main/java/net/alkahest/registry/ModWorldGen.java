package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class ModWorldGen {
    public static final RegistryKey<Biome> ALCHEMICAL_WASTES = RegistryKey.of(
        RegistryKeys.BIOME, Alkahest.id("alchemical_wastes")
    );
    public static final RegistryKey<PlacedFeature> SALT_ORE = RegistryKey.of(
        RegistryKeys.PLACED_FEATURE, Alkahest.id("salt_ore")
    );
    public static final RegistryKey<PlacedFeature> ALCHEMICAL_MOSS_RARE = RegistryKey.of(
        RegistryKeys.PLACED_FEATURE, Alkahest.id("alchemical_moss_rare")
    );

    private static TagKey<Biome> biomeTag(String vanillaPath) {
        return TagKey.of(RegistryKeys.BIOME, Identifier.ofVanilla(vanillaPath));
    }

    public static void register() {
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Feature.UNDERGROUND_ORES,
            SALT_ORE
        );
        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(BiomeKeys.DARK_FOREST, BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP),
            GenerationStep.Feature.VEGETAL_DECORATION,
            ALCHEMICAL_MOSS_RARE
        );

        BiomeModifications.addSpawn(
            BiomeSelectors.foundInTheNether(),
            SpawnGroup.MONSTER, ModEntities.ELEMENTALS.get(EssenceType.IGNIS), 12, 1, 2
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.tag(biomeTag("is_ocean"))
                .or(BiomeSelectors.tag(biomeTag("is_river")))
                .or(BiomeSelectors.tag(biomeTag("is_beach"))),
            SpawnGroup.MONSTER, ModEntities.ELEMENTALS.get(EssenceType.AQUA), 14, 1, 2
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.tag(biomeTag("is_mountain"))
                .or(BiomeSelectors.tag(biomeTag("is_hill")))
                .or(BiomeSelectors.includeByKey(BiomeKeys.DRIPSTONE_CAVES, BiomeKeys.LUSH_CAVES)),
            SpawnGroup.MONSTER, ModEntities.ELEMENTALS.get(EssenceType.TERRA), 12, 1, 2
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.tag(biomeTag("is_mountain"))
                .or(BiomeSelectors.includeByKey(
                    BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS,
                    BiomeKeys.WINDSWEPT_FOREST, BiomeKeys.MEADOW
                )),
            SpawnGroup.MONSTER, ModEntities.ELEMENTALS.get(EssenceType.AER), 12, 1, 2
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(
                BiomeKeys.MEADOW, BiomeKeys.FLOWER_FOREST,
                BiomeKeys.CHERRY_GROVE, BiomeKeys.SUNFLOWER_PLAINS
            ),
            SpawnGroup.MONSTER, ModEntities.ELEMENTALS.get(EssenceType.ORDO), 8, 1, 1
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP, BiomeKeys.DEEP_DARK)
                .or(BiomeSelectors.tag(biomeTag("is_badlands"))),
            SpawnGroup.MONSTER, ModEntities.ELEMENTALS.get(EssenceType.PERDITIO), 10, 1, 2
        );
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP),
            SpawnGroup.MONSTER, ModEntities.DISSOLVED, 6, 1, 1
        );
    }

    private ModWorldGen() { }
}
