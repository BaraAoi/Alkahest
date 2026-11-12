package net.alkahest.research;

/** Pure page arithmetic shared by both journal sections. A page always has at least one slot. */
public final class BookPagination {
    private BookPagination() { }
    public static int pages(int items, int slots) {
        int capacity = Math.max(1, slots);
        return Math.max(1, (Math.max(0, items) + capacity - 1) / capacity);
    }
    public static int index(int page, int slot, int slots) {
        return Math.max(0, page) * Math.max(1, slots) + Math.max(0, slot);
    }
}
