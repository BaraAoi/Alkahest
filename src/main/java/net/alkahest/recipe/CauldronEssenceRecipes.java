package net.alkahest.recipe;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
import java.util.Map;
import static net.alkahest.essence.EssenceType.*;

/** Early cauldron recipes: a single identifiable catalyst and required dissolved essences. */
public final class CauldronEssenceRecipes {
    public record Recipe(Item catalyst, Map<EssenceType, Integer> cost, Item result, int count) {
        public boolean matches(ItemStack stack, Map<EssenceType, Integer> available) {
            return stack.isOf(catalyst) && cost.entrySet().stream().allMatch(e -> available.getOrDefault(e.getKey(), 0) >= e.getValue());
        }
    }
    public static List<Recipe> all() {
        return List.of(
            new Recipe(Items.GLASS_BOTTLE, Map.of(AQUA,1, IGNIS,2), ModItems.PURIFIED_WATER,1),
            new Recipe(Items.WHEAT_SEEDS, Map.of(HERBA,4, AQUA,2), ModItems.HERBAL_PASTE,2),
            new Recipe(Items.GUNPOWDER, Map.of(IGNIS,3, POTENTIA,3), ModItems.FIRE_DUST,2),
            new Recipe(Items.SLIME_BALL, Map.of(VITIUM,3, HERBA,3), ModItems.CAUSTIC_SLIME,2),
            new Recipe(Items.STRING, Map.of(ORDO,3, AQUA,1), ModItems.BINDING_SALT,2),
            new Recipe(Items.GLASS_PANE, Map.of(ORDO,3, LUX,2), ModItems.ALCHEMISTS_MONOCLE,1),
            new Recipe(Items.LIGHTNING_ROD, Map.of(IGNIS,4, AER,4, POTENTIA,2), ModItems.LIGHTNING_IN_A_BOTTLE,1),
            new Recipe(Items.FERMENTED_SPIDER_EYE, Map.of(MORTUUS,4, VITIUM,3, AQUA,2), ModItems.MIASMA,1)
        );
    }
    private CauldronEssenceRecipes() { }
}
