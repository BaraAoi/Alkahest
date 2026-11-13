package net.alkahest.essence;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

import static net.alkahest.essence.EssenceType.*;

/** Default values for the early distillation/monocle systems. Values are per item. */
public final class ItemEssenceMap {
    private static final TagKey<Item> FLOWERS = tag("minecraft", "flowers");
    private static final TagKey<Item> LEAVES = tag("minecraft", "leaves");
    private static final TagKey<Item> LOGS = tag("minecraft", "logs");
    private static final TagKey<Item> PLANKS = tag("minecraft", "planks");
    private static final TagKey<Item> SEEDS = tag("c", "seeds");
    private static final Map<Item, List<EssenceStack>> VALUES = Map.ofEntries(
        e(Items.APPLE, s(HERBA, 3), s(AQUA, 1), s(ORDO, 1)),
        // Organic sources make Potentia obtainable in the early distillery without inorganic inputs.
        e(Items.CHICKEN, s(HERBA, 2), s(AER, 2)),
        e(Items.GLOW_BERRIES, s(HERBA, 3), s(LUX, 2), s(POTENTIA, 2)),
        e(Items.GOLDEN_APPLE, s(HERBA, 3), s(ORDO, 3), s(POTENTIA, 2)),
        e(Items.BREAD, s(HERBA, 3), s(ORDO, 1)),
        e(Items.CARROT, s(HERBA, 3), s(AQUA, 1)),
        // Additional vanilla resources that have no crafting recipe.
        e(Items.DIRT, s(TERRA, 3), s(HERBA, 1)),
        e(Items.MUD, s(TERRA, 3), s(AQUA, 2)),
        e(Items.CLAY, s(TERRA, 8), s(AQUA, 4)),
        e(Items.DEEPSLATE, s(TERRA, 3), s(PERDITIO, 1)),
        e(Items.COBBLED_DEEPSLATE, s(TERRA, 3), s(PERDITIO, 1)),
        e(Items.INFESTED_DEEPSLATE, s(TERRA, 3), s(PERDITIO, 1), s(VITIUM, 1)),
        e(Items.DEEPSLATE_COAL_ORE, s(TERRA, 3), s(PERDITIO, 1), s(IGNIS, 3)),
        e(Items.DEEPSLATE_IRON_ORE, s(TERRA, 4), s(PERDITIO, 1), s(METALLUM, 5)),
        e(Items.DEEPSLATE_COPPER_ORE, s(TERRA, 3), s(PERDITIO, 1), s(METALLUM, 3), s(POTENTIA, 1)),
        e(Items.DEEPSLATE_GOLD_ORE, s(TERRA, 3), s(PERDITIO, 1), s(METALLUM, 3), s(ORDO, 5), s(LUX, 1)),
        e(Items.DEEPSLATE_REDSTONE_ORE, s(TERRA, 3), s(PERDITIO, 1), s(POTENTIA, 4), s(ORDO, 2)),
        e(Items.DEEPSLATE_EMERALD_ORE, s(TERRA, 9), s(PERDITIO, 1), s(ORDO, 8), s(LUX, 4)),
        e(Items.DEEPSLATE_DIAMOND_ORE, s(TERRA, 18), s(PERDITIO, 1), s(ORDO, 15), s(LUX, 10)),
        e(Items.DEEPSLATE_LAPIS_ORE, s(TERRA, 3), s(PERDITIO, 1), s(ORDO, 3), s(LUX, 2)),
        e(Items.TUFF, s(TERRA, 3), s(PERDITIO, 1)),
        e(Items.CALCITE, s(TERRA, 3), s(ORDO, 1)),
        e(Items.GRANITE, s(TERRA, 3), s(IGNIS, 1)),
        e(Items.DIORITE, s(TERRA, 3), s(ORDO, 1)),
        e(Items.ANDESITE, s(TERRA, 3), s(ORDO, 1)),
        e(Items.DRIPSTONE_BLOCK, s(TERRA, 3), s(AQUA, 1)),
        e(Items.POINTED_DRIPSTONE, s(TERRA, 2), s(AQUA, 1)),
        e(Items.BASALT, s(TERRA, 3), s(IGNIS, 1)),
        e(Items.BLACKSTONE, s(TERRA, 3), s(IGNIS, 1)),
        e(Items.SOUL_SAND, s(TERRA, 2), s(MORTUUS, 2)),
        e(Items.SOUL_SOIL, s(TERRA, 2), s(MORTUUS, 2)),
        e(Items.ICE, s(AQUA, 3), s(GELUM, 2)),
        e(Items.SNOWBALL, s(AQUA, 1), s(GELUM, 1)),
        e(Items.MOSS_BLOCK, s(HERBA, 3), s(AQUA, 1)),
        e(Items.VINE, s(HERBA, 3), s(AQUA, 1)),
        e(Items.BAMBOO, s(HERBA, 2), s(TERRA, 1)),
        // Stripping changes the surface, not the bamboo resource in the block.
        e(Items.STRIPPED_BAMBOO_BLOCK, s(HERBA, 18), s(TERRA, 9)),
        e(Items.OAK_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.SPRUCE_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.BIRCH_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.JUNGLE_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.ACACIA_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.DARK_OAK_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.CHERRY_SAPLING, s(HERBA, 3), s(AQUA, 1)),
        e(Items.MANGROVE_PROPAGULE, s(HERBA, 3), s(AQUA, 1)),
        e(Items.AZALEA, s(HERBA, 3), s(AQUA, 1)),
        e(Items.FLOWERING_AZALEA, s(HERBA, 3), s(AQUA, 1)),
        e(Items.BROWN_MUSHROOM, s(HERBA, 2), s(AQUA, 1)),
        e(Items.RED_MUSHROOM, s(HERBA, 2), s(AQUA, 1)),
        e(Items.KELP, s(HERBA, 2), s(AQUA, 2)),
        e(Items.SEAGRASS, s(HERBA, 2), s(AQUA, 2)),
        e(Items.NETHER_WART, s(HERBA, 2), s(IGNIS, 1)),
        e(Items.GLOWSTONE_DUST, s(LUX, 3), s(POTENTIA, 1)),
        // Vanilla drops and naturally occurring resources, explicitly assigned rather than guessed by tag.
        e(Items.COCOA_BEANS, s(HERBA, 2), s(AQUA, 1)),
        e(Items.HONEYCOMB, s(HERBA, 2), s(ORDO, 1)),
        e(Items.HONEY_BOTTLE, s(HERBA, 2), s(AQUA, 3), s(TERRA, 2), s(ORDO, 1)),
        e(Items.MILK_BUCKET, s(HERBA, 2), s(AQUA, 3), s(METALLUM, 18), s(TERRA, 6)),
        e(Items.EGG, s(HERBA, 2), s(AQUA, 1)),
        e(Items.BEETROOT, s(HERBA, 3), s(AQUA, 1)),
        e(Items.MELON_SLICE, s(HERBA, 2), s(AQUA, 2)),
        e(Items.PUMPKIN, s(HERBA, 4), s(AQUA, 2)),
        e(Items.SWEET_BERRIES, s(HERBA, 2), s(AQUA, 1)),
        e(Items.SHORT_GRASS, s(HERBA, 2), s(AQUA, 1)),
        e(Items.FERN, s(HERBA, 2), s(AQUA, 1)),
        e(Items.LILY_PAD, s(HERBA, 2), s(AQUA, 2)),
        e(Items.CRIMSON_FUNGUS, s(HERBA, 2), s(IGNIS, 1)),
        e(Items.WARPED_FUNGUS, s(HERBA, 2), s(IGNIS, 1)),
        e(Items.CHORUS_FRUIT, s(HERBA, 2), s(SPIRITUS, 2)),
        e(Items.BEEF, s(HERBA, 2), s(AQUA, 1)),
        e(Items.PORKCHOP, s(HERBA, 2), s(AQUA, 1)),
        e(Items.MUTTON, s(HERBA, 2), s(AQUA, 1)),
        e(Items.RABBIT, s(HERBA, 2), s(AQUA, 1)),
        e(Items.COD, s(HERBA, 2), s(AQUA, 2)),
        e(Items.SALMON, s(HERBA, 2), s(AQUA, 2)),
        e(Items.TROPICAL_FISH, s(HERBA, 2), s(AQUA, 2)),
        e(Items.PUFFERFISH, s(HERBA, 2), s(AQUA, 2), s(VITIUM, 1)),
        e(Items.RABBIT_HIDE, s(HERBA, 2), s(ORDO, 1)),
        e(Items.RABBIT_FOOT, s(HERBA, 2), s(MOTUS, 1)),
        e(Items.TURTLE_SCUTE, s(HERBA, 2), s(ORDO, 2)),
        e(Items.ARMADILLO_SCUTE, s(HERBA, 2), s(ORDO, 2)),
        e(Items.NAUTILUS_SHELL, s(AQUA, 3), s(ORDO, 2)),
        e(Items.SHULKER_SHELL, s(SPIRITUS, 3), s(ORDO, 2)),
        e(Items.PHANTOM_MEMBRANE, s(SPIRITUS, 2), s(AER, 2)),
        e(Items.GHAST_TEAR, s(AQUA, 2), s(SPIRITUS, 2)),
        e(Items.PRISMARINE_CRYSTALS, s(AQUA, 3), s(LUX, 2)),
        e(Items.NETHER_STAR, s(SPIRITUS, 18), s(LUX, 12), s(POTENTIA, 12)),
        e(Items.HEAVY_CORE, s(METALLUM, 6), s(ORDO, 4)),
        e(Items.BELL, s(METALLUM, 4), s(ORDO, 2), s(MOTUS, 2)),
        e(Items.GLOW_INK_SAC, s(AQUA, 1), s(LUX, 2)),
        e(Items.CRIMSON_NYLIUM, s(TERRA, 3), s(HERBA, 2), s(IGNIS, 1)),
        e(Items.WARPED_NYLIUM, s(TERRA, 3), s(HERBA, 2), s(IGNIS, 1)),
        e(Items.WARPED_WART_BLOCK, s(HERBA, 4), s(IGNIS, 2)),
        e(Items.GILDED_BLACKSTONE, s(TERRA, 3), s(IGNIS, 1), s(METALLUM, 2), s(ORDO, 2)),
        e(Items.BREEZE_ROD, s(AER, 6), s(MOTUS, 4)),
        e(Items.ANCIENT_DEBRIS, s(TERRA, 4), s(METALLUM, 4), s(IGNIS, 2)),
        e(Items.CHORUS_PLANT, s(HERBA, 2), s(SPIRITUS, 2)),
        e(Items.GRASS_BLOCK, s(TERRA, 3), s(HERBA, 2)),
        e(Items.PODZOL, s(TERRA, 3), s(HERBA, 2)),
        e(Items.MYCELIUM, s(TERRA, 3), s(HERBA, 2)),
        e(Items.ROOTED_DIRT, s(TERRA, 3), s(HERBA, 2)),
        e(Items.SEA_PICKLE, s(AQUA, 2), s(LUX, 1)),
        e(Items.SPONGE, s(AQUA, 3), s(HERBA, 1)),
        // Explicit environmental sources of Vapor. Ordinary and soul campfires retain the
        // composition of their baseline crafting ingredients as well as a trace of smoke.
        e(Items.WET_SPONGE, s(AQUA, 3), s(HERBA, 1), s(VAPOR, 1)),
        e(Items.CAMPFIRE, s(IGNIS, 3), s(TERRA, 10), s(HERBA, 18), s(VAPOR, 1)),
        e(Items.SOUL_CAMPFIRE, s(TERRA, 11), s(HERBA, 18), s(MORTUUS, 2), s(VAPOR, 1)),
        e(Items.AMETHYST_CLUSTER, s(ORDO, 5), s(LUX, 3)),
        e(Items.CRYING_OBSIDIAN, s(TERRA, 4), s(PERDITIO, 2), s(SPIRITUS, 2)),
        e(Items.COAL_ORE, s(TERRA, 2), s(IGNIS, 3)),
        e(Items.IRON_ORE, s(TERRA, 2), s(METALLUM, 5)),
        e(Items.COPPER_ORE, s(TERRA, 2), s(METALLUM, 3), s(POTENTIA, 1)),
        e(Items.GOLD_ORE, s(TERRA, 2), s(METALLUM, 3), s(ORDO, 5), s(LUX, 1)),
        e(Items.REDSTONE_ORE, s(TERRA, 2), s(POTENTIA, 4), s(ORDO, 2)),
        e(Items.EMERALD_ORE, s(TERRA, 8), s(ORDO, 8), s(LUX, 4)),
        e(Items.DIAMOND_ORE, s(TERRA, 17), s(ORDO, 15), s(LUX, 10)),
        e(Items.LAPIS_ORE, s(TERRA, 2), s(ORDO, 3), s(LUX, 2)),
        e(Items.NETHER_GOLD_ORE, s(TERRA, 2), s(IGNIS, 1), s(METALLUM, 3), s(ORDO, 5)),
        e(Items.NETHER_QUARTZ_ORE, s(TERRA, 2), s(IGNIS, 1), s(ORDO, 3), s(POTENTIA, 2)),
        e(Items.SAND, s(TERRA, 3)),
        e(Items.RED_SAND, s(TERRA, 3)),
        e(Items.GRAVEL, s(TERRA, 3), s(PERDITIO, 1)),
        e(Items.CLAY_BALL, s(TERRA, 2), s(AQUA, 1)),
        e(Items.FLINT, s(TERRA, 2), s(PERDITIO, 1)),
        e(Items.GLASS, s(TERRA, 2), s(ORDO, 1)),
        e(Items.NETHERRACK, s(TERRA, 2), s(IGNIS, 1)),
        e(Items.END_STONE, s(TERRA, 2), s(SPIRITUS, 1)),
        e(Items.WHEAT, s(HERBA, 3), s(ORDO, 1)),
        e(Items.POTATO, s(HERBA, 3), s(AQUA, 1)),
        e(Items.SUGAR_CANE, s(HERBA, 3), s(AQUA, 1)),
        e(Items.LEATHER, s(HERBA, 2), s(ORDO, 1)),
        e(Items.INK_SAC, s(AQUA, 1), s(PERDITIO, 1)),
        e(Items.COBBLESTONE, s(TERRA, 2), s(PERDITIO, 1)),
        e(Items.STONE, s(TERRA, 2), s(ORDO, 1)),
        e(Items.COAL, s(IGNIS, 3), s(TERRA, 1)),
        e(Items.CHARCOAL, s(IGNIS, 3), s(TERRA, 1)),
        e(Items.BLAZE_ROD, s(IGNIS, 8), s(POTENTIA, 4)),
        e(Items.BLAZE_POWDER, s(IGNIS, 4), s(POTENTIA, 2)),
        e(Items.TORCH, s(IGNIS, 1), s(LUX, 1)),
        e(Items.CACTUS, s(HERBA, 2), s(AQUA, 1)),
        e(Items.SLIME_BALL, s(AQUA, 2), s(HERBA, 1)),
        e(Items.STRING, s(HERBA, 1), s(ORDO, 1)),
        e(Items.FERMENTED_SPIDER_EYE, s(VITIUM, 4), s(MORTUUS, 2)),
        // Filled buckets are consumed whole, including the three iron ingots of the vessel.
        e(Items.WATER_BUCKET, s(AQUA, 10), s(METALLUM, 18), s(TERRA, 6)),
        e(Items.PRISMARINE_SHARD, s(AQUA, 5), s(TERRA, 2)),
        e(Items.FEATHER, s(AER, 4), s(MOTUS, 2)),
        e(Items.SUGAR, s(AER, 2), s(MOTUS, 1)),
        e(Items.REDSTONE, s(POTENTIA, 4), s(ORDO, 2)),
        e(Items.GOLD_INGOT, s(METALLUM, 4), s(ORDO, 6), s(LUX, 2)),
        e(Items.IRON_INGOT, s(METALLUM, 6), s(TERRA, 2)),
        e(Items.COPPER_INGOT, s(METALLUM, 4), s(POTENTIA, 1)),
        e(Items.RAW_GOLD, s(METALLUM, 3), s(ORDO, 5), s(LUX, 1)),
        e(Items.RAW_IRON, s(METALLUM, 5), s(TERRA, 2)),
        e(Items.RAW_COPPER, s(METALLUM, 3), s(POTENTIA, 1)),
        e(Items.DIAMOND, s(TERRA, 15), s(ORDO, 15), s(LUX, 10)),
        e(Items.EMERALD, s(ORDO, 8), s(TERRA, 6), s(LUX, 4)),
        e(Items.LAPIS_LAZULI, s(ORDO, 3), s(LUX, 2)),
        e(Items.QUARTZ, s(ORDO, 3), s(POTENTIA, 2)),
        e(Items.OBSIDIAN, s(TERRA, 4), s(IGNIS, 2), s(PERDITIO, 2)),
        e(Items.ROTTEN_FLESH, s(MORTUUS, 4), s(PERDITIO, 3)),
        e(Items.BONE, s(MORTUUS, 4), s(TERRA, 2)),
        e(Items.SPIDER_EYE, s(VITIUM, 3), s(PERDITIO, 2)),
        e(Items.GUNPOWDER, s(PERDITIO, 5), s(IGNIS, 4)),
        e(Items.ENDER_PEARL, s(SPIRITUS, 6), s(MOTUS, 6), s(PERDITIO, 2)),
        e(Items.AMETHYST_SHARD, s(ORDO, 5), s(LUX, 3))
    );

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.of(RegistryKeys.ITEM, Identifier.of(namespace, path));
    }
    private static EssenceStack s(EssenceType type, int amount) { return new EssenceStack(type, amount, 1.0f); }
    private static Map.Entry<Item, List<EssenceStack>> e(Item item, EssenceStack... values) { return Map.entry(item, List.of(values)); }

    /** The legacy manually assigned values; used as seeds, never as a guess for unknown raw materials. */
    public static List<EssenceStack> base(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        for (var entry : net.alkahest.registry.ModItems.CRYSTALS.entrySet()) {
            if (entry.getKey() != VACUUM && stack.isOf(entry.getValue())) return List.of(s(entry.getKey(), 1));
        }
        // Like the six infused materials, preserve the base plus three units on recycling;
        // recipe-depth bonuses must not return more Spiritus than infusion costs.
        if (stack.isOf(net.alkahest.registry.ModItems.SPIRIT_CLOTH)) {
            java.util.EnumMap<EssenceType, Integer> values = new java.util.EnumMap<>(EssenceType.class);
            for (EssenceStack value : base(new ItemStack(Items.STRING))) values.put(value.type(), value.amount() * 4);
            values.merge(SPIRITUS, 3, Integer::sum);
            List<EssenceStack> result = new java.util.ArrayList<>();
            values.forEach((type, amount) -> result.add(s(type, amount)));
            return List.copyOf(result);
        }
        // Existing infused items predate the price increase. Keep their composition stable:
        // source plus three units. The additional infusion cost is consumed, not returned.
        for (var entry : net.alkahest.registry.ModItems.INFUSED_MATERIALS.entrySet()) {
            if (!stack.isOf(entry.getValue())) continue;
            Item source = net.alkahest.item.custom.InfusedMaterialItem.source(entry.getKey());
            java.util.EnumMap<EssenceType, Integer> values = new java.util.EnumMap<>(EssenceType.class);
            for (EssenceStack value : VALUES.get(source)) values.put(value.type(), value.amount());
            values.merge(entry.getKey(), 3, Integer::sum);
            List<EssenceStack> result = new java.util.ArrayList<>();
            values.forEach((type, amount) -> result.add(s(type, amount)));
            return List.copyOf(result);
        }
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId.equals(Identifier.of("alkahest", "ash"))) return List.of(s(PERDITIO, 1));
        if (itemId.equals(Identifier.of("alkahest", "philosophers_stone"))) return List.of(s(ORDO, 10), s(SPIRITUS, 10));
        if (itemId.equals(Identifier.of("alkahest", "dissolved_spawn_egg"))) return List.of(s(SPIRITUS, 3), s(PERDITIO, 2));
        for (EssenceType type : List.of(IGNIS, AQUA, TERRA, AER, ORDO, PERDITIO)) {
            if (itemId.equals(Identifier.of("alkahest", "elemental_" + type.asString() + "_spawn_egg")))
                return List.of(s(SPIRITUS, 3), s(type, 2));
        }
        if (itemId.equals(Identifier.of("alkahest", "salt_ore"))) {
            return List.of(s(TERRA, 3), s(ORDO, 2), s(AQUA, 1));
        }
        if (itemId.equals(Identifier.of("alkahest", "alkahest_vial"))) {
            // The cauldron consumes the diamond and the ten specified dissolved essence costs.
            return List.of(s(IGNIS, 1), s(AQUA, 1), s(TERRA, 16), s(AER, 1), s(ORDO, 18),
                s(PERDITIO, 3), s(HERBA, 3), s(VITIUM, 2), s(SPIRITUS, 6), s(MOTUS, 6), s(LUX, 10));
        }
        if (itemId.equals(Identifier.of("alkahest", "herbal_paste"))) {
            return List.of(s(HERBA, 3), s(AQUA, 1));
        }
        if (itemId.equals(Identifier.of("alkahest", "salt"))) {
            return List.of(s(ORDO, 2), s(TERRA, 1), s(AQUA, 1));
        }
        if (itemId.equals(Identifier.of("alkahest", "alchemical_moss"))) {
            return List.of(s(HERBA, 3), s(VITIUM, 2), s(ORDO, 2));
        }
        if (itemId.equals(Identifier.of("alkahest", "caustic_slime"))) {
            return List.of(s(VITIUM, 2), s(HERBA, 1));
        }
        List<EssenceStack> exact = VALUES.get(stack.getItem());
        if (exact != null) return exact;
        List<EssenceStack> vanilla = VanillaEssenceSources.get(stack.getItem());
        if (!vanilla.isEmpty()) return vanilla;
        if (stack.isIn(FLOWERS) || stack.isIn(LEAVES) || stack.isIn(SEEDS)
            || stack.isOf(Items.WHEAT_SEEDS) || stack.isOf(Items.BEETROOT_SEEDS)
            || stack.isOf(Items.MELON_SEEDS) || stack.isOf(Items.PUMPKIN_SEEDS)
            || stack.isOf(Items.TORCHFLOWER_SEEDS) || stack.isOf(Items.PITCHER_POD)) {
            return List.of(s(HERBA, 3), s(AQUA, 1));
        }
        if (stack.isIn(LOGS) || stack.isIn(PLANKS)) return List.of(s(HERBA, 4), s(TERRA, 2));
        return List.of();
    }
    /** Vanilla aquatic buckets deliberately consume their creature with the vessel. */
    private static boolean vanillaCreatureBucket(ItemStack stack) {
        return stack.isOf(Items.AXOLOTL_BUCKET) || stack.isOf(Items.COD_BUCKET)
            || stack.isOf(Items.PUFFERFISH_BUCKET) || stack.isOf(Items.SALMON_BUCKET)
            || stack.isOf(Items.TADPOLE_BUCKET) || stack.isOf(Items.TROPICAL_FISH_BUCKET);
    }

    /** Protect stored items, loot, bees, other saved entities and filled essence jars. */
    public static boolean safeToConsume(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var distillery = stack.get(net.alkahest.registry.ModDataComponents.DISTILLERY_CONTENTS);
        if (distillery != null && distillery.total() > 0) return false;
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null && container.streamNonEmpty().findAny().isPresent()) return false;
        var bundle = stack.get(net.minecraft.component.DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) return false;
        var bees = stack.get(net.minecraft.component.DataComponentTypes.BEES);
        if (bees != null && !bees.isEmpty()) return false;
        if (stack.contains(net.minecraft.component.DataComponentTypes.CONTAINER_LOOT)
            || stack.contains(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA)
            || stack.contains(net.minecraft.component.DataComponentTypes.ENTITY_DATA)
            || (stack.contains(net.minecraft.component.DataComponentTypes.BUCKET_ENTITY_DATA)
                && !vanillaCreatureBucket(stack))) return false;
        return !(stack.getItem() instanceof net.alkahest.item.custom.JarItem)
            || net.alkahest.item.custom.JarItem.contents(stack).total() == 0;
    }

    /** Context-free access to explicit values, retained for existing API users. */
    public static List<EssenceStack> get(ItemStack stack) { return base(stack); }

    /** Server and client snapshots are separate even in an integrated single-player JVM. */
    public static List<EssenceStack> get(ItemStack stack, net.minecraft.world.World world) {
        if (stack.isEmpty()) return List.of();
        if (world == null) return base(stack);
        Map<Item, List<EssenceStack>> snapshot = world.isClient
            ? RecipeEssenceService.clientValues() : RecipeEssenceService.serverValues(world.getServer());
        return snapshot == null ? base(stack) : snapshot.getOrDefault(stack.getItem(), List.of());
    }
    private ItemEssenceMap() { }
}
