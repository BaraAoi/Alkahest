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
public record TransmutationRecipe(ItemStack input, ItemStack output, Map<EssenceType, Integer> cost, int duration) {
    public static final int DEFAULT_DURATION = 100;
    private static final TransmutationRecipe IRON_DIAMOND = recipeWithDuration(Items.IRON_INGOT, Items.DIAMOND, 2400,
        EssenceType.TERRA, 50, EssenceType.LUX, 30, EssenceType.ORDO, 25, EssenceType.METALLUM, 15);
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
        recipe(Items.QUARTZ, Items.AMETHYST_SHARD, EssenceType.LUX, 2, EssenceType.VITIUM, 1),
        // Intermediate materials for later Matrix syntheses, always one input to one output.
        recipe(Items.GRAVEL, Items.FLINT, EssenceType.TERRA, 2, EssenceType.ORDO, 1),
        recipe(Items.FLINT, Items.QUARTZ, EssenceType.TERRA, 3, EssenceType.ORDO, 2),
        recipe(Items.REDSTONE, Items.GLOWSTONE_DUST, EssenceType.LUX, 4, EssenceType.POTENTIA, 3),
        recipe(Items.NETHERRACK, Items.NETHER_BRICK, EssenceType.IGNIS, 3, EssenceType.TERRA, 2),
        recipe(Items.SOUL_SAND, Items.SOUL_SOIL, EssenceType.MORTUUS, 3, EssenceType.PERDITIO, 2),
        recipe(Items.IRON_NUGGET, Items.GOLD_NUGGET, EssenceType.METALLUM, 2, EssenceType.ORDO, 1),
        IRON_DIAMOND
    );

    private static TransmutationRecipe recipe(net.minecraft.item.Item input, net.minecraft.item.Item output,
                                              Object... essence) {
        return recipeWithDuration(input, output, DEFAULT_DURATION, essence);
    }

    private static TransmutationRecipe recipeWithDuration(net.minecraft.item.Item input, net.minecraft.item.Item output,
                                                          int duration, Object... essence) {
        Map<EssenceType, Integer> cost = new LinkedHashMap<>();
        for (int i = 0; i < essence.length; i += 2) {
            cost.put((EssenceType) essence[i], (Integer) essence[i + 1]);
        }
        return new TransmutationRecipe(new ItemStack(input), new ItemStack(output),
            java.util.Collections.unmodifiableMap(cost), duration);
    }

    public static List<TransmutationRecipe> all() { return ALL; }

    public static TransmutationRecipe find(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (TransmutationRecipe recipe : ALL) {
            if (ItemStack.areItemsEqual(recipe.input, stack)) return recipe;
        }
        return null;
    }

    /** Alternative affects only iron; old worlds and other inputs keep their existing recipe. */
    public static TransmutationRecipe find(ItemStack stack, boolean ironDiamond) {
        return ironDiamond && stack != null && stack.isOf(Items.IRON_INGOT) ? IRON_DIAMOND : find(stack);
    }

    public static int cost(Map<EssenceType, Integer> cost) {
        int total = 0;
        for (int amount : cost.values()) total += amount;
        return total;
    }
}
