package net.alkahest.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.alkahest.research.EarlyResearch;

/** Guide with early research pages; the full research tree is not yet implemented. */
public final class JournalScreen extends Screen {
    private static final int GUIDE_PAGES = 7;
    private record ResearchPage(String key, int parents, int discovered, int completed, String[] choices, int[] failed) { }
    private static final ResearchPage[] RESEARCH = {
        new ResearchPage("research", EarlyResearch.ROOT, EarlyResearch.DISCOVERED, EarlyResearch.COMPLETED,
            new String[]{"sand", "sugar", "coal"}, new int[]{EarlyResearch.FAILED_SAND, EarlyResearch.FAILED_SUGAR, 0}),
        new ResearchPage("reactive", EarlyResearch.COMPLETED, EarlyResearch.REACTIVE_DISCOVERED, EarlyResearch.REACTIVE_COMPLETED,
            new String[]{"feather", "redstone", "clay"}, new int[]{EarlyResearch.FAILED_FEATHER, 0, EarlyResearch.FAILED_CLAY}),
        new ResearchPage("raw", EarlyResearch.COMPLETED, EarlyResearch.RAW_DISCOVERED, EarlyResearch.RAW_COMPLETED,
            new String[]{"copper", "iron", "cobble"}, new int[]{0, EarlyResearch.FAILED_IRON, EarlyResearch.FAILED_COBBLE}),
        new ResearchPage("sight", EarlyResearch.RAW_COMPLETED | EarlyResearch.REACTIVE_COMPLETED, EarlyResearch.SIGHT_DISCOVERED, EarlyResearch.SIGHT_COMPLETED,
            new String[]{"glass", "eye", "torch"}, new int[]{0, EarlyResearch.FAILED_EYE, EarlyResearch.FAILED_TORCH})
    };
    private static final int PAGE_COUNT = GUIDE_PAGES + RESEARCH.length;
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
        if (page >= GUIDE_PAGES) renderResearch(context, left, top, RESEARCH[page - GUIDE_PAGES]);
        else context.drawTextWrapped(textRenderer, Text.translatable("screen.alkahest.journal.page." + page), left + 15, top + 34, 222, 0xFF30251B);
        context.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + PAGE_COUNT, width / 2, top + 168, 0xFF725033);
        super.render(context, mouseX, mouseY, delta);
    }
    private void renderResearch(DrawContext context, int left, int top, ResearchPage research) {
        int color = 0xFF30251B;
        String key = "screen.alkahest." + research.key() + ".";
        context.drawText(textRenderer, Text.translatable(key + "title"), left + 15, top + 34, color, false);
        String state;
        if ((researchFlags & research.parents()) != research.parents()) state = "hidden";
        else if ((researchFlags & research.completed()) != 0) state = "completed";
        else if ((researchFlags & research.discovered()) != 0) state = "hypothesis";
        else state = "locked";
        context.drawTextWrapped(textRenderer, Text.translatable(key + state), left + 15, top + 49, 222, color);
        if (!state.equals("hypothesis")) return;
        context.drawTextWrapped(textRenderer, Text.translatable(key + "instructions"), left + 15, top + 72, 222, color);
        for (int i = 0; i < research.choices().length; i++)
            drawChoice(context, left + 15, top + 108 + 13 * i, key + research.choices()[i], research.failed()[i]);
    }
    private void drawChoice(DrawContext context, int x, int y, String key, int failedBit) {
        boolean failed = (researchFlags & failedBit) != 0;
        Text text = failed ? Text.translatable(key).formatted(Formatting.RED, Formatting.STRIKETHROUGH)
                           : Text.translatable(key);
        context.drawText(textRenderer, text, x, y, failed ? 0xFFAA2222 : 0xFF30251B, false);
    }
    @Override public boolean shouldPause() { return false; }
}
