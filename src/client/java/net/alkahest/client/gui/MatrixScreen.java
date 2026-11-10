package net.alkahest.client.gui;

import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.screen.MatrixScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drawn in code: a violet-and-gold panel with the star of slots, a progress bar and the price.
 *
 * <p>The question mark in the corner turns the panel into the index of syntheses, because the seven
 * recipes of the Matrix live in code and are otherwise invisible in game: every row shows the base
 * item, the parts it swallows, the result and the total essence price, with the full names and the
 * per-essence breakdown in the tooltip.
 */
public final class MatrixScreen extends HandledScreen<MatrixScreenHandler> {
    private static final int WIDTH = 176, HEIGHT = 166;
    private static final int TRACK_X = 72, TRACK_Y = 34, TRACK_WIDTH = 40, TRACK_HEIGHT = 6;
    private static final int INDEX_TOP = 22, INDEX_HEADER = 14, ROW_HEIGHT = 17;
    private static final int ESCAPE_KEY = 256;

    private boolean index;
    private ButtonWidget indexButton;

    public MatrixScreen(MatrixScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = WIDTH;
        backgroundHeight = HEIGHT;
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 72;
    }

    @Override protected void init() {
        super.init();
        indexButton = ButtonWidget.builder(Text.literal("?"), button -> toggleIndex())
            .dimensions(x + WIDTH - 17, y + 3, 13, 14).build();
        addDrawableChild(indexButton);
    }

    private void toggleIndex() {
        index = !index;
        if (indexButton != null) indexButton.setMessage(Text.literal(index ? "×" : "?"));
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

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (!index) return;
        drawIndex(context);
        drawIndexTooltip(context, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (index && mouseX >= x + 2 && mouseX < x + WIDTH - 2
            && mouseY >= y + INDEX_TOP && mouseY < y + HEIGHT - 2) {
            toggleIndex();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (index && keyCode == ESCAPE_KEY) {
            toggleIndex();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The index page: one row per synthesis, icons only, names and prices live in the tooltip. */
    private void drawIndex(DrawContext c) {
        int left = x + 2, right = x + WIDTH - 2;
        int top = y + INDEX_TOP, bottom = y + HEIGHT - 2;
        c.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFFB08A52);
        c.fill(left, top, right, bottom, 0xFF120D1B);
        c.fill(left, top, right, top + INDEX_HEADER, 0xFF241A38);
        c.fill(left, top + INDEX_HEADER, right, top + INDEX_HEADER + 1, 0xFFB08A52);
        c.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.matrix.index_title"),
            left + 5, top + 3, 0xFFE9D7A8);
        String count = String.valueOf(MatrixRecipe.all().size());
        c.drawTextWithShadow(textRenderer, count, right - 5 - textRenderer.getWidth(count), top + 3, 0xFF9C8FBF);

        List<MatrixRecipe> recipes = MatrixRecipe.all();
        for (int i = 0; i < recipes.size(); i++) {
            int rowY = rowTop(i);
            if (rowY + ROW_HEIGHT > bottom) break;
            if (i % 2 == 0) c.fill(left + 1, rowY, right - 1, rowY + ROW_HEIGHT - 1, 0xFF1A1327);
            drawRow(c, recipes.get(i), left + 5, rowY + 1);
        }
    }

    private int rowTop(int position) {
        return y + INDEX_TOP + INDEX_HEADER + 2 + position * ROW_HEIGHT;
    }

    private void drawRow(DrawContext c, MatrixRecipe recipe, int iconX, int iconY) {
        int cursor = iconX;
        c.drawItem(new ItemStack(recipe.base()), cursor, iconY);
        cursor += 18;
        for (Item component : recipe.components()) {
            c.drawTextWithShadow(textRenderer, "+", cursor, iconY + 4, 0xFF9C8FBF);
            cursor += 9;
            c.drawItem(new ItemStack(component), cursor, iconY);
            cursor += 18;
        }
        c.drawTextWithShadow(textRenderer, "→", cursor, iconY + 4, 0xFFB08A52);
        cursor += 12;
        ItemStack output = recipe.output();
        c.drawItem(output, cursor, iconY);
        if (output.getCount() > 1) {
            String amount = String.valueOf(output.getCount());
            c.drawTextWithShadow(textRenderer, amount,
                cursor + 17 - textRenderer.getWidth(amount), iconY + 10, 0xFFFFFFFF);
        }
        String price = "◆ " + MatrixRecipe.price(recipe.cost());
        c.drawTextWithShadow(textRenderer, price,
            x + WIDTH - 8 - textRenderer.getWidth(price), iconY + 4, 0xFFD9B36B);
    }

    private void drawIndexTooltip(DrawContext c, int mouseX, int mouseY) {
        if (mouseX < x + 2 || mouseX >= x + WIDTH - 2) return;
        List<MatrixRecipe> recipes = MatrixRecipe.all();
        for (int i = 0; i < recipes.size(); i++) {
            int rowY = rowTop(i);
            if (mouseY < rowY || mouseY >= rowY + ROW_HEIGHT - 1) continue;
            c.drawTooltip(textRenderer, describe(recipes.get(i)), mouseX, mouseY);
            return;
        }
    }

    private List<Text> describe(MatrixRecipe recipe) {
        List<Text> lines = new ArrayList<>();
        ItemStack output = recipe.output();
        lines.add(Text.literal(output.getName().getString() + (output.getCount() > 1 ? " ×" + output.getCount() : ""))
            .formatted(Formatting.GOLD));
        StringBuilder parts = new StringBuilder();
        for (Item component : recipe.components()) {
            if (parts.length() > 0) parts.append(" + ");
            parts.append(new ItemStack(component).getName().getString());
        }
        lines.add(Text.literal(Text.translatable("screen.alkahest.matrix.index_from").getString() + ": "
            + recipe.base().getName().getString() + (parts.length() > 0 ? " + " + parts : ""))
            .formatted(Formatting.GRAY));
        StringBuilder cost = new StringBuilder();
        for (Map.Entry<EssenceType, Integer> entry : recipe.cost().entrySet()) {
            if (cost.length() > 0) cost.append("  ");
            cost.append(entry.getKey().displayName()).append(' ').append(entry.getValue());
        }
        lines.add(Text.literal(Text.translatable("screen.alkahest.matrix.index_cost").getString() + ": " + cost)
            .formatted(Formatting.DARK_PURPLE));
        return lines;
    }
}
