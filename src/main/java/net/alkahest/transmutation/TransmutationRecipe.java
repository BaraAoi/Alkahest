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
public record TransmutationRecipe(String id, ItemStack input, ItemStack output, Map<EssenceType, Integer> cost, int duration) {
    public static final int DEFAULT_DURATION = 100;
    private static final TransmutationRecipe IRON_DIAMOND = recipe("iron_ingot_to_diamond", Items.IRON_INGOT, Items.DIAMOND,
        EssenceType.TERRA, 1, EssenceType.LUX, 1, EssenceType.ORDO, 1, EssenceType.METALLUM, 1);
    private static final List<TransmutationRecipe> ALL = List.of(
        recipe("sand_to_glass", Items.SAND, Items.GLASS, EssenceType.IGNIS, 1),
        recipe("cobblestone_to_stone", Items.COBBLESTONE, Items.STONE, EssenceType.TERRA, 1),
        recipe("stone_to_deepslate", Items.STONE, Items.DEEPSLATE, EssenceType.TERRA, 1, EssenceType.PERDITIO, 1),
        recipe("deepslate_to_calcite", Items.DEEPSLATE, Items.CALCITE, EssenceType.ORDO, 1, EssenceType.AER, 1),
        recipe("copper_ingot_to_gold_ingot", Items.COPPER_INGOT, Items.GOLD_INGOT, EssenceType.METALLUM, 2, EssenceType.ORDO, 2),
        recipe("iron_ingot_to_gold_ingot", Items.IRON_INGOT, Items.GOLD_INGOT, EssenceType.METALLUM, 1, EssenceType.ORDO, 2),
        recipe("gold_ingot_to_emerald", Items.GOLD_INGOT, Items.EMERALD, EssenceType.METALLUM, 2, EssenceType.VITIUM, 1),
        recipe("coal_to_diamond", Items.COAL, Items.DIAMOND, EssenceType.METALLUM, 3, EssenceType.LUX, 3),
        recipe("rotten_flesh_to_leather", Items.ROTTEN_FLESH, Items.LEATHER, EssenceType.MORTUUS, 1, EssenceType.HERBA, 1),
        recipe("clay_ball_to_iron_nugget", Items.CLAY_BALL, Items.IRON_NUGGET, EssenceType.METALLUM, 1),
        recipe("quartz_to_amethyst_shard", Items.QUARTZ, Items.AMETHYST_SHARD, EssenceType.LUX, 1, EssenceType.VITIUM, 1),
        // Intermediate materials for later Matrix syntheses, always one input to one output.
        recipe("gravel_to_flint", Items.GRAVEL, Items.FLINT, EssenceType.TERRA, 1, EssenceType.ORDO, 1),
        recipe("flint_to_quartz", Items.FLINT, Items.QUARTZ, EssenceType.TERRA, 2, EssenceType.ORDO, 1),
        recipe("redstone_to_glowstone_dust", Items.REDSTONE, Items.GLOWSTONE_DUST, EssenceType.LUX, 2, EssenceType.POTENTIA, 2),
        recipe("netherrack_to_nether_brick", Items.NETHERRACK, Items.NETHER_BRICK, EssenceType.IGNIS, 2, EssenceType.TERRA, 1),
        recipe("soul_sand_to_soul_soil", Items.SOUL_SAND, Items.SOUL_SOIL, EssenceType.MORTUUS, 2, EssenceType.PERDITIO, 1),
        recipe("iron_nugget_to_gold_nugget", Items.IRON_NUGGET, Items.GOLD_NUGGET, EssenceType.METALLUM, 1, EssenceType.ORDO, 1),
        IRON_DIAMOND
    );

    static {
        var ids = new java.util.HashSet<String>();
        for (TransmutationRecipe recipe : ALL)
            if (recipe.id().isBlank() || !ids.add(recipe.id()))
                throw new IllegalStateException("Duplicate or empty transmutation recipe ID: " + recipe.id());
    }

    private static TransmutationRecipe recipe(String id, net.minecraft.item.Item input, net.minecraft.item.Item output,
                                              Object... essence) {
        return recipeWithDuration(id, input, output, DEFAULT_DURATION, essence);
    }

    private static TransmutationRecipe recipeWithDuration(String id, net.minecraft.item.Item input, net.minecraft.item.Item output,
                                                          int duration, Object... essence) {
        Map<EssenceType, Integer> cost = new LinkedHashMap<>();
        for (int i = 0; i < essence.length; i += 2) {
            cost.put((EssenceType) essence[i], (Integer) essence[i + 1]);
        }
        return new TransmutationRecipe(id, new ItemStack(input), new ItemStack(output),
            java.util.Collections.unmodifiableMap(cost), duration);
    }

    public static List<TransmutationRecipe> all() { return ALL; }

    /** Registry order defines the cycle and default, while persistent identity uses stable IDs. */
    public static List<TransmutationRecipe> choices(ItemStack stack) { return choices(ALL, stack); }
    public static List<TransmutationRecipe> choices(List<TransmutationRecipe> recipes, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        return recipes.stream().filter(recipe -> ItemStack.areItemsEqual(recipe.input, stack)).toList();
    }
    public static int selectedIndex(List<TransmutationRecipe> choices, String id) {
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(id)) return i;
        return 0; // Missing/removed recipe falls back to the current input's first recipe.
    }
    public static TransmutationRecipe selected(List<TransmutationRecipe> choices, int index) {
        if (choices.isEmpty()) return null;
        return choices.get(index >= 0 && index < choices.size() ? index : 0);
    }
    public static TransmutationRecipe next(List<TransmutationRecipe> choices, String id) {
        return choices.size() < 2 ? null : choices.get((selectedIndex(choices, id) + 1) % choices.size());
    }
    public static TransmutationRecipe find(ItemStack stack) { return selected(choices(stack), 0); }
    public static TransmutationRecipe find(ItemStack stack, int index) { return selected(choices(stack), index); }

    /** Compatibility for older journal callers and saves selecting the original iron alternative. */
    public static TransmutationRecipe find(ItemStack stack, boolean ironDiamond) {
        if (ironDiamond && stack != null && stack.isOf(Items.IRON_INGOT)) return IRON_DIAMOND;
        return find(stack);
    }

    public static int cost(Map<EssenceType, Integer> cost) {
        int total = 0;
        for (int amount : cost.values()) total += amount;
        return total;
    }
}
