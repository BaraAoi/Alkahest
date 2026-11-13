package net.alkahest.shield;

/** A shield impact lasts briefly; possession and regeneration do not trigger it. */
public final class ShieldImpactEffect {
    public static final int DURATION_TICKS = 14;
    public static boolean shouldTrigger(float absorbed) { return absorbed > 0; }
    public static float opacity(float elapsedTicks) {
        if (!Float.isFinite(elapsedTicks) || elapsedTicks < 0 || elapsedTicks >= DURATION_TICKS) return 0;
        float remaining = 1 - elapsedTicks / DURATION_TICKS;
        return remaining * remaining;
    }
    private ShieldImpactEffect() { }
}
