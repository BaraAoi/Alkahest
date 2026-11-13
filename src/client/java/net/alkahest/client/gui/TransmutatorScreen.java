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
        if (handler.canSelectIronRecipe()) {
            int sx = x + TransmutatorScreenHandler.CHOICE_X, sy = y + TransmutatorScreenHandler.CHOICE_Y;
            boolean hovered = inside(mouseX, mouseY, TransmutatorScreenHandler.CHOICE_X, TransmutatorScreenHandler.CHOICE_Y,
                TransmutatorScreenHandler.CHOICE_WIDTH, TransmutatorScreenHandler.CHOICE_HEIGHT);
            c.fill(sx, sy, sx + TransmutatorScreenHandler.CHOICE_WIDTH, sy + TransmutatorScreenHandler.CHOICE_HEIGHT,
                hovered ? 0xFF5A4930 : 0xFF24312D);
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
        if (handler.canSelectIronRecipe()) {
            String choice = "↻ " + handler.selectedOutput().getName().getString();
            c.drawTextWithShadow(textRenderer, fit(choice, TransmutatorScreenHandler.CHOICE_WIDTH - 2),
                TransmutatorScreenHandler.CHOICE_X + 1, TransmutatorScreenHandler.CHOICE_Y, 0xFFF1D49C);
        }
        c.drawTextWithShadow(textRenderer, fit(Text.translatable("screen.alkahest.transmutator.cost").getString(), 76), COST_X, 32, 0xFFF2D5A0);
        int statusColor = switch (handler.status()) {
            case 1 -> 0xFF94DFCC;
            case 2 -> 0xFFFFD197;
            case 3 -> 0xFFFFAB89;
            default -> 0xFFE0D4BC;
        };
        c.drawCenteredTextWithShadow(textRenderer, fit(statusText().getString(), 200), WIDTH / 2, 113, statusColor);
        List<EssenceStack> costs = handler.requirements();
        if (costs.isEmpty()) {
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

    private boolean inside(int mouseX, int mouseY, int sx, int sy, int width, int height) {
        return mouseX >= x + sx && mouseX < x + sx + width && mouseY >= y + sy && mouseY < y + sy + height;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handler.canSelectIronRecipe()
            && mouseX >= x + TransmutatorScreenHandler.CHOICE_X && mouseX < x + TransmutatorScreenHandler.CHOICE_X + TransmutatorScreenHandler.CHOICE_WIDTH
            && mouseY >= y + TransmutatorScreenHandler.CHOICE_Y && mouseY < y + TransmutatorScreenHandler.CHOICE_Y + TransmutatorScreenHandler.CHOICE_HEIGHT
            && client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        super.render(c, mouseX, mouseY, delta);
        drawMouseoverTooltip(c, mouseX, mouseY);
        if (inside(mouseX, mouseY, TRACK_X, TRACK_Y, TRACK_WIDTH, TRACK_HEIGHT)) {
            c.drawTooltip(textRenderer, Text.translatable("screen.alkahest.transmutator.progress", percent()), mouseX, mouseY);
        } else if (handler.canSelectIronRecipe() && inside(mouseX, mouseY, TransmutatorScreenHandler.CHOICE_X, TransmutatorScreenHandler.CHOICE_Y,
            TransmutatorScreenHandler.CHOICE_WIDTH, TransmutatorScreenHandler.CHOICE_HEIGHT)) {
            c.drawTooltip(textRenderer, List.of(Text.translatable("screen.alkahest.transmutator.iron_choice", handler.selectedOutput().getName()),
                Text.translatable("screen.alkahest.transmutator.iron_choice_hint")), mouseX, mouseY);
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
