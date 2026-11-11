package net.alkahest.essence;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static net.alkahest.essence.EssenceType.*;
import static net.alkahest.essence.RecipeEssenceCalculator.*;

class RecipeEssenceCalculatorTest {
    @Test void sumsEachSlotDividesOutputsAndRetainsFractionUntilFinalItem() {
        var seed = Map.of("wood", Map.of(HERBA, 4.0), "iron", Map.of(METALLUM, 6.0));
        var recipes = List.of(new Recipe("a", "plank", 4, List.of(List.of("wood"))),
            new Recipe("b", "part", 2, List.of(List.of("plank"), List.of("plank"), List.of("iron"))));
        var result = calculate(seed, recipes);
        assertEquals(1.0, result.get("plank").get(HERBA));
        assertEquals(1.0, result.get("part").get(HERBA));
        assertEquals(3.0, result.get("part").get(METALLUM));
    }
    @Test void cyclesAndMissingMaterialsStayUnresolvedAndNeverInventAnAspect() {
        var result = calculate(Map.of("base", Map.of(ORDO, 1.0)), List.of(
            new Recipe("a", "x", 1, List.of(List.of("y"))),
            new Recipe("b", "y", 1, List.of(List.of("x"))),
            new Recipe("c", "unknown_product", 1, List.of(List.of("unassigned")))));
        assertEquals(Map.of("base", Map.of(ORDO, 1.0)), result);
    }
    @Test void deterministicAlternativesAndExplicitSeedsWin() {
        var seeds = Map.of("a", Map.of(ORDO, 2.0), "z", Map.of(IGNIS, 4.0), "fixed", Map.of(AQUA, 3.0));
        var recipes = List.of(new Recipe("z", "out", 1, List.of(List.of("z"))),
            new Recipe("a", "out", 1, List.of(List.of("z", "a"))),
            new Recipe("b", "fixed", 1, List.of(List.of("z"))));
        assertEquals(Map.of(ORDO, 2.0), calculate(seeds, recipes).get("out"));
        assertEquals(Map.of(AQUA, 3.0), calculate(seeds, recipes).get("fixed"));
    }
    @Test void multiStepFractionsAreNotRoundedOrCappedUntilTheEnd() {
        var values = calculate(Map.of("source", Map.of(AQUA, 1.0)), List.of(
            new Recipe("a", "many", 4, List.of(List.of("source"))),
            new Recipe("b", "combined", 1, List.of(List.of("many"), List.of("many"), List.of("many")))));
        assertEquals(.75, values.get("combined").get(AQUA));
        assertEquals(List.of(new EssenceStack(AQUA, 1, 1f)), rounded(values.get("combined")));
    }
    @Test void emptyOrUnknownSlotAndZeroCountDoNotProduceItems() {
        var values = calculate(Map.of("base", Map.of(AQUA, 1.0)), List.of(
            new Recipe("a", "missing", 1, List.of(List.of("base"), List.of())),
            new Recipe("b", "zero", 0, List.of(List.of("base")))));
        assertFalse(values.containsKey("missing"));
        assertFalse(values.containsKey("zero"));
    }
    @Test void subtractsReturnedContainersAndRequiresTheirKnownComposition() {
        var seeds = Map.of("milk", Map.of(METALLUM, 6.0, AQUA, 3.0), "bucket", Map.of(METALLUM, 6.0));
        var recipe = new Recipe("cake", "food", 1, List.of(List.of("milk")), Map.of("milk", "bucket"));
        assertEquals(Map.of(AQUA, 3.0), calculate(seeds, List.of(recipe)).get("food"));
        assertFalse(calculate(Map.of("milk", seeds.get("milk")), List.of(recipe)).containsKey("food"));
    }
    @Test void integerBoundsAndRounding() {
        assertEquals(List.of(new EssenceStack(AQUA, 1, 1f)), rounded(Map.of(AQUA, .1)));
        assertEquals(List.of(new EssenceStack(ORDO, 256, 1f)), rounded(Map.of(ORDO, 9999.0)));
        assertTrue(rounded(Map.of()).isEmpty());
    }
}
