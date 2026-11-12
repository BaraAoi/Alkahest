package net.alkahest.research;

/** All coordinates are in scaled Minecraft GUI pixels, shared by rendering and input. */
public record JournalLayout(Rect map, Rect detail) {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
    public static JournalLayout of(int width, int height) {
        int mapMargin = 10, header = 34, footer = 27;
        Rect map = new Rect(mapMargin, header, Math.max(1, width - 2 * mapMargin), Math.max(1, height - header - footer));
        int bookWidth = Math.max(1, Math.min(520, width - 24));
        int bookHeight = Math.max(1, Math.min(300, height - 24));
        return new JournalLayout(map, new Rect((width - bookWidth) / 2, (height - bookHeight) / 2, bookWidth, bookHeight));
    }
    /** Disjoint map strips outside the opaque modal, so no map item can be drawn through it. */
    public java.util.List<Rect> mapRegions(boolean modal) {
        if (!modal) return java.util.List.of(map);
        int x1 = Math.max(map.x, detail.x), y1 = Math.max(map.y, detail.y);
        int x2 = Math.min(map.x + map.width, detail.x + detail.width);
        int y2 = Math.min(map.y + map.height, detail.y + detail.height);
        if (x1 >= x2 || y1 >= y2) return java.util.List.of(map);
        var result = new java.util.ArrayList<Rect>();
        add(result, map.x, map.y, map.width, y1 - map.y);
        add(result, map.x, y2, map.width, map.y + map.height - y2);
        add(result, map.x, y1, x1 - map.x, y2 - y1);
        add(result, x2, y1, map.x + map.width - x2, y2 - y1);
        return java.util.List.copyOf(result);
    }
    private static void add(java.util.List<Rect> regions, int x, int y, int w, int h) {
        if (w > 0 && h > 0) regions.add(new Rect(x, y, w, h));
    }
    public Rect detailContent() {
        return new Rect(detail.x + 18, detail.y + 24, Math.max(1, detail.width - 36), Math.max(1, detail.height - 60));
    }
}
