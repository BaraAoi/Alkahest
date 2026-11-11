package net.alkahest.client.gui;

import net.alkahest.essence.EssenceStack;
import net.alkahest.screen.TransmutatorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

/** Drawn in code: copper panel, amulet socket, progress bar and the essence price of the input. */
public final class TransmutatorScreen extends HandledScreen<TransmutatorScreenHandler> {
    private static final int WIDTH = 176, HEIGHT = 166;
    private static final int TRACK_X = 74, TRACK_Y = 32, TRACK_WIDTH = 34, TRACK_HEIGHT = 6;

    public TransmutatorScreen(TransmutatorScreenHandler handler, PlayerInventory inventory, Text title) {
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
            case 1 -> "screen.alkahest.transmutator.working";
            case 2 -> "screen.alkahest.transmutator.no_essence";
            case 3 -> "screen.alkahest.transmutator.blocked";
            case 4 -> "screen.alkahest.transmutator.unformed";
            case 5 -> "screen.alkahest.transmutator.no_amulet";
            case 6 -> "screen.alkahest.transmutator.dormant";
            default -> "screen.alkahest.transmutator.ready";
        });
    }

    private void slotFrame(DrawContext c, int sx, int sy) {
        int px = x + sx, py = y + sy;
        c.fill(px - 2, py - 2, px + 18, py + 18, 0xFF171117);
        c.fill(px - 1, py - 1, px + 17, py + 17, 0xFF8A6A3F);
        c.fill(px, py, px + 16, py + 16, 0xFF0E1B22);
        c.fill(px, py, px + 16, py + 1, 0xFF1B2A30);
        c.fill(px, py + 15, px + 16, py + 16, 0xFF3A4442);
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF241E18);
        c.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF17120E);
        c.fill(x + 4, y + 4, x + WIDTH - 4, y + 20, 0xFF2E241A);
        c.fill(x + 4, y + 20, x + WIDTH - 4, y + 21, 0xFF8A6A3F);
        c.fill(x + 4, y + 68, x + WIDTH - 4, y + 69, 0xFF8A6A3F);
        for (int[] rivet : new int[][]{{4, 4}, {WIDTH - 7, 4}, {4, HEIGHT - 7}, {WIDTH - 7, HEIGHT - 7}}) {
            c.fill(x + rivet[0], y + rivet[1], x + rivet[0] + 3, y + rivet[1] + 3, 0xFFB08A52);
        }
        slotFrame(c, TransmutatorScreenHandler.INPUT_X, TransmutatorScreenHandler.INPUT_Y);
        slotFrame(c, TransmutatorScreenHandler.AMULET_X, TransmutatorScreenHandler.AMULET_Y);
        slotFrame(c, TransmutatorScreenHandler.OUTPUT_X, TransmutatorScreenHandler.OUTPUT_Y);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slotFrame(c, 8 + col * 18, 84 + row * 18);
        for (int col = 0; col < 9; col++) slotFrame(c, 8 + col * 18, 142);

        int bx = x + TRACK_X, by = y + TRACK_Y;
        c.fill(bx - 1, by - 1, bx + TRACK_WIDTH + 1, by + TRACK_HEIGHT + 1, 0xFFB08A52);
        c.fill(bx, by, bx + TRACK_WIDTH, by + TRACK_HEIGHT, 0xFF0A171E);
        int fill = (int) ((long) Math.max(0, Math.min(handler.progress(), handler.maxProgress()))
            * (TRACK_WIDTH - 2) / Math.max(1, handler.maxProgress()));
        if (fill > 0) {
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + TRACK_HEIGHT - 1, 0xFFD9A93B);
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + 3, 0xFFF5E3A1);
        }
    }

    @Override protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFF1D8A3);
        c.drawTextWithShadow(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFFDFD0AB);
        int color = switch (handler.status()) {
            case 1 -> 0xFFFFE08A;
            case 2 -> 0xFFFFC27D;
            case 3 -> 0xFFFF9E7A;
            default -> 0xFFD1C6AC;
        };
        c.drawCenteredTextWithShadow(textRenderer, statusText(), 88, 48, color);

        // Essence price of the input, one line per type, tinted with the essence colour.
        List<EssenceStack> requirements = handler.requirements();
        int lineY = 22;
        for (int i = 0; i < requirements.size() && i < 5; i++) {
            EssenceStack stack = requirements.get(i);
            c.drawTextWithShadow(textRenderer, Text.literal(stack.type().displayName() + " × " + stack.amount()),
                90, lineY, stack.type().color() | 0xFF000000);
            lineY += 10;
        }
        if (requirements.isEmpty()) {
            c.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.transmutator.no_recipe"), 90, 22, 0xFF8C8578);
        }
    }
}
