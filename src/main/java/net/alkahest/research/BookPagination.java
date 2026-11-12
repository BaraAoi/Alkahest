package net.alkahest.research;

/** Pure page arithmetic shared by both journal sections. A page always has at least one slot. */
public final class BookPagination {
    private BookPagination() { }
    public static int pages(int items, int slots) {
        int capacity = Math.max(1, slots);
        return Math.max(1, (Math.max(0, items) + capacity - 1) / capacity);
    }
    /** Each illustrated spread reserves the right page; later spreads use both pages for prose. */
    public static int detailSpreads(int lines, int rows, int diagrams) {
        int r = Math.max(1, rows), d = Math.max(0, diagrams);
        int remaining = Math.max(0, lines - d * r);
        return Math.max(1, d + (remaining == 0 ? 0 : pages(remaining, 2 * r)));
    }
    /** Returns the first prose line for a page, or -1 if that page is reserved for an illustration. */
    public static int detailStart(int spread, int column, int rows, int diagrams) {
        int r = Math.max(1, rows), d = Math.max(0, diagrams), p = Math.max(0, spread);
        if (p < d) return column == 0 ? p * r : -1;
        return d * r + (p - d) * 2 * r + (column == 0 ? 0 : r);
    }
    public static int index(int page, int slot, int slots) {
        return Math.max(0, page) * Math.max(1, slots) + Math.max(0, slot);
    }
}
