package net.alkahest.essence;

/** Vanilla-container-style comparator signal: empty 0, nonempty 1..14, full 15. */
public final class EssenceComparator {
    private EssenceComparator() {}

    public static int signal(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return 0;
        return 1 + (int)(14L * Math.min(amount, capacity) / capacity);
    }
}
