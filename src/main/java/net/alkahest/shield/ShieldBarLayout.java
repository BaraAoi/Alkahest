package net.alkahest.shield;

/** Align the barrier with the vanilla hearts, above all heart and armor rows. */
public final class ShieldBarLayout {
    public record Placement(int x, int y, int width, int fillWidth) { }
    public static Placement calculate(int screenWidth, int screenHeight, float maxHealth, float absorption,
                                      boolean hasArmor, float points, int maxShield) {
        int rows = Math.max(1, (int)Math.ceil((Math.max(1, maxHealth) + Math.max(0, absorption)) / 20.0));
        int rowSpacing = Math.max(10 - (rows - 2), 3);
        int heartTop = screenHeight - 39 - (rows - 1) * rowSpacing;
        int y = heartTop - (hasArmor ? 10 : 0) - 8;
        int width = 81, inner = width - 10;
        float ratio = maxShield > 0 ? Math.max(0, Math.min(1, points / maxShield)) : 0;
        return new Placement(screenWidth / 2 - 91, y, width, Math.round(inner * ratio));
    }
    private ShieldBarLayout() { }
}
