package net.alkahest.client.gui;

import net.alkahest.screen.Tier2DistilleryScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** Drawn entirely in code: no backdrop texture is needed for this compact machine panel. */
public final class Tier2DistilleryScreen extends HandledScreen<Tier2DistilleryScreenHandler> {
    private static final int WIDTH = 176, HEIGHT = 166;
    private static final int TRACK_X = 62, TRACK_Y = 52, TRACK_WIDTH = 52, TRACK_HEIGHT = 6;

    public Tier2DistilleryScreen(Tier2DistilleryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = WIDTH;
        backgroundHeight = HEIGHT;
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 72;
    }

    private int percent() {
        int max = handler.maxProgress();
        return max <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) handler.progress() * 100 / max));
    }

    private Text statusText() {
        return Text.translatable(switch (handler.status()) {
            case 1 -> "screen.alkahest.distillery_tier2.working";
            case 2 -> "screen.alkahest.distillery_tier2.blocked";
            case 3 -> "screen.alkahest.distillery_tier2.no_bellows";
            default -> "screen.alkahest.distillery_tier2.ready";
        });
    }

    private void slotFrame(DrawContext context, int sx, int sy) {
        int px = x + sx, py = y + sy;
        context.fill(px - 2, py - 2, px + 18, py + 18, 0xFF171117);
        context.fill(px - 1, py - 1, px + 17, py + 17, 0xFF8A6A3F);
        context.fill(px, py, px + 16, py + 16, 0xFF0E1B22);
        context.fill(px, py, px + 16, py + 1, 0xFF1B2A30);
        context.fill(px, py + 15, px + 16, py + 16, 0xFF3A4442);
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF241E18);
        c.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF17120E);
        c.fill(x + 4, y + 4, x + WIDTH - 4, y + 20, 0xFF2E241A);
        c.fill(x + 4, y + 20, x + WIDTH - 4, y + 21, 0xFF8A6A3F);
        c.fill(x + 4, y + 68, x + WIDTH - 4, y + 69, 0xFF8A6A3F);
        // rivets
        for (int[] rivet : new int[][]{{4, 4}, {WIDTH - 7, 4}, {4, HEIGHT - 7}, {WIDTH - 7, HEIGHT - 7}}) {
            c.fill(x + rivet[0], y + rivet[1], x + rivet[0] + 3, y + rivet[1] + 3, 0xFFB08A52);
        }
        slotFrame(c, Tier2DistilleryScreenHandler.INPUT_X, Tier2DistilleryScreenHandler.INPUT_Y);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slotFrame(c, 8 + col * 18, 84 + row * 18);
        for (int col = 0; col < 9; col++) slotFrame(c, 8 + col * 18, 142);

        int bx = x + TRACK_X, by = y + TRACK_Y;
        c.fill(bx - 1, by - 1, bx + TRACK_WIDTH + 1, by + TRACK_HEIGHT + 1, 0xFFB08A52);
        c.fill(bx, by, bx + TRACK_WIDTH, by + TRACK_HEIGHT, 0xFF0A171E);
        int fill = (int) ((long) Math.max(0, Math.min(handler.progress(), handler.maxProgress()))
            * (TRACK_WIDTH - 2) / Math.max(1, handler.maxProgress()));
        if (fill > 0) {
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + TRACK_HEIGHT - 1, 0xFF8B5CF6);
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + 3, 0xFFD8B4FE);
        }
    }

    @Override protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFF1D8A3);
        c.drawTextWithShadow(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFFDFD0AB);
        c.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.distillery_tier2.input"), 14, 34, 0xFFE3D1A5);
        int color = (handler.status() == 2 || handler.status() == 3) ? 0xFFFFC27D : handler.status() == 1 ? 0xFFC9A6FF : 0xFFD1C6AC;
        c.drawCenteredTextWithShadow(textRenderer, statusText(), 88, 64, color);
        c.drawTextWithShadow(textRenderer, percent() + "%", 118, 52, 0xFFE9D9AE);
    }
}
