package net.alkahest.client.gui;

import net.alkahest.screen.DistilleryScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** Small custom panel with a server-synchronised progress indicator. */
public class DistilleryScreen extends HandledScreen<DistilleryScreenHandler> {
    public DistilleryScreen(DistilleryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176; backgroundHeight = 166;
        titleX = 9; titleY = 7; playerInventoryTitleX = 8; playerInventoryTitleY = 72;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xFFE8D5A1, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFFDCCBA9, false);
        context.drawText(textRenderer, Text.translatable("screen.alkahest.distillery.input"), 39, 21, 0xFFDCCBA9, false);
        context.drawText(textRenderer, Text.translatable("screen.alkahest.distillery.output"), 107, 21, 0xFFDCCBA9, false);
    }
    private void slot(DrawContext context, int x, int y) {
        context.fill(x - 1, y - 1, x + 17, y + 17, 0xFF947B50);
        context.fill(x, y, x + 16, y + 16, 0xFF111A29);
    }
    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF92764A);
        context.fill(x + 2, y + 2, x + backgroundWidth - 2, y + backgroundHeight - 2, 0xFF222D3D);
        context.fill(x + 5, y + 18, x + 171, y + 67, 0xFF344254);
        slot(context, x + 44, y + 32); slot(context, x + 116, y + 32);
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) slot(context, x + 8 + col*18, y + 84 + row*18);
        for (int col=0; col<9; col++) slot(context, x + 8 + col*18, y + 142);
        context.fill(x + 70, y + 36, x + 105, y + 45, 0xFF151E2D);
        int max = handler.maxProgress();
        int fill = max <= 0 ? 0 : Math.max(0, Math.min(33, handler.progress() * 33 / max));
        context.fill(x + 71, y + 37, x + 71 + fill, y + 44, 0xFF62CFBA);
    }
}
