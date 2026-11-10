package net.alkahest.client.gui;

import net.alkahest.essence.EssenceStack;
import net.alkahest.screen.MatrixScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

/** Drawn in code: a violet-and-gold panel with the star of slots, a progress bar and the price. */
public final class MatrixScreen extends HandledScreen<MatrixScreenHandler> {
    private static final int WIDTH = 176, HEIGHT = 166;
    private static final int TRACK_X = 72, TRACK_Y = 34, TRACK_WIDTH = 40, TRACK_HEIGHT = 6;

    public MatrixScreen(MatrixScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = WIDTH;
        backgroundHeight = HEIGHT;
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 72;
    }

    private Text statusText() {
        return Text.translatable(switch (handler.status()) {
            case 1 -> "screen.alkahest.matrix.working";
            case 2 -> "screen.alkahest.matrix.no_essence";
            case 3 -> "screen.alkahest.matrix.blocked";
            case 4 -> "screen.alkahest.matrix.unformed";
            case 5 -> "screen.alkahest.matrix.no_recipe";
            case 6 -> "screen.alkahest.matrix.asleep";
            default -> "screen.alkahest.matrix.ready";
        });
    }

    private void slotFrame(DrawContext c, int sx, int sy) {
        int px = x + sx, py = y + sy;
        c.fill(px - 2, py - 2, px + 18, py + 18, 0xFF14101A);
        c.fill(px - 1, py - 1, px + 17, py + 17, 0xFF8A6A3F);
        c.fill(px, py, px + 16, py + 16, 0xFF171029);
        c.fill(px, py, px + 16, py + 1, 0xFF241A3A);
        c.fill(px, py + 15, px + 16, py + 16, 0xFF3A3444);
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF1B1426);
        c.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF120D1B);
        c.fill(x + 4, y + 4, x + WIDTH - 4, y + 20, 0xFF241A38);
        c.fill(x + 4, y + 20, x + WIDTH - 4, y + 21, 0xFFB08A52);
        c.fill(x + 4, y + 68, x + WIDTH - 4, y + 69, 0xFFB08A52);
        for (int[] rivet : new int[][]{{4, 4}, {WIDTH - 7, 4}, {4, HEIGHT - 7}, {WIDTH - 7, HEIGHT - 7}}) {
            c.fill(x + rivet[0], y + rivet[1], x + rivet[0] + 3, y + rivet[1] + 3, 0xFFD9B36B);
        }
        slotFrame(c, MatrixScreenHandler.BASE_X, MatrixScreenHandler.BASE_Y);
        slotFrame(c, MatrixScreenHandler.OUTPUT_X, MatrixScreenHandler.OUTPUT_Y);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slotFrame(c, 8 + col * 18, 84 + row * 18);
        for (int col = 0; col < 9; col++) slotFrame(c, 8 + col * 18, 142);

        int bx = x + TRACK_X, by = y + TRACK_Y;
        c.fill(bx - 1, by - 1, bx + TRACK_WIDTH + 1, by + TRACK_HEIGHT + 1, 0xFFB08A52);
        c.fill(bx, by, bx + TRACK_WIDTH, by + TRACK_HEIGHT, 0xFF0D0A14);
        int fill = (int) ((long) Math.max(0, Math.min(handler.progress(), handler.maxProgress()))
            * (TRACK_WIDTH - 2) / Math.max(1, handler.maxProgress()));
        if (fill > 0) {
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + TRACK_HEIGHT - 1, 0xFF9A6BE0);
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + 3, 0xFFE7D5FF);
        }
    }

    @Override protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFE9D7A8);
        c.drawTextWithShadow(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFFDFD0AB);
        int color = switch (handler.status()) {
            case 1 -> 0xFFE7D5FF;
            case 2 -> 0xFFFFC27D;
            case 3, 5 -> 0xFFFF9E7A;
            case 6 -> 0xFF9C8FBF;
            default -> 0xFFD1C6AC;
        };
        c.drawCenteredTextWithShadow(textRenderer, statusText(), 88, 50, color);

        List<EssenceStack> requirements = handler.requirements();
        int lineY = 58;
        for (EssenceStack stack : requirements) {
            c.drawTextWithShadow(textRenderer, Text.literal(stack.type().displayName() + " × " + stack.amount()),
                100, lineY, stack.type().color() | 0xFF000000);
            lineY += 10;
        }
    }
}
