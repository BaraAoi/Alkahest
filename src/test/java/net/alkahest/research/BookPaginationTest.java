package net.alkahest.research;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookPaginationTest {
    @Test void pagesCoverEveryLineExactlyOnce() {
        for (int items = 0; items < 100; items++) for (int slots = 1; slots < 20; slots++) {
            int pages = BookPagination.pages(items, slots);
            assertTrue(pages >= 1);
            boolean[] seen = new boolean[items];
            for (int page = 0; page < pages; page++) for (int slot = 0; slot < slots; slot++) {
                int index = BookPagination.index(page, slot, slots);
                if (index < items) { assertFalse(seen[index]); seen[index] = true; }
            }
            for (boolean line : seen) assertTrue(line);
        }
    }
    @Test void illustratedAndTextSpreadsNeverLoseOrRepeatLines() {
        for (int lines = 0; lines < 100; lines++) for (int rows = 1; rows < 12; rows++) for (int diagrams = 0; diagrams < 5; diagrams++) {
            boolean[] seen = new boolean[lines];
            int spreads = BookPagination.detailSpreads(lines, rows, diagrams);
            assertTrue(spreads >= Math.max(1, diagrams));
            for (int page = 0; page < spreads; page++) for (int column = 0; column < 2; column++) {
                int start = BookPagination.detailStart(page, column, rows, diagrams);
                if (page < diagrams && column == 1) { assertEquals(-1, start); continue; }
                for (int i = 0; i < rows && start + i < lines; i++) {
                    assertFalse(seen[start + i]); seen[start + i] = true;
                }
            }
            for (boolean found : seen) assertTrue(found);
        }
    }

    @Test void zeroCapacityAndEmptyEntriesStillHaveAPage() {
        assertEquals(1, BookPagination.pages(0, 0));
        assertEquals(5, BookPagination.pages(5, 0));
        assertEquals(4, BookPagination.index(4, 0, 0));
    }
}
