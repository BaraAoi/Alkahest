package net.alkahest.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Small localised guide until the research tree and hypothesis UI are implemented. */
public final class JournalScreen extends Screen {
    private static final int PAGE_COUNT = 7;
    private int page;
    public JournalScreen() { super(Text.translatable("screen.alkahest.journal")); }

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
        context.drawTextWrapped(textRenderer, Text.translatable("screen.alkahest.journal.page." + page), left + 15, top + 34, 222, 0xFF30251B);
        context.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + PAGE_COUNT, width / 2, top + 168, 0xFF725033);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
