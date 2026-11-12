package net.alkahest.research;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static net.alkahest.research.ResearchJournalContent.Catalogue.*;

class ResearchJournalContentTest {
    @Test void formerGuideAndCataloguesAreReachableThroughRelevantNodes() {
        var chapters = new HashSet<Integer>();
        for (var step : PracticalResearchPlan.STEPS) chapters.addAll(ResearchJournalContent.chapters(step.id()));
        assertEquals(java.util.Set.of(0, 1, 2, 4, 5, 6, 7, 8), chapters);
        assertEquals(CAULDRON, ResearchJournalContent.catalogue("cauldron"));
        assertEquals(MATRIX, ResearchJournalContent.catalogue("matrix"));
        assertEquals(TRANSMUTATOR, ResearchJournalContent.catalogue("transmutator"));
        assertEquals(NONE, ResearchJournalContent.catalogue("water"));
    }
    @Test void mapUsesViewportAndModalFitsAtSeveralGuiSizes() {
        for (int[] size : new int[][]{{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            var layout = JournalLayout.of(size[0], size[1]);
            var map = layout.map(); var book = layout.detail(); var content = layout.detailContent();
            assertTrue(map.width() >= size[0] - 20 && map.height() >= size[1] - 61);
            assertTrue(book.x() >= 0 && book.y() >= 0);
            assertTrue(book.x() + book.width() <= size[0] && book.y() + book.height() <= size[1]);
            assertTrue(content.x() > book.x() && content.y() > book.y());
            assertTrue(content.x() + content.width() < book.x() + book.width());
            assertTrue(content.y() + content.height() < book.y() + book.height());
            assertTrue(book.contains(book.x(), book.y()));
            assertFalse(book.contains(book.x() + book.width(), book.y()));
        }
    }
}
