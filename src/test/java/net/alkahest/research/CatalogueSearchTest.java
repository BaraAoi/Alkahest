package net.alkahest.research;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueSearchTest {
    private final List<String> fields = List.of("Железный слиток", "minecraft:iron_ingot", "Алмаз", "Terra", "Metallum");

    @Test void emptyCaseAndMultipleTermsAcrossFields() {
        assertTrue(CatalogueSearch.matches("", fields));
        assertTrue(CatalogueSearch.matches("  \t ", fields));
        assertTrue(CatalogueSearch.matches("ЖЕЛЕЗ алмаз", fields));
        assertTrue(CatalogueSearch.matches("  IRON   TERRA  ", fields));
        assertTrue(CatalogueSearch.matches("minecraft:iron", fields));
        assertFalse(CatalogueSearch.matches("iron ордо", fields));
        assertFalse(CatalogueSearch.matches("золото", fields));
        assertFalse(CatalogueSearch.matches("что-то", List.of()));
    }
}
