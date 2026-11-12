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
    public Rect detailContent() {
        return new Rect(detail.x + 18, detail.y + 24, Math.max(1, detail.width - 36), Math.max(1, detail.height - 60));
    }
}
