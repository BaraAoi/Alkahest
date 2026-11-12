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
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.Locale;

/** Optional, quiet current-cell panel; values are supplied by the server, never reconstructed client-side. */
public final class AtmosphereHud {
    private static final AtmosphereViewState VIEW = new AtmosphereViewState();
    private static KeyBinding toggle;
    private static boolean enabled;
    private static net.alkahest.atmosphere.AtmosphereReading rendered;
    private static java.util.List<Text> cachedLines;
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
    public static void clear() { VIEW.clear(); enabled = false; rendered = null; cachedLines = null; }
    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled || client.player == null || client.world == null || client.options.hudHidden || client.currentScreen != null
            || !TrinketsApi.getTrinketComponent(client.player).map(c -> c.isEquipped(ModItems.ALCHEMISTS_MONOCLE)).orElse(false)) return;
        var reading = VIEW.forPosition(client.world.getRegistryKey().getValue().toString(), client.player.getBlockPos());
        java.util.List<Text> lines;
        if (reading.isEmpty()) lines = java.util.List.of(Text.translatable("screen.alkahest.atmosphere.title"),
            Text.translatable("screen.alkahest.atmosphere.waiting"));
        else {
            var snapshot = reading.get();
            if (!snapshot.equals(rendered)) {
                var updated = new ArrayList<Text>();
                updated.add(Text.translatable("screen.alkahest.atmosphere.title"));
                updated.add(Text.translatable("screen.alkahest.atmosphere.cell", snapshot.x(), snapshot.y(), snapshot.z()));
                for (int i = 0; i < AtmosphereMath.PRIMALS.size(); i++) {
                    var type = AtmosphereMath.PRIMALS.get(i);
                    updated.add(Text.translatable("screen.alkahest.atmosphere.value", type.displayName(),
                        String.format(Locale.ROOT, "%.1f", snapshot.levels().get(i)),
                        String.format(Locale.ROOT, "%.1f", snapshot.baseline().get(i)))
                        .styled(style -> style.withColor(type.color())));
                }
                updated.add(Text.translatable("screen.alkahest.atmosphere.chance", String.format(Locale.ROOT, "%.1f", snapshot.chance() * 100)));
                rendered = snapshot; cachedLines = java.util.List.copyOf(updated);
            }
            lines = cachedLines;
        }
        int width = 0;
        for (Text line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        int left = 4, top = 4, boxWidth = width + 12, height = lines.size() * 11 + 9;
        context.fill(left, top, left + boxWidth, top + height, 0xE40C1A25);
        context.fill(left, top, left + boxWidth, top + 1, 0xFFD5B479);
        context.fill(left, top + height - 1, left + boxWidth, top + height, 0xFF70583E);
        context.fill(left, top, left + 1, top + height, 0xFF99794F);
        context.fill(left + boxWidth - 1, top, left + boxWidth, top + height, 0xFF99794F);
        for (int i = 0; i < lines.size(); i++)
            context.drawTextWithShadow(client.textRenderer, lines.get(i), left + 6, top + 5 + i * 11, i == 0 ? 0xFFF0D39D : 0xFFDDE4D4);
    }
    private AtmosphereHud() { }
}
