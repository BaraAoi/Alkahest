package net.alkahest.client.gui;

import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceStack;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.item.ItemStack;
import net.alkahest.screen.TransmutatorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Separate instrument, essence, status and inventory panels; every slot keeps its real hitbox. */
public final class TransmutatorScreen extends HandledScreen<TransmutatorScreenHandler> {
    private static final Identifier BACKGROUND = Alkahest.id("textures/gui/transmutator_panel.png");
    private static final int WIDTH = TransmutatorScreenHandler.WIDTH, HEIGHT = TransmutatorScreenHandler.HEIGHT;
    private static final int TRACK_X = 54, TRACK_Y = 50, TRACK_WIDTH = 43, TRACK_HEIGHT = 7;
    private static final int COST_X = 146, COST_Y = 45, COST_ROW = 11, COST_WIDTH = 74, COST_ROWS = 5;
    private static final int CHOICE_ROW_HEIGHT = 20, CHOICES_PER_PAGE = 2, CHOICE_NAV_Y = 87;
    private int choicePage;

    public TransmutatorScreen(TransmutatorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = WIDTH; backgroundHeight = HEIGHT;
        titleX = 13; titleY = 9; playerInventoryTitleX = 13; playerInventoryTitleY = 130;
    }

    private Text statusText() {
        return Text.translatable(switch (handler.status()) {
            case 1 -> "screen.alkahest.transmutator.working";
            case 2 -> "screen.alkahest.transmutator.no_essence";
            case 3 -> "screen.alkahest.transmutator.blocked";
            case 4 -> "screen.alkahest.transmutator.unformed";
            case 5 -> "screen.alkahest.transmutator.no_amulet";
            case 6 -> "screen.alkahest.transmutator.dormant";
            case 7 -> "screen.alkahest.transmutator.choose_status";
            default -> "screen.alkahest.transmutator.ready";
        });
    }

    private int percent() {
        return (int)Math.max(0, Math.min(100, (long)handler.progress() * 100 / handler.maxProgress()));
    }

    private int amuletCharge() {
        ItemStack stack = handler.getSlot(1).getStack();
        return stack.getItem() instanceof AlkahestAmuletItem
            ? Math.max(0, stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0)) : -1;
    }

    /** Guard translated/custom names against crossing a panel edge. Hover shows the full text. */
    private String fit(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        return textRenderer.trimToWidth(text, maxWidth - textRenderer.getWidth("…")) + "…";
    }

    private void slot(DrawContext c, int sx, int sy, boolean focus) {
        int px = x + sx, py = y + sy;
        c.fill(px - 2, py - 2, px + 18, py + 18, 0xFF110E0D);
        c.fill(px - 1, py - 1, px + 17, py + 17, focus ? 0xFFD8AE66 : 0xFF8D7959);
        c.fill(px, py, px + 16, py + 16, 0xFF0C181C);
        c.fill(px, py, px + 16, py + 1, 0xFF2F4543);
        c.fill(px, py + 15, px + 16, py + 16, 0xFF29302D);
        if (focus) c.fill(px - 1, py - 1, px + 4, py, 0xFFFFDEA4);
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.drawTexture(BACKGROUND, x, y, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        slot(c, TransmutatorScreenHandler.INPUT_X, TransmutatorScreenHandler.INPUT_Y, true);
        slot(c, TransmutatorScreenHandler.AMULET_X, TransmutatorScreenHandler.AMULET_Y, true);
        slot(c, TransmutatorScreenHandler.OUTPUT_X, TransmutatorScreenHandler.OUTPUT_Y, true);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            slot(c, TransmutatorScreenHandler.PLAYER_X + col * 18, TransmutatorScreenHandler.PLAYER_Y + row * 18, false);
        for (int col = 0; col < 9; col++) slot(c, TransmutatorScreenHandler.PLAYER_X + col * 18, TransmutatorScreenHandler.HOTBAR_Y, false);
        int bx = x + TRACK_X, by = y + TRACK_Y;
        c.fill(bx - 1, by - 1, bx + TRACK_WIDTH + 1, by + TRACK_HEIGHT + 1, 0xFF9F8259);
        c.fill(bx, by, bx + TRACK_WIDTH, by + TRACK_HEIGHT, 0xFF091618);
        int fill = (int)((long)Math.max(0, Math.min(handler.progress(), handler.maxProgress())) * (TRACK_WIDTH - 2) / handler.maxProgress());
        if (fill > 0) {
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + TRACK_HEIGHT - 1, 0xFFB6843F);
            c.fill(bx + 1, by + 1, bx + 1 + fill, by + 3, 0xFFFFD68A);
        }
        if (handler.canSelectRecipe()) {
            int sx = x + TransmutatorScreenHandler.CHOICE_X, sy = y + TransmutatorScreenHandler.CHOICE_Y;
            boolean hovered = inside(mouseX, mouseY, TransmutatorScreenHandler.CHOICE_X, TransmutatorScreenHandler.CHOICE_Y,
                TransmutatorScreenHandler.CHOICE_WIDTH, TransmutatorScreenHandler.CHOICE_HEIGHT);
            c.fill(sx - 1, sy - 1, sx + TransmutatorScreenHandler.CHOICE_WIDTH + 1, sy + TransmutatorScreenHandler.CHOICE_HEIGHT + 1, 0xFFFFD678);
            c.fill(sx, sy, sx + TransmutatorScreenHandler.CHOICE_WIDTH, sy + TransmutatorScreenHandler.CHOICE_HEIGHT,
                handler.needsRecipeChoice() ? 0xFF813D20 : (hovered ? 0xFF6B5634 : 0xFF31443B));
        }
        if (handler.needsRecipeChoice()) {
            for (int row = 0; row < CHOICES_PER_PAGE; row++) {
                int index = page() * CHOICES_PER_PAGE + row;
                if (index >= handler.recipeCount()) break;
                int yy = COST_Y + row * CHOICE_ROW_HEIGHT;
                boolean hovered = inside(mouseX, mouseY, COST_X, yy, COST_WIDTH, 18);
                c.fill(x + COST_X - 1, y + yy - 1, x + COST_X + COST_WIDTH + 1, y + yy + 19, 0xFFD6AD67);
                c.fill(x + COST_X, y + yy, x + COST_X + COST_WIDTH, y + yy + 18, hovered ? 0xFF6D5530 : 0xFF253C36);
                c.drawItem(handler.recipeChoices().get(index).output(), x + COST_X + 1, y + yy + 1);
            }
        }
        // Separate from the operation progress: the installed amulet's actual Ordo charge.
        c.fill(x + 51, y + 104, x + 127, y + 106, 0xFF35413C);
        int charge = amuletCharge();
        if (charge > 0) {
            int length = (int)(76L * Math.min(charge, AlkahestAmuletItem.MAX_CHARGE) / AlkahestAmuletItem.MAX_CHARGE);
            c.fill(x + 51, y + 104, x + 51 + length, y + 106, 0xFFE2BF72);
        }
    }

    @Override protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawTextWithShadow(textRenderer, fit(title.getString(), 205), titleX, titleY, 0xFFF0DBB6);
        c.drawTextWithShadow(textRenderer, fit(playerInventoryTitle.getString(), 206), playerInventoryTitleX, playerInventoryTitleY, 0xFFDDD0B7);
        c.drawTextWithShadow(textRenderer, fit(Text.translatable("screen.alkahest.transmutator.input").getString(), 43), 18, 32, 0xFFD1C4A8);
        c.drawTextWithShadow(textRenderer, fit(Text.translatable("screen.alkahest.transmutator.output").getString(), 38), 96, 32, 0xFFD1C4A8);
        c.drawTextWithShadow(textRenderer, fit(Text.translatable("screen.alkahest.transmutator.amulet").getString(), 76), 51, 82, 0xFFCFBD9B);
        int charge = amuletCharge();
        String chargeText = charge < 0 ? "—" : charge + " / " + AlkahestAmuletItem.MAX_CHARGE;
        c.drawTextWithShadow(textRenderer, fit(chargeText, 76), 51, 94, charge == 0 ? 0xFFFFAB89 : 0xFFE9D5A8);
        c.drawCenteredTextWithShadow(textRenderer, percent() + "%", 75, 63, 0xFFF1D49C);
        if (handler.canSelectRecipe()) {
            String label = handler.needsRecipeChoice() ? Text.translatable("screen.alkahest.transmutator.choose_button").getString()
                : "↻ " + handler.selectedOutput().getName().getString();
            c.drawCenteredTextWithShadow(textRenderer, fit(label, TransmutatorScreenHandler.CHOICE_WIDTH - 2),
                TransmutatorScreenHandler.CHOICE_X + TransmutatorScreenHandler.CHOICE_WIDTH / 2, TransmutatorScreenHandler.CHOICE_Y, 0xFFFFE49A);
        }
        c.drawTextWithShadow(textRenderer, fit(Text.translatable(handler.needsRecipeChoice()
            ? "screen.alkahest.transmutator.choose_heading" : "screen.alkahest.transmutator.cost").getString(), 76), COST_X, 32, 0xFFF2D5A0);
        int statusColor = switch (handler.status()) {
            case 1 -> 0xFF94DFCC;
            case 2 -> 0xFFFFD197;
            case 3, 7 -> 0xFFFFAB89;
            default -> 0xFFE0D4BC;
        };
        c.drawCenteredTextWithShadow(textRenderer, fit(statusText().getString(), 200), WIDTH / 2, 113, statusColor);
        List<EssenceStack> costs = handler.requirements();
        if (handler.needsRecipeChoice()) {
            for (int row = 0; row < CHOICES_PER_PAGE; row++) {
                int index = page() * CHOICES_PER_PAGE + row;
                if (index >= handler.recipeCount()) break;
                var output = handler.recipeChoices().get(index).output();
                c.drawTextWithShadow(textRenderer, fit(output.getName().getString(), COST_WIDTH - 21),
                    COST_X + 20, COST_Y + row * CHOICE_ROW_HEIGHT + 5, 0xFFFFE4AE);
            }
            if (pages() > 1) c.drawCenteredTextWithShadow(textRenderer, "<  " + (page() + 1) + "/" + pages() + "  >", COST_X + COST_WIDTH / 2, CHOICE_NAV_Y, 0xFFFFD678);
        } else if (costs.isEmpty()) {
            c.drawTextWithShadow(textRenderer, fit(Text.translatable("screen.alkahest.transmutator.no_recipe").getString(), COST_WIDTH), COST_X, COST_Y, 0xFFACAAA0);
        } else {
            for (int i = 0; i < costs.size() && i < COST_ROWS; i++) {
                EssenceStack essence = costs.get(i);
                int yy = COST_Y + i * COST_ROW;
                c.fill(COST_X, yy + 2, COST_X + 5, yy + 7, 0xFFB2A38B);
                c.fill(COST_X + 1, yy + 3, COST_X + 4, yy + 6, essence.type().color() | 0xFF000000);
                String label = essence.type().displayName() + " × " + essence.amount();
                c.drawTextWithShadow(textRenderer, fit(label, COST_WIDTH - 8), COST_X + 8, yy, 0xFFE4DECE);
            }
        }
    }

    private int pages() { return Math.max(1, (handler.recipeCount() + CHOICES_PER_PAGE - 1) / CHOICES_PER_PAGE); }
    private int page() { return Math.min(choicePage, pages() - 1); }

    private boolean inside(int mouseX, int mouseY, int sx, int sy, int width, int height) {
        return mouseX >= x + sx && mouseX < x + sx + width && mouseY >= y + sy && mouseY < y + sy + height;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && client != null && client.interactionManager != null && handler.canSelectRecipe()) {
            if (!handler.needsRecipeChoice() && inside((int)mouseX, (int)mouseY, TransmutatorScreenHandler.CHOICE_X, TransmutatorScreenHandler.CHOICE_Y,
                TransmutatorScreenHandler.CHOICE_WIDTH, TransmutatorScreenHandler.CHOICE_HEIGHT)) {
                choicePage = 0;
                client.interactionManager.clickButton(handler.syncId, 0);
                return true;
            }
            if (handler.needsRecipeChoice()) {
                for (int row = 0; row < CHOICES_PER_PAGE; row++) {
                    int index = page() * CHOICES_PER_PAGE + row;
                    if (index < handler.recipeCount() && inside((int)mouseX, (int)mouseY, COST_X, COST_Y + row * CHOICE_ROW_HEIGHT, COST_WIDTH, 18)) {
                        client.interactionManager.clickButton(handler.syncId, index + 1);
                        return true;
                    }
                }
                if (pages() > 1 && inside((int)mouseX, (int)mouseY, COST_X, CHOICE_NAV_Y, COST_WIDTH, 10)) {
                    choicePage = Math.floorMod(page() + (mouseX < x + COST_X + COST_WIDTH / 2 ? -1 : 1), pages());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        super.render(c, mouseX, mouseY, delta);
        drawMouseoverTooltip(c, mouseX, mouseY);
        if (inside(mouseX, mouseY, TRACK_X, TRACK_Y, TRACK_WIDTH, TRACK_HEIGHT)) {
            c.drawTooltip(textRenderer, Text.translatable("screen.alkahest.transmutator.progress", percent()), mouseX, mouseY);
        } else if (handler.needsRecipeChoice() && inside(mouseX, mouseY, COST_X, COST_Y, COST_WIDTH, CHOICE_ROW_HEIGHT * CHOICES_PER_PAGE)) {
            int row = (mouseY - y - COST_Y) / CHOICE_ROW_HEIGHT;
            int index = page() * CHOICES_PER_PAGE + row;
            if (index < handler.recipeCount() && (mouseY - y - COST_Y) % CHOICE_ROW_HEIGHT < 18) {
                var recipe = handler.recipeChoices().get(index);
                List<Text> lines = new ArrayList<>();
                lines.add(recipe.output().getName());
                lines.add(Text.translatable("screen.alkahest.transmutator.pick_hint"));
                for (var entry : recipe.cost().entrySet()) lines.add(Text.literal(entry.getKey().displayName() + " × " + entry.getValue()));
                c.drawTooltip(textRenderer, lines, mouseX, mouseY);
            }
        } else if (handler.canSelectRecipe() && inside(mouseX, mouseY, TransmutatorScreenHandler.CHOICE_X, TransmutatorScreenHandler.CHOICE_Y,
            TransmutatorScreenHandler.CHOICE_WIDTH, TransmutatorScreenHandler.CHOICE_HEIGHT)) {
            if (handler.needsRecipeChoice()) c.drawTooltip(textRenderer, Text.translatable("screen.alkahest.transmutator.pick_hint"), mouseX, mouseY);
            else c.drawTooltip(textRenderer, List.of(Text.translatable("screen.alkahest.transmutator.recipe_choice", handler.selectedOutput().getName(),
                handler.selectedRecipeIndex() + 1, handler.recipeCount()), Text.translatable("screen.alkahest.transmutator.recipe_choice_hint")), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 10, 109, 216, 16)) {
            c.drawTooltip(textRenderer, statusText(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 139, 43, 86, 59) && !handler.requirements().isEmpty()) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable("screen.alkahest.transmutator.cost"));
            for (EssenceStack e : handler.requirements()) lines.add(Text.literal(e.type().displayName() + " × " + e.amount()));
            c.drawTooltip(textRenderer, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 51, 94, 76, 12) && amuletCharge() >= 0) {
            c.drawTooltip(textRenderer, Text.translatable("screen.alkahest.transmutator.amulet_charge", amuletCharge(), AlkahestAmuletItem.MAX_CHARGE), mouseX, mouseY);
        } else if (!handler.getSlot(1).hasStack() && inside(mouseX, mouseY, TransmutatorScreenHandler.AMULET_X, TransmutatorScreenHandler.AMULET_Y, 16, 16)) {
            c.drawTooltip(textRenderer, Text.translatable("screen.alkahest.transmutator.amulet_hint"), mouseX, mouseY);
        }
    }
}
