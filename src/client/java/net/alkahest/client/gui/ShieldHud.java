package net.alkahest.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.Locale;

/** Separate from vanilla absorption hearts, so effects from other sources are never overwritten. */
public final class ShieldHud {
    private static float shield = -1;
    public static void receive(float value, int charge) {
        shield = value;
        // Trinkets may not notice stacks mutated inside its own tick: keep the equipped tooltip
        // and charge bar aligned with the authoritative server packet as well as the HUD.
        var player = MinecraftClient.getInstance().player;
        if (value >= 0 && player != null) dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var pair : c.getEquipped(net.alkahest.registry.ModItems.RING_SHIELD)) {
                pair.getRight().set(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, charge);
                pair.getRight().set(net.alkahest.registry.ModDataComponents.SHIELD_POINTS, value);
            }
        });
    }
    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (shield < 0 || client.player == null || client.options.hudHidden || client.player.isSpectator()) return;
        Text text = Text.translatable("hud.alkahest.shield", String.format(Locale.ROOT, "%.1f", shield));
        context.drawTextWithShadow(client.textRenderer, text, 8, 8, 0xFFE6E9FF);
    }
    private ShieldHud() { }
}
