package net.alkahest.transmutation;

import net.alkahest.essence.EssenceType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-to-one transmutations of the Transmutator. Every recipe turns a single input item into a
 * single output item and charges a fixed amount of essence, which the machine drains from the
 * jars standing around its pillars.
 */
public record TransmutationRecipe(ItemStack input, ItemStack output, Map<EssenceType, Integer> cost) {
    private static final List<TransmutationRecipe> ALL = List.of(
        recipe(Items.SAND, Items.GLASS, EssenceType.IGNIS, 2),
        recipe(Items.COBBLESTONE, Items.STONE, EssenceType.TERRA, 2),
        recipe(Items.STONE, Items.DEEPSLATE, EssenceType.TERRA, 2, EssenceType.PERDITIO, 2),
        recipe(Items.DEEPSLATE, Items.CALCITE, EssenceType.ORDO, 2, EssenceType.AER, 1),
        recipe(Items.COPPER_INGOT, Items.GOLD_INGOT, EssenceType.METALLUM, 3, EssenceType.ORDO, 2),
        recipe(Items.IRON_INGOT, Items.GOLD_INGOT, EssenceType.METALLUM, 2, EssenceType.ORDO, 3),
        recipe(Items.GOLD_INGOT, Items.EMERALD, EssenceType.METALLUM, 4, EssenceType.VITIUM, 2),
        recipe(Items.COAL, Items.DIAMOND, EssenceType.METALLUM, 4, EssenceType.LUX, 4),
        recipe(Items.ROTTEN_FLESH, Items.LEATHER, EssenceType.MORTUUS, 2, EssenceType.HERBA, 2),
        recipe(Items.CLAY_BALL, Items.IRON_NUGGET, EssenceType.METALLUM, 2),
        recipe(Items.QUARTZ, Items.AMETHYST_SHARD, EssenceType.LUX, 2, EssenceType.VITIUM, 1)
    );

    private static TransmutationRecipe recipe(net.minecraft.item.Item input, net.minecraft.item.Item output,
                                              Object... essence) {
        Map<EssenceType, Integer> cost = new LinkedHashMap<>();
        for (int i = 0; i < essence.length; i += 2) {
            cost.put((EssenceType) essence[i], (Integer) essence[i + 1]);
        }
        return new TransmutationRecipe(new ItemStack(input), new ItemStack(output),
            java.util.Collections.unmodifiableMap(cost));
    }

    public static List<TransmutationRecipe> all() { return ALL; }

    public static TransmutationRecipe find(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (TransmutationRecipe recipe : ALL) {
            if (ItemStack.areItemsEqual(recipe.input, stack)) return recipe;
        }
        return null;
    }

    public static int cost(Map<EssenceType, Integer> cost) {
        int total = 0;
        for (int amount : cost.values()) total += amount;
        return total;
    }
}
