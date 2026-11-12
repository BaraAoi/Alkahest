package net.alkahest.mixin;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla transform recipes do not reliably expose their three slots via Recipe#getIngredients. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
    @Accessor("template") Ingredient alkahest$template();
    @Accessor("base") Ingredient alkahest$base();
    @Accessor("addition") Ingredient alkahest$addition();
}
