package net.alkahest.matrix;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A synthesis of the Matrix: one base item plus up to three additional items turn into a new
 * item, paid for with essence that the cube draws out of the surrounding jars.
 */
public record MatrixRecipe(Item base, List<Item> components, ItemStack output,
                           Map<EssenceType, Integer> cost) {
    private static final List<MatrixRecipe> ALL = List.of(
        synthesis(Items.STRING, List.of(), new ItemStack(ModItems.BINDING_SALT, 3),
            EssenceType.ORDO, 4, EssenceType.AQUA, 2),
        synthesis(Items.GLASS_PANE, List.of(Items.GOLD_INGOT), new ItemStack(ModItems.ALCHEMISTS_MONOCLE),
            EssenceType.ORDO, 6, EssenceType.LUX, 4),
        synthesis(Items.STONE, List.of(), new ItemStack(net.alkahest.registry.ModBlocks.ALCHEMICAL_BRICK.asItem(), 6),
            EssenceType.TERRA, 4, EssenceType.ORDO, 2),
        synthesis(Items.GUNPOWDER, List.of(), new ItemStack(ModItems.FIRE_DUST, 2),
            EssenceType.IGNIS, 3, EssenceType.POTENTIA, 3),
        synthesis(Items.LIGHTNING_ROD, List.of(Items.GLASS_BOTTLE), new ItemStack(ModItems.LIGHTNING_IN_A_BOTTLE),
            EssenceType.IGNIS, 6, EssenceType.AER, 6),
        synthesis(Items.QUARTZ, List.of(), new ItemStack(Items.AMETHYST_SHARD, 4),
            EssenceType.LUX, 3, EssenceType.VITIUM, 2),
        synthesis(Items.COAL, List.of(Items.GOLD_INGOT, ModItems.SALT), new ItemStack(Items.DIAMOND),
            EssenceType.METALLUM, 8, EssenceType.LUX, 6, EssenceType.ORDO, 4),
        // The specification's Matrix recipe; the earlier cauldron recipe remains available.
        synthesis(Items.GLASS_BOTTLE, List.of(ModItems.CAUSTIC_SLIME), new ItemStack(ModItems.MIASMA),
            EssenceType.PERDITIO, 20, EssenceType.MORTUUS, 15, EssenceType.AQUA, 10),
        // The specification's alternative; the lightning-rod recipe stays available as well.
        synthesis(Items.GLASS_BOTTLE, List.of(ModItems.CRYSTALS.get(EssenceType.IGNIS), ModItems.CRYSTALS.get(EssenceType.AER)),
            new ItemStack(ModItems.LIGHTNING_IN_A_BOTTLE), EssenceType.POTENTIA, 15, EssenceType.AER, 10),
        // The player requested more involved Matrix syntheses than the specification's original two items.
        synthesis(Items.GOLDEN_APPLE, List.of(ModItems.PURIFIED_WATER, Items.GOLD_INGOT, ModItems.CRYSTALS.get(EssenceType.ORDO)),
            new ItemStack(ModItems.MIDAS_ELIXIR), EssenceType.ORDO, 15, EssenceType.LUX, 10, EssenceType.METALLUM, 5),
        // A higher-volume alternative to the early inventory mixture, not a replacement for it.
        synthesis(ModItems.FIRE_DUST, List.of(Items.SLIME_BALL, Items.BLAZE_POWDER), new ItemStack(ModItems.NAPALM, 3),
            EssenceType.IGNIS, 12, EssenceType.POTENTIA, 8),
        // Optional advanced batch; the earlier two-item inventory mixture remains available.
        synthesis(ModItems.CAUSTIC_SLIME, List.of(Items.GUNPOWDER, Items.IRON_NUGGET), new ItemStack(ModItems.ACID_BOMB, 3),
            EssenceType.VITIUM, 12, EssenceType.PERDITIO, 8, EssenceType.METALLUM, 4),
        // New consumables are final products, not the future byproduct materials reserved for discussion.
        synthesis(Items.SNOWBALL, List.of(Items.GLASS_BOTTLE, ModItems.CRYSTALS.get(EssenceType.GELUM)),
            new ItemStack(ModItems.FROST_FLASK, 2), EssenceType.GELUM, 12, EssenceType.AQUA, 8),
        synthesis(Items.HONEY_BOTTLE, List.of(ModItems.PURIFIED_WATER, ModItems.CRYSTALS.get(EssenceType.ORDO)),
            new ItemStack(ModItems.PURIFYING_FLASK, 2), EssenceType.ORDO, 12, EssenceType.AQUA, 8),
        // Intermediate resources from the Transmutator are useful as Matrix bases.
        synthesis(Items.FLINT, List.of(Items.IRON_NUGGET, Items.FEATHER), new ItemStack(Items.ARROW, 8),
            EssenceType.METALLUM, 4, EssenceType.AER, 4),
        synthesis(Items.GOLD_NUGGET, List.of(Items.IRON_INGOT, Items.REDSTONE), new ItemStack(Items.POWERED_RAIL, 6),
            EssenceType.METALLUM, 8, EssenceType.POTENTIA, 6),
        synthesis(Items.GLOWSTONE_DUST, List.of(Items.QUARTZ, Items.PRISMARINE_SHARD), new ItemStack(Items.SEA_LANTERN),
            EssenceType.LUX, 10, EssenceType.AQUA, 6),
        synthesis(Items.NETHER_BRICK, List.of(Items.BLAZE_POWDER, Items.MAGMA_CREAM), new ItemStack(Items.MAGMA_BLOCK, 2),
            EssenceType.IGNIS, 10, EssenceType.TERRA, 4),
        synthesis(Items.SOUL_SOIL, List.of(Items.BONE, Items.GLOWSTONE_DUST), new ItemStack(Items.SOUL_LANTERN, 4),
            EssenceType.MORTUUS, 8, EssenceType.LUX, 6),
        synthesis(Items.IRON_NUGGET, List.of(Items.COPPER_INGOT, Items.REDSTONE), new ItemStack(Items.RAIL, 12),
            EssenceType.METALLUM, 8, EssenceType.POTENTIA, 4)
    );

    private static MatrixRecipe synthesis(Item base, List<Item> components, ItemStack output, Object... essence) {
        Map<EssenceType, Integer> cost = new LinkedHashMap<>();
        for (int i = 0; i < essence.length; i += 2) {
            cost.put((EssenceType) essence[i], (Integer) essence[i + 1]);
        }
        return new MatrixRecipe(base, List.copyOf(components), output,
            Collections.unmodifiableMap(cost));
    }

    public static List<MatrixRecipe> all() { return ALL; }

    /** Components are a multiset: order does not matter, duplicates have to be paid for. */
    public List<Item> missingComponents(List<ItemStack> given) {
        List<Item> pool = new ArrayList<>();
        List<Item> missing = new ArrayList<>();
        for (ItemStack stack : given) if (!stack.isEmpty()) pool.add(stack.getItem());
        for (Item component : components) if (!pool.remove(component)) missing.add(component);
        return missing;
    }

    public boolean accepts(List<ItemStack> given) {
        return missingComponents(given).isEmpty();
    }

    /** Stable identity includes the ingredients, output and price; never an index in the list. */
    public String key() {
        List<String> parts = new ArrayList<>();
        for (Item component : components) parts.add(Registries.ITEM.getId(component).toString());
        Collections.sort(parts);
        List<String> prices = new ArrayList<>();
        cost.forEach((type, amount) -> prices.add(type.asString() + "=" + amount));
        Collections.sort(prices);
        return Registries.ITEM.getId(base) + "|" + String.join(",", parts) + "|"
            + Registries.ITEM.getId(output.getItem()) + "*" + output.getCount() + "|" + String.join(",", prices);
    }

    /** Extra unrelated pedestal items are allowed, but two complete matches are ambiguous. */
    public static List<MatrixRecipe> matches(ItemStack base, List<ItemStack> given) {
        List<MatrixRecipe> matches = new ArrayList<>();
        if (base == null || base.isEmpty()) return matches;
        for (MatrixRecipe recipe : ALL) {
            if (base.getItem() == recipe.base && recipe.accepts(given)) matches.add(recipe);
        }
        return matches;
    }

    public static MatrixRecipe find(ItemStack base, List<ItemStack> given) {
        List<MatrixRecipe> matches = matches(base, given);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    /** A base-only preview is safe only when the base has exactly one possible recipe. */
    public static MatrixRecipe uniqueForBase(ItemStack base) {
        if (base == null || base.isEmpty()) return null;
        MatrixRecipe result = null;
        for (MatrixRecipe recipe : ALL) {
            if (base.getItem() != recipe.base) continue;
            if (result != null) return null;
            result = recipe;
        }
        return result;
    }

    public static boolean hasMultipleForBase(ItemStack base) {
        if (base == null || base.isEmpty()) return false;
        int count = 0;
        for (MatrixRecipe recipe : ALL) if (base.getItem() == recipe.base && ++count > 1) return true;
        return false;
    }

    public static int price(Map<EssenceType, Integer> cost) {
        int total = 0;
        for (int amount : cost.values()) total += amount;
        return total;
    }
}
