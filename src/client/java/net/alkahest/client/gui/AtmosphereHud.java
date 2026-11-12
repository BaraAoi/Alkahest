package net.alkahest.client.gui;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.atmosphere.AtmosphereMath;
import net.alkahest.atmosphere.AtmosphereViewState;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.util.Locale;

/** Optional, quiet current-cell panel; values are supplied by the server, never reconstructed client-side. */
public final class AtmosphereHud {
    private static final AtmosphereViewState VIEW = new AtmosphereViewState();
    private static KeyBinding toggle;
    private static boolean enabled;
    private static net.alkahest.atmosphere.AtmosphereReading rendered;
    private static java.util.List<String> cachedValues;
    // Legible tints in the fixed order Ignis, Aqua, Terra, Aer, Ordo, Perditio.
    private static final int[] COLORS = {0xFFFF7035, 0xFF65B8FF, 0xFFC89A6A, 0xFFE9F8FF, 0xFFFFDE72, 0xFFC58AF2};
    public static void register() {
        toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.alkahest.atmosphere",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.alkahest"));
    }
    public static void tick(MinecraftClient client) {
        VIEW.tick();
        while (toggle != null && toggle.wasPressed())
            if (client.currentScreen == null && client.player != null) enabled = !enabled;
    }
    public static void receive(String data) { VIEW.receive(data); }
    public static void clear() { VIEW.clear(); enabled = false; rendered = null; cachedValues = null; }
    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null || client.options.hudHidden || client.currentScreen != null
            || !TrinketsApi.getTrinketComponent(client.player).map(c -> c.isEquipped(ModItems.ALCHEMISTS_MONOCLE)).orElse(false)) return;
        var reading = VIEW.forPosition(client.world.getRegistryKey().getValue().toString(), client.player.getBlockPos());
        // Never show an old cell or fabricate values while waiting for a new snapshot.
        if (reading.isEmpty()) return;
        var snapshot = reading.get();
        if (!snapshot.equals(rendered)) {
            var updated = new java.util.ArrayList<String>(AtmosphereMath.PRIMALS.size());
            for (double value : snapshot.levels()) updated.add(String.format(Locale.ROOT, "%.1f", value));
            rendered = snapshot; cachedValues = java.util.List.copyOf(updated);
        }
        // Only six coloured numbers: no plate, frame, labels, coordinates, symbols or chance.
        for (int i = 0; i < cachedValues.size(); i++) {
            int x = 7 + (i % 3) * 42, y = 7 + (i / 3) * 14;
            context.drawTextWithShadow(client.textRenderer, cachedValues.get(i), x, y, COLORS[i]);
        }
    }
    private AtmosphereHud() { }
}
