package net.alkahest.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.alkahest.research.EarlyResearch;

/** Guide with a first research page; the full research tree is not yet implemented. */
public final class JournalScreen extends Screen {
    private static final int PAGE_COUNT = 8;
    private final int researchFlags;
    private int page;
    public JournalScreen(int researchFlags) { super(Text.translatable("screen.alkahest.journal")); this.researchFlags = researchFlags; }

    @Override protected void init() {
        int left = (width - 252) / 2;
        int top = (height - 194) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), button -> page = Math.floorMod(page - 1, PAGE_COUNT))
            .dimensions(left + 14, top + 162, 38, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), button -> page = (page + 1) % PAGE_COUNT)
            .dimensions(left + 200, top + 162, 38, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - 252) / 2;
        int top = (height - 194) / 2;
        context.fill(left, top, left + 252, top + 194, 0xFF453020);
        context.fill(left + 4, top + 4, left + 248, top + 190, 0xFFF2E2B7);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.alkahest.journal"), width / 2, top + 11, 0xFFFFE390);
        context.fill(left + 12, top + 26, left + 240, top + 27, 0xFF9D784F);
        if (page == 7) renderResearch(context, left, top);
        else context.drawTextWrapped(textRenderer, Text.translatable("screen.alkahest.journal.page." + page), left + 15, top + 34, 222, 0xFF30251B);
        context.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + PAGE_COUNT, width / 2, top + 168, 0xFF725033);
        super.render(context, mouseX, mouseY, delta);
    }
    private void renderResearch(DrawContext context, int left, int top) {
        int color = 0xFF30251B;
        context.drawText(textRenderer, Text.translatable("screen.alkahest.research.title"), left + 15, top + 34, color, false);
        String state = (researchFlags & EarlyResearch.COMPLETED) != 0 ? "completed" :
                       (researchFlags & EarlyResearch.DISCOVERED) != 0 ? "hypothesis" : "locked";
        context.drawTextWrapped(textRenderer, Text.translatable("screen.alkahest.research." + state), left + 15, top + 49, 222, color);
        if (!state.equals("hypothesis")) return;
        context.drawTextWrapped(textRenderer, Text.translatable("screen.alkahest.research.instructions"), left + 15, top + 72, 222, color);
        drawChoice(context, left + 15, top + 108, "sand", EarlyResearch.FAILED_SAND);
        drawChoice(context, left + 15, top + 121, "sugar", EarlyResearch.FAILED_SUGAR);
        drawChoice(context, left + 15, top + 134, "coal", 0);
    }
    private void drawChoice(DrawContext context, int x, int y, String choice, int failedBit) {
        boolean failed = (researchFlags & failedBit) != 0;
        Text text = failed ? Text.translatable("screen.alkahest.research." + choice).formatted(Formatting.RED, Formatting.STRIKETHROUGH)
                           : Text.translatable("screen.alkahest.research." + choice);
        context.drawText(textRenderer, text, x, y, failed ? 0xFFAA2222 : 0xFF30251B, false);
    }
    @Override public boolean shouldPause() { return false; }
}
