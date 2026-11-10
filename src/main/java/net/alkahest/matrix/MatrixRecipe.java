package net.alkahest.matrix;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
            EssenceType.METALLUM, 8, EssenceType.LUX, 6, EssenceType.ORDO, 4)
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

    public static MatrixRecipe find(ItemStack base, List<ItemStack> given) {
        if (base == null || base.isEmpty()) return null;
        for (MatrixRecipe recipe : ALL) {
            if (base.getItem() != recipe.base) continue;
            if (recipe.accepts(given)) return recipe;
        }
        return null;
    }

    /** The cheapest recipe for the base item, used to explain what is still missing. */
    public static MatrixRecipe cheapestFor(ItemStack base) {
        if (base == null || base.isEmpty()) return null;
        for (MatrixRecipe recipe : ALL) if (base.getItem() == recipe.base) return recipe;
        return null;
    }

    public static int price(Map<EssenceType, Integer> cost) {
        int total = 0;
        for (int amount : cost.values()) total += amount;
        return total;
    }
}
