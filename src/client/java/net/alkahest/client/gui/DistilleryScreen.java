package net.alkahest.client.gui;

import net.alkahest.Alkahest;
import net.alkahest.screen.DistilleryScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The artwork is purely decorative; progress and the three machine states come from the server. */
public final class DistilleryScreen extends HandledScreen<DistilleryScreenHandler> {
    private static final Identifier BACKGROUND = Alkahest.id("textures/gui/distillery_backdrop.png");
    private static final int WIDTH = 176, HEIGHT = 214;
    private static final int TRACK_X = 47, TRACK_Y = 56, TRACK_WIDTH = 82;

    public DistilleryScreen(DistilleryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = WIDTH; backgroundHeight = HEIGHT;
        titleX = 12; titleY = 9; playerInventoryTitleX = 10; playerInventoryTitleY = 120;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (mouseX >= x + TRACK_X && mouseX < x + TRACK_X + TRACK_WIDTH
            && mouseY >= y + TRACK_Y && mouseY < y + TRACK_Y + 9) {
            context.drawTooltip(textRenderer, Text.translatable("screen.alkahest.distillery.progress", percent()), mouseX, mouseY);
        } else if (!handler.getSlot(DistilleryScreenHandler.CATALYST_SCREEN_SLOT).hasStack()
            && mouseX >= x + DistilleryScreenHandler.CATALYST_X && mouseX < x + DistilleryScreenHandler.CATALYST_X + 16
            && mouseY >= y + DistilleryScreenHandler.CATALYST_Y && mouseY < y + DistilleryScreenHandler.CATALYST_Y + 16) {
            context.drawTooltip(textRenderer, Text.translatable("screen.alkahest.distillery.catalyst_hint"), mouseX, mouseY);
        }
    }

    private int percent() {
        int max = handler.maxProgress();
        return max <= 0 ? 0 : (int)Math.max(0, Math.min(100, (long)handler.progress() * 100 / max));
    }

    private Text statusText() {
        if (handler.status() == 1 && handler.isAlkahestActive()) {
            return Text.translatable("screen.alkahest.distillery.working_alkahest");
        }
        return Text.translatable(switch (handler.status()) {
            case 1 -> "screen.alkahest.distillery.working";
            case 2 -> "screen.alkahest.distillery.blocked";
            default -> "screen.alkahest.distillery.ready";
        });
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFF1D8A3);
        context.drawTextWithShadow(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFFDFD0AB);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.distillery.input"), 17, 35, 0xFFDCCBA6);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.distillery.output"), 12, 83, 0xFFDCCBA6);
        int statusColor = handler.status() == 2 ? 0xFFFFC27D
            : handler.status() == 1 ? (handler.isAlkahestActive() ? 0xFFD8B4FE : 0xFF8EE5D7) : 0xFFD1C6AC;
        context.drawCenteredTextWithShadow(textRenderer, statusText(), 88, 70, statusColor);
        context.drawTextWithShadow(textRenderer, percent() + "%", 135, 56, 0xFFE9D9AE);
    }

    private void slot(DrawContext c, int sx, int sy, boolean special, boolean arcane) {
        int px = x + sx, py = y + sy;
        // Bevels sit outside the 16x16 actual hit box and never cover its item.
        c.fill(px - 2, py - 2, px + 18, py + 18, 0xFF231C20);
        int rim = arcane ? 0xFFA855F7 : (special ? 0xFFD1AC6A : 0xFF8D754F);
        c.fill(px - 1, py - 1, px + 17, py + 17, rim);
        c.fill(px, py, px + 16, py + 16, arcane ? 0xEF160E26 : 0xEF0C1A23);
        c.fill(px, py, px + 16, py + 1, 0xFF151E24);
        c.fill(px, py + 15, px + 16, py + 16, 0xFF384243);
        c.fill(px, py, px + 1, py + 16, 0xFF20292A);
        if (special || arcane) {
            c.fill(px - 2, py - 2, px + 2, py - 1, arcane ? 0xFFD8B4FE : 0xFFFFD78E);
            c.fill(px + 14, py + 16, px + 18, py + 17, 0xFF61452E);
        }
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.drawTexture(BACKGROUND, x, y, WIDTH, HEIGHT, 0, 0, 472, 568, 472, 568);
        // Dark glass insets preserve contrast without hiding the engraved brass artwork.
        c.fill(x + 7, y + 6, x + 169, y + 20, 0xD816100B);
        c.fill(x + 10, y + 26, x + 166, y + 50, 0xA51A120C);
        c.fill(x + 13, y + 53, x + 163, y + 79, 0xD018110B);
        c.fill(x + 8, y + 81, x + 168, y + 112, 0xCC1A120C);
        c.fill(x + 5, y + 117, x + 171, y + 208, 0xB9130E0A);
        c.fill(x + 10, y + 21, x + 166, y + 22, 0xFF8A8378);
        c.fill(x + 49, y + 87, x + 163, y + 88, 0xFF6E6656);
        c.fill(x + 9, y + 129, x + 167, y + 130, 0xFF6E6656);
        // Brass feed capillary connecting the Alkahest vial slot to the central retort input
        boolean alkahest = handler.isAlkahestActive();
        c.fill(x + 98, y + 38, x + 126, y + 40, alkahest ? 0xFFA855F7 : 0xFF6A6252);
        slot(c, 80, 31, true, false);
        slot(c, DistilleryScreenHandler.CATALYST_X, DistilleryScreenHandler.CATALYST_Y, false, alkahest);
        for (int col = 0; col < 7; col++) slot(c, 26 + col * 18, 96, false, false);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(c, 8 + col * 18, 132 + row * 18, false, false);
        for (int col = 0; col < 9; col++) slot(c, 8 + col * 18, 190, false, false);
        // A narrow inset vial fills only from the server-synchronised progress value.
        int bx = x + TRACK_X, by = y + TRACK_Y;
        c.fill(bx - 1, by - 1, bx + TRACK_WIDTH + 1, by + 9, 0xFFB3935D);
        c.fill(bx, by, bx + TRACK_WIDTH, by + 8, 0xFF0A171E);
        int fill = (int)((long)Math.max(0, Math.min(handler.progress(), handler.maxProgress())) * (TRACK_WIDTH - 2) / Math.max(1, handler.maxProgress()));
        if (fill > 0) {
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + 7, alkahest ? 0xFF8B5CF6 : 0xFF319B8C);
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + 3, alkahest ? 0xFFD8B4FE : 0xFF82E1C6);
        }
        // A small indicator changes colour when the output is blocked.
        int gem = handler.status() == 2 ? 0xFFFFB66D
            : handler.status() == 1 ? (alkahest ? 0xFFC084FC : 0xFF82E7D1) : 0xFF72827B;
        c.fill(x + 155, y + 33, x + 159, y + 41, 0xFF4E4235);
        c.fill(x + 153, y + 35, x + 161, y + 39, 0xFF4E4235);
        c.fill(x + 155, y + 35, x + 159, y + 39, gem);
    }
}
