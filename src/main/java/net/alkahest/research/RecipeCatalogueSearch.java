package net.alkahest.research;

import net.alkahest.essence.EssenceType;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.transmutation.TransmutationRecipe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Uses the live registries and localized stack names: never a duplicate list of recipes. */
public final class RecipeCatalogueSearch {
    private RecipeCatalogueSearch() { }

    private static void item(List<String> fields, ItemStack stack) {
        fields.add(stack.getName().getString());
        fields.add(Registries.ITEM.getId(stack.getItem()).toString());
    }
    private static void cost(List<String> fields, Map<EssenceType, Integer> cost) {
        for (EssenceType type : cost.keySet()) fields.add(type.displayName());
    }
    public static boolean matches(String query, MatrixRecipe recipe) {
        List<String> fields = new ArrayList<>();
        item(fields, new ItemStack(recipe.base()));
        for (Item part : recipe.components()) item(fields, new ItemStack(part));
        item(fields, recipe.output());
        cost(fields, recipe.cost());
        return CatalogueSearch.matches(query, fields);
    }
    public static boolean matches(String query, TransmutationRecipe recipe) {
        List<String> fields = new ArrayList<>();
        item(fields, recipe.input());
        item(fields, recipe.output());
        cost(fields, recipe.cost());
        return CatalogueSearch.matches(query, fields);
    }
}
