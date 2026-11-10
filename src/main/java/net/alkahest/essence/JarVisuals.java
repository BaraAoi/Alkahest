package net.alkahest.essence;

/** Deterministic appearance for a normal jar; independent of client-only classes. */
public final class JarVisuals {
    public static final int CAPACITY = 100;
    public static final int LEVELS = 10;
    /** Empty is zero; even one unit is visible. Invalid oversized data is safely clamped. */
    public static int level(JarContentsComponent contents) {
        return Math.min(LEVELS, (Math.max(0, contents.total()) + 9) / 10);
    }
    /** RGB weighted by quantity; no contents is white. */
    public static int color(JarContentsComponent contents) {
        int total = contents.total();
        if (total <= 0) return 0xffffff;
        long r = 0, g = 0, b = 0;
        for (var essence : contents.contents()) {
            int c = essence.type().color(), n = essence.amount();
            r += (long) ((c >> 16) & 255) * n;
            g += (long) ((c >> 8) & 255) * n;
            b += (long) (c & 255) * n;
        }
        return ((int)(r / total) << 16) | ((int)(g / total) << 8) | (int)(b / total);
    }
    private JarVisuals() { }
}
