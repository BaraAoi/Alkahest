package net.alkahest.research;

import java.util.List;

/** Former field-guide chapters belong to research entries, not a separate navigation mode. */
public final class ResearchJournalContent {
    public enum Catalogue { NONE, CAULDRON, MATRIX, TRANSMUTATOR }
    public static List<Integer> chapters(String research) {
        return switch (research) {
            case "cauldron" -> List.of(0, 1, 2);
            case "distillery" -> List.of(4);
            case "fire_dust" -> List.of(5);
            case "monocle" -> List.of(6);
            case "binding_salt" -> List.of(7);
            case "alkahest" -> List.of(8);
            default -> List.of();
        };
    }
    public static Catalogue catalogue(String research) {
        return switch (research) {
            case "cauldron" -> Catalogue.CAULDRON;
            case "matrix" -> Catalogue.MATRIX;
            case "transmutator" -> Catalogue.TRANSMUTATOR;
            default -> Catalogue.NONE;
        };
    }
    private ResearchJournalContent() { }
}
