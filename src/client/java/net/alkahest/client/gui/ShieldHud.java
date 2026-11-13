package net.alkahest.client.gui;

import net.alkahest.shield.ShieldBarLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** The shield is independent of vanilla absorption and displayed over the heart/armor rows. */
public final class ShieldHud {
    private static float shield = -1;
    private static int maxShield;
    public static void receive(float value, int max, int charge) {
        shield = value; maxShield = max;
        // Trinkets may not notice stacks mutated inside its own tick. Mirror the authoritative
        // charge and points so the equipped item's tooltip and bar remain current.
        var player = MinecraftClient.getInstance().player;
        if (value >= 0 && player != null) dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var pair : c.getEquipped(s -> s.getItem() instanceof net.alkahest.item.custom.ShieldRingItem)) {
                pair.getRight().set(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, charge);
                pair.getRight().set(net.alkahest.registry.ModDataComponents.SHIELD_POINTS, value);
            }
        });
    }
    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (shield < 0 || maxShield <= 0 || client.player == null || client.options.hudHidden || client.player.isSpectator()
                || client.interactionManager == null || !client.interactionManager.hasStatusBars()) return;
        var player = client.player;
        var window = client.getWindow();
        var p = ShieldBarLayout.calculate(window.getScaledWidth(), window.getScaledHeight(), player.getMaxHealth(),
            player.getAbsorptionAmount(), player.getArmor() > 0, shield, maxShield);
        int x=p.x(), y=p.y(), end=x+p.width();
        // Inset shield emblem and metallic border, readable even on bright terrain.
        context.fill(x, y+1, x+5, y+5, 0xFF152A3D);
        context.fill(x+1, y, x+4, y+5, 0xFF7FC7E2);
        context.fill(x+2, y+1, x+3, y+4, 0xFFE0FAFF);
        context.fill(x+2, y+5, x+3, y+6, 0xFF7FC7E2);
        int left=x+8;
        context.fill(left-1, y, end, y+6, 0xFF102330);
        context.fill(left, y+1, end-1, y+5, 0xFF2A4757);
        int filled=p.fillWidth();
        if (filled > 0) {
            context.fill(left, y+1, left+filled, y+2, 0xFFD7F9FF);
            context.fill(left, y+2, left+filled, y+4, 0xFF77CDE8);
            context.fill(left, y+4, left+filled, y+5, 0xFF3989AF);
        }
        // One notch per HP, scaled by the actual tier, without obscuring the fill.
        for (int i=1; i<maxShield; i++) {
            int mark=left + i*(p.width()-10)/maxShield;
            context.fill(mark, y+4, mark+1, y+5, 0xFF254657);
        }
    }
    private ShieldHud() { }
}
