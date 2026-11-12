package net.alkahest.essence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure, deterministic calculation for single-output crafting recipes. */
public final class RecipeEssenceCalculator {
    /** Each ingredient slot contains alternative item IDs; empty grid positions are omitted. */
    public record Recipe(String id, String output, int count, List<List<String>> slots, Map<String, String> remainders) {
        public Recipe(String id, String output, int count, List<List<String>> slots) {
            this(id, output, count, slots, Map.of());
        }
    }
    public record Calculation(Map<String, Map<EssenceType, Double>> values, Map<String, Integer> depths) { }
    public static Map<String, Map<EssenceType, Double>> calculate(
            Map<String, Map<EssenceType, Double>> seeds, List<Recipe> recipes) {
        return calculateDetailed(seeds, recipes).values();
    }
    /** Depth counts the longest known consumed-ingredient chain, not guesses about loot rarity. */
    public static Calculation calculateDetailed(Map<String, Map<EssenceType, Double>> seeds, List<Recipe> recipes) {
        Map<String, Map<EssenceType, Double>> known = new HashMap<>();
        Map<String, Integer> depths = new HashMap<>();
        seeds.forEach((id, map) -> { if (!map.isEmpty()) known.put(id, Map.copyOf(map)); });
        List<Recipe> ordered = new ArrayList<>(recipes);
        ordered.sort(Comparator.comparing(Recipe::id));
        boolean changed;
        do {
            changed = false;
            for (Recipe recipe : ordered) {
                if (known.containsKey(recipe.output()) || recipe.count() <= 0 || recipe.slots().isEmpty()) continue;
                EnumMap<EssenceType, Double> sum = new EnumMap<>(EssenceType.class);
                boolean ready = true;
                int maxDepth = 0, returnedOutput = 0;
                for (List<String> slot : recipe.slots()) {
                    // Template duplication: an unambiguous copy of the output is consumed and
                    // returned among the results. Infer only the genuinely new copies.
                    if (slot.size() == 1 && slot.getFirst().equals(recipe.output())
                            && !recipe.remainders().containsKey(recipe.output())) {
                        returnedOutput++;
                        continue;
                    }
                    String selected = slot.stream().filter(known::containsKey)
                        .filter(id -> !recipe.remainders().containsKey(id) || known.containsKey(recipe.remainders().get(id)))
                        .sorted().findFirst().orElse(null);
                    if (selected == null) { ready = false; break; }
                    Map<EssenceType, Double> returned = recipe.remainders().containsKey(selected)
                        ? known.get(recipe.remainders().get(selected)) : Map.of();
                    if (known.get(selected).entrySet().stream().anyMatch(e -> e.getValue() > returned.getOrDefault(e.getKey(), 0.0)))
                        maxDepth = Math.max(maxDepth, depths.getOrDefault(selected, 0));
                    known.get(selected).forEach((type, amount) -> {
                        double consumed = Math.max(0.0, amount - returned.getOrDefault(type, 0.0));
                        if (consumed > 0) sum.merge(type, consumed, Double::sum);
                    });
                }
                int netOutput = recipe.count() - returnedOutput;
                if (!ready || sum.isEmpty() || netOutput <= 0) continue;
                // Keep fractions and large intermediate values: a later recipe may divide them.
                sum.replaceAll((type, amount) -> amount / netOutput);
                known.put(recipe.output(), Map.copyOf(sum));
                depths.put(recipe.output(), Math.min(7, maxDepth + 1));
                changed = true;
            }
        } while (changed);
        return new Calculation(Map.copyOf(known), Map.copyOf(depths));
    }

    /** Round only at the end; keep at least one aspect from a non-empty fractional recipe. */
    public static List<EssenceStack> rounded(Map<EssenceType, Double> amounts) {
        return rounded(amounts, 0);
    }
    /** Later recipe stages get a modest, non-compounding bonus; Gelum remains unchanged. */
    public static List<EssenceStack> rounded(Map<EssenceType, Double> amounts, int depth) {
        double multiplier = 1 + .1 * Math.min(6, Math.max(0, depth - 1));
        List<EssenceStack> result = new ArrayList<>();
        EssenceType largest = null;
        double maximum = 0;
        for (EssenceType type : EssenceType.values()) {
            double amount = amounts.getOrDefault(type, 0.0);
            if (amount > maximum) { maximum = amount; largest = type; }
            // The component format supports at most 500; leave Gelum's existing balance untouched.
            double adjusted = type == EssenceType.GELUM ? amount : amount * multiplier;
            int rounded = (int) Math.min(type == EssenceType.GELUM ? 256 : 500, Math.max(0, Math.round(adjusted)));
            if (rounded > 0) result.add(new EssenceStack(type, rounded, 1.0f));
        }
        if (result.isEmpty() && largest != null) result.add(new EssenceStack(largest, 1, 1.0f));
        return List.copyOf(result);
    }
    private RecipeEssenceCalculator() { }
}
