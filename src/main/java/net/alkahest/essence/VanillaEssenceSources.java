package net.alkahest.essence;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static net.alkahest.essence.EssenceType.*;

/** Explicitly reviewed vanilla sources; no registry-name or tag inference for other mods. */
final class VanillaEssenceSources {
    private static final Map<Item, List<EssenceStack>> VALUES = create();
    private static Map<Item, List<EssenceStack>> create() {
        Map<Item, List<EssenceStack>> values = new HashMap<>();
        // Vanilla spawn eggs represent living or summoned entities.
        put(values, List.of(new EssenceStack(SPIRITUS, 3, 1f), new EssenceStack(HERBA, 2, 1f)),
            Items.ALLAY_SPAWN_EGG, Items.ARMADILLO_SPAWN_EGG, Items.AXOLOTL_SPAWN_EGG, Items.BAT_SPAWN_EGG,
            Items.BEE_SPAWN_EGG, Items.BLAZE_SPAWN_EGG, Items.BOGGED_SPAWN_EGG, Items.BREEZE_SPAWN_EGG,
            Items.CAMEL_SPAWN_EGG, Items.CAT_SPAWN_EGG, Items.CAVE_SPIDER_SPAWN_EGG, Items.CHICKEN_SPAWN_EGG,
            Items.COD_SPAWN_EGG, Items.COW_SPAWN_EGG, Items.CREEPER_SPAWN_EGG, Items.DOLPHIN_SPAWN_EGG,
            Items.DONKEY_SPAWN_EGG, Items.DROWNED_SPAWN_EGG, Items.ELDER_GUARDIAN_SPAWN_EGG, Items.ENDER_DRAGON_SPAWN_EGG,
            Items.ENDERMAN_SPAWN_EGG, Items.ENDERMITE_SPAWN_EGG, Items.EVOKER_SPAWN_EGG, Items.FOX_SPAWN_EGG,
            Items.FROG_SPAWN_EGG, Items.GHAST_SPAWN_EGG, Items.GLOW_SQUID_SPAWN_EGG, Items.GOAT_SPAWN_EGG,
            Items.GUARDIAN_SPAWN_EGG, Items.HOGLIN_SPAWN_EGG, Items.HORSE_SPAWN_EGG, Items.HUSK_SPAWN_EGG,
            Items.IRON_GOLEM_SPAWN_EGG, Items.LLAMA_SPAWN_EGG, Items.MAGMA_CUBE_SPAWN_EGG, Items.MOOSHROOM_SPAWN_EGG,
            Items.MULE_SPAWN_EGG, Items.OCELOT_SPAWN_EGG, Items.PANDA_SPAWN_EGG, Items.PARROT_SPAWN_EGG,
            Items.PHANTOM_SPAWN_EGG, Items.PIG_SPAWN_EGG, Items.PIGLIN_BRUTE_SPAWN_EGG, Items.PIGLIN_SPAWN_EGG,
            Items.PILLAGER_SPAWN_EGG, Items.POLAR_BEAR_SPAWN_EGG, Items.PUFFERFISH_SPAWN_EGG, Items.RABBIT_SPAWN_EGG,
            Items.RAVAGER_SPAWN_EGG, Items.SALMON_SPAWN_EGG, Items.SHEEP_SPAWN_EGG, Items.SHULKER_SPAWN_EGG,
            Items.SILVERFISH_SPAWN_EGG, Items.SKELETON_HORSE_SPAWN_EGG, Items.SKELETON_SPAWN_EGG, Items.SLIME_SPAWN_EGG,
            Items.SNIFFER_SPAWN_EGG, Items.SNOW_GOLEM_SPAWN_EGG, Items.SPIDER_SPAWN_EGG, Items.SQUID_SPAWN_EGG,
            Items.STRAY_SPAWN_EGG, Items.STRIDER_SPAWN_EGG, Items.TADPOLE_SPAWN_EGG, Items.TRADER_LLAMA_SPAWN_EGG,
            Items.TROPICAL_FISH_SPAWN_EGG, Items.TURTLE_SPAWN_EGG, Items.VEX_SPAWN_EGG, Items.VILLAGER_SPAWN_EGG,
            Items.VINDICATOR_SPAWN_EGG, Items.WANDERING_TRADER_SPAWN_EGG, Items.WARDEN_SPAWN_EGG, Items.WITCH_SPAWN_EGG,
            Items.WITHER_SKELETON_SPAWN_EGG, Items.WITHER_SPAWN_EGG, Items.WOLF_SPAWN_EGG, Items.ZOGLIN_SPAWN_EGG,
            Items.ZOMBIE_HORSE_SPAWN_EGG, Items.ZOMBIE_SPAWN_EGG, Items.ZOMBIE_VILLAGER_SPAWN_EGG, Items.ZOMBIFIED_PIGLIN_SPAWN_EGG
        );
        // Archaeology sherds are decorated fired clay.
        put(values, List.of(new EssenceStack(TERRA, 2, 1f), new EssenceStack(ORDO, 1, 1f)),
            Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD, Items.ARMS_UP_POTTERY_SHERD, Items.BLADE_POTTERY_SHERD,
            Items.BREWER_POTTERY_SHERD, Items.BURN_POTTERY_SHERD, Items.DANGER_POTTERY_SHERD, Items.EXPLORER_POTTERY_SHERD,
            Items.FLOW_POTTERY_SHERD, Items.FRIEND_POTTERY_SHERD, Items.GUSTER_POTTERY_SHERD, Items.HEART_POTTERY_SHERD,
            Items.HEARTBREAK_POTTERY_SHERD, Items.HOWL_POTTERY_SHERD, Items.MINER_POTTERY_SHERD, Items.MOURNER_POTTERY_SHERD,
            Items.PLENTY_POTTERY_SHERD, Items.PRIZE_POTTERY_SHERD, Items.SCRAPE_POTTERY_SHERD, Items.SHEAF_POTTERY_SHERD,
            Items.SHELTER_POTTERY_SHERD, Items.SKULL_POTTERY_SHERD, Items.SNORT_POTTERY_SHERD
        );
        // Vanilla smithing templates are rare ordered mineral patterns.
        put(values, List.of(new EssenceStack(ORDO, 3, 1f), new EssenceStack(TERRA, 2, 1f)),
            Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE
        );
        // Vanilla records and the disc fragment are carriers of sound.
        put(values, List.of(new EssenceStack(ORDO, 2, 1f), new EssenceStack(MOTUS, 2, 1f)),
            Items.MUSIC_DISC_11, Items.MUSIC_DISC_13, Items.MUSIC_DISC_5, Items.MUSIC_DISC_BLOCKS,
            Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_CREATOR, Items.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_OTHERSIDE,
            Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_PRECIPICE, Items.MUSIC_DISC_RELIC, Items.MUSIC_DISC_STAL,
            Items.MUSIC_DISC_STRAD, Items.MUSIC_DISC_WAIT, Items.MUSIC_DISC_WARD, Items.DISC_FRAGMENT_5
        );
        // Naturally growing vegetation and fungal blocks.
        put(values, List.of(new EssenceStack(HERBA, 3, 1f), new EssenceStack(AQUA, 1, 1f)),
            Items.BIG_DRIPLEAF, Items.SMALL_DRIPLEAF, Items.TALL_GRASS, Items.LARGE_FERN,
            Items.DEAD_BUSH, Items.CRIMSON_ROOTS, Items.WARPED_ROOTS, Items.NETHER_SPROUTS,
            Items.HANGING_ROOTS, Items.MANGROVE_ROOTS, Items.MUSHROOM_STEM, Items.BROWN_MUSHROOM_BLOCK,
            Items.RED_MUSHROOM_BLOCK, Items.TWISTING_VINES, Items.WEEPING_VINES, Items.GLOW_LICHEN
        );
        // Live marine coral.
        put(values, List.of(new EssenceStack(AQUA, 3, 1f), new EssenceStack(HERBA, 2, 1f)),
            Items.BRAIN_CORAL, Items.BRAIN_CORAL_BLOCK, Items.BRAIN_CORAL_FAN, Items.BUBBLE_CORAL,
            Items.BUBBLE_CORAL_BLOCK, Items.BUBBLE_CORAL_FAN, Items.FIRE_CORAL, Items.FIRE_CORAL_BLOCK,
            Items.FIRE_CORAL_FAN, Items.HORN_CORAL, Items.HORN_CORAL_BLOCK, Items.HORN_CORAL_FAN,
            Items.TUBE_CORAL, Items.TUBE_CORAL_BLOCK, Items.TUBE_CORAL_FAN
        );
        // Dead marine coral.
        put(values, List.of(new EssenceStack(TERRA, 2, 1f), new EssenceStack(MORTUUS, 1, 1f)),
            Items.DEAD_BRAIN_CORAL, Items.DEAD_BRAIN_CORAL_BLOCK, Items.DEAD_BRAIN_CORAL_FAN, Items.DEAD_BUBBLE_CORAL,
            Items.DEAD_BUBBLE_CORAL_BLOCK, Items.DEAD_BUBBLE_CORAL_FAN, Items.DEAD_FIRE_CORAL, Items.DEAD_FIRE_CORAL_BLOCK,
            Items.DEAD_FIRE_CORAL_FAN, Items.DEAD_HORN_CORAL, Items.DEAD_HORN_CORAL_BLOCK, Items.DEAD_HORN_CORAL_FAN,
            Items.DEAD_TUBE_CORAL, Items.DEAD_TUBE_CORAL_BLOCK, Items.DEAD_TUBE_CORAL_FAN
        );
        // Crystal buds.
        put(values, List.of(new EssenceStack(ORDO, 3, 1f), new EssenceStack(LUX, 2, 1f)),
            Items.SMALL_AMETHYST_BUD, Items.MEDIUM_AMETHYST_BUD, Items.LARGE_AMETHYST_BUD, Items.BUDDING_AMETHYST
        );
        // Stone inhabited by silverfish.
        put(values, List.of(new EssenceStack(TERRA, 2, 1f), new EssenceStack(PERDITIO, 1, 1f), new EssenceStack(VITIUM, 1, 1f)),
            Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE, Items.INFESTED_STONE_BRICKS, Items.INFESTED_MOSSY_STONE_BRICKS,
            Items.INFESTED_CRACKED_STONE_BRICKS, Items.INFESTED_CHISELED_STONE_BRICKS
        );
        // Sculk blocks.
        put(values, List.of(new EssenceStack(SPIRITUS, 2, 1f), new EssenceStack(MORTUUS, 2, 1f)),
            Items.SCULK, Items.SCULK_CATALYST, Items.SCULK_SENSOR, Items.SCULK_SHRIEKER,
            Items.SCULK_VEIN
        );
        // Rare naturally generated luminous blocks.
        put(values, List.of(new EssenceStack(LUX, 3, 1f), new EssenceStack(HERBA, 2, 1f)),
            Items.OCHRE_FROGLIGHT, Items.PEARLESCENT_FROGLIGHT, Items.VERDANT_FROGLIGHT, Items.SHROOMLIGHT
        );
        // Common rare living drops.
        put(values, List.of(new EssenceStack(SPIRITUS, 2, 1f), new EssenceStack(ORDO, 2, 1f)),
            Items.GOAT_HORN, Items.DRAGON_BREATH, Items.ECHO_SHARD, Items.SADDLE
        );
        // Reviewed hard-to-obtain loot, rather than inventing rarity from item names.
        put(values, List.of(new EssenceStack(SPIRITUS, 10, 1f), new EssenceStack(MOTUS, 8, 1f), new EssenceStack(ORDO, 6, 1f)), Items.ELYTRA);
        put(values, List.of(new EssenceStack(SPIRITUS, 8, 1f), new EssenceStack(AQUA, 8, 1f), new EssenceStack(ORDO, 6, 1f)), Items.HEART_OF_THE_SEA);
        put(values, List.of(new EssenceStack(SPIRITUS, 10, 1f), new EssenceStack(ORDO, 8, 1f)), Items.TOTEM_OF_UNDYING);
        put(values, List.of(new EssenceStack(AQUA, 10, 1f), new EssenceStack(METALLUM, 8, 1f), new EssenceStack(MOTUS, 4, 1f)), Items.TRIDENT);
        // Loot-only banner patterns and named tags.
        put(values, List.of(new EssenceStack(HERBA, 1, 1f), new EssenceStack(ORDO, 2, 1f)),
            Items.CREEPER_BANNER_PATTERN, Items.FLOW_BANNER_PATTERN, Items.GLOBE_BANNER_PATTERN, Items.GUSTER_BANNER_PATTERN,
            Items.MOJANG_BANNER_PATTERN, Items.PIGLIN_BANNER_PATTERN, Items.SKULL_BANNER_PATTERN, Items.NAME_TAG
        );
        // Aquatic and powder-snow buckets include the metal vessel in the consumed item.
        put(values, List.of(new EssenceStack(METALLUM, 18, 1f), new EssenceStack(TERRA, 6, 1f), new EssenceStack(AQUA, 3, 1f)),
            Items.AXOLOTL_BUCKET, Items.COD_BUCKET, Items.PUFFERFISH_BUCKET, Items.SALMON_BUCKET,
            Items.TADPOLE_BUCKET, Items.TROPICAL_FISH_BUCKET, Items.POWDER_SNOW_BUCKET
        );
        // Naturally generated and loot-only vanilla materials.
        put(values, List.of(new EssenceStack(TERRA, 4, 1f), new EssenceStack(ORDO, 2, 1f)),
            Items.BEDROCK, Items.REINFORCED_DEEPSLATE, Items.END_PORTAL_FRAME,
            Items.PETRIFIED_OAK_SLAB, Items.SUSPICIOUS_SAND, Items.SUSPICIOUS_GRAVEL);
        put(values, List.of(new EssenceStack(HERBA, 2, 1f), new EssenceStack(ORDO, 1, 1f)),
            Items.BEE_NEST, Items.BUNDLE, Items.TURTLE_EGG, Items.SNIFFER_EGG, Items.FROGSPAWN);
        put(values, List.of(new EssenceStack(MORTUUS, 3, 1f), new EssenceStack(ORDO, 1, 1f)),
            Items.CREEPER_HEAD, Items.DRAGON_HEAD, Items.PIGLIN_HEAD, Items.PLAYER_HEAD,
            Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL, Items.ZOMBIE_HEAD);
        put(values, List.of(new EssenceStack(SPIRITUS, 3, 1f), new EssenceStack(ORDO, 2, 1f)),
            Items.TRIAL_KEY, Items.OMINOUS_TRIAL_KEY, Items.SPAWNER, Items.TRIAL_SPAWNER, Items.VAULT);
        put(values, List.of(new EssenceStack(SPIRITUS, 20, 1f), new EssenceStack(ORDO, 12, 1f)), Items.DRAGON_EGG);
        // Creative/operator items are known vanilla registry entries, assigned explicitly too.
        put(values, List.of(new EssenceStack(POTENTIA, 3, 1f), new EssenceStack(ORDO, 2, 1f)),
            Items.BARRIER, Items.LIGHT, Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK,
            Items.REPEATING_COMMAND_BLOCK, Items.JIGSAW, Items.STRUCTURE_BLOCK, Items.STRUCTURE_VOID,
            Items.COMMAND_BLOCK_MINECART, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK);
        // Loot-only equipment with no vanilla crafting recipe.
        put(values, List.of(new EssenceStack(METALLUM, 4, 1f), new EssenceStack(ORDO, 1, 1f)),
            Items.CHAINMAIL_BOOTS, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_HELMET,
            Items.CHAINMAIL_LEGGINGS, Items.IRON_HORSE_ARMOR, Items.GOLDEN_HORSE_ARMOR, Items.DIAMOND_HORSE_ARMOR);
        put(values, List.of(new EssenceStack(HERBA, 2, 1f), new EssenceStack(ORDO, 1, 1f)),
            Items.ENCHANTED_BOOK, Items.FILLED_MAP, Items.WRITTEN_BOOK);
        put(values, List.of(new EssenceStack(HERBA, 3, 1f), new EssenceStack(ORDO, 4, 1f), new EssenceStack(POTENTIA, 3, 1f)),
            Items.ENCHANTED_GOLDEN_APPLE);
        put(values, List.of(new EssenceStack(IGNIS, 3, 1f), new EssenceStack(POTENTIA, 2, 1f)), Items.FIREWORK_STAR);
        put(values, List.of(new EssenceStack(HERBA, 2, 1f), new EssenceStack(VITIUM, 2, 1f)), Items.POISONOUS_POTATO);
        put(values, List.of(new EssenceStack(METALLUM, 18, 1f), new EssenceStack(TERRA, 6, 1f), new EssenceStack(IGNIS, 10, 1f)), Items.LAVA_BUCKET);
        // The base vessel is known; potion contents vary with item components and are not inferred.
        put(values, List.of(new EssenceStack(TERRA, 2, 1f), new EssenceStack(ORDO, 1, 1f), new EssenceStack(AQUA, 1, 1f)),
            Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.OMINOUS_BOTTLE, Items.EXPERIENCE_BOTTLE);
        put(values, List.of(new EssenceStack(TERRA, 2, 1f), new EssenceStack(HERBA, 2, 1f), new EssenceStack(MOTUS, 1, 1f)), Items.TIPPED_ARROW);
        put(values, List.of(new EssenceStack(HERBA, 3, 1f), new EssenceStack(AQUA, 2, 1f)), Items.SUSPICIOUS_STEW);
        return Map.copyOf(values);
    }
    private static void put(Map<Item, List<EssenceStack>> values, List<EssenceStack> aspects, Item... items) {
        for (Item item : items) {
            if (values.putIfAbsent(item, aspects) != null) throw new IllegalStateException("Duplicate vanilla essence source");
        }
    }
    static List<EssenceStack> get(Item item) { return VALUES.getOrDefault(item, List.of()); }
    private VanillaEssenceSources() { }
}
