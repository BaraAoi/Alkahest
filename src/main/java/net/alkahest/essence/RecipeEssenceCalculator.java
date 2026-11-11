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
    public record Recipe(String id, String output, int count, List<List<String>> slots) { }
    public static Map<String, Map<EssenceType, Double>> calculate(
            Map<String, Map<EssenceType, Double>> seeds, List<Recipe> recipes) {
        Map<String, Map<EssenceType, Double>> known = new HashMap<>();
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
                for (List<String> slot : recipe.slots()) {
                    String selected = slot.stream().filter(known::containsKey).sorted().findFirst().orElse(null);
                    if (selected == null) { ready = false; break; }
                    known.get(selected).forEach((type, amount) -> sum.merge(type, amount, Double::sum));
                }
                if (!ready || sum.isEmpty()) continue;
                // Keep fractions and large intermediate values: a later recipe may divide them.
                sum.replaceAll((type, amount) -> amount / recipe.count());
                known.put(recipe.output(), Map.copyOf(sum));
                changed = true;
            }
        } while (changed);
        return Map.copyOf(known);
    }

    /** Round only the final item. Limit to the cauldron's per-type capacity, and retain
     * at least one aspect for a non-empty fractional recipe. */
    public static List<EssenceStack> rounded(Map<EssenceType, Double> amounts) {
        List<EssenceStack> result = new ArrayList<>();
        EssenceType largest = null;
        double maximum = 0;
        for (EssenceType type : EssenceType.values()) {
            double amount = amounts.getOrDefault(type, 0.0);
            if (amount > maximum) { maximum = amount; largest = type; }
            int rounded = (int) Math.min(256, Math.max(0, Math.round(amount)));
            if (rounded > 0) result.add(new EssenceStack(type, rounded, 1.0f));
        }
        if (result.isEmpty() && largest != null) result.add(new EssenceStack(largest, 1, 1.0f));
        return List.copyOf(result);
    }
    private RecipeEssenceCalculator() { }
}
