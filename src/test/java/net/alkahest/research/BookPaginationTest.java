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
    @Test void zeroCapacityAndEmptyEntriesStillHaveAPage() {
        assertEquals(1, BookPagination.pages(0, 0));
        assertEquals(5, BookPagination.pages(5, 0));
        assertEquals(4, BookPagination.index(4, 0, 0));
    }
}
