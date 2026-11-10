package net.alkahest.client.gui;

import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An original arcane research map and field notes. Progress remains server-authoritative. */
public final class JournalScreen extends Screen {
    private static final Identifier BACKGROUND = Alkahest.id("textures/gui/journal_background.png");
    private static final int GUIDE_PAGES = 10;
    /** The last page of the field notes is not text: it lists every synthesis of the Matrix. */
    private static final int RECIPES_PAGE = GUIDE_PAGES - 1;
    private record Node(String key, long parents, long discovered, long completed, int x, int y,
                        ItemStack icon, String[] choices, long[] failed) { }
    private static final Node[] NODES = {
        new Node("root", 0L, EarlyResearch.ROOT, EarlyResearch.ROOT, 24, 0,
            new ItemStack(ModItems.ALCHEMICAL_JOURNAL), new String[0], new long[0]),
        new Node("research", EarlyResearch.ROOT, EarlyResearch.DISCOVERED, EarlyResearch.COMPLETED, 96, 0,
            new ItemStack(ModBlocks.ALCHEMY_CAULDRON), new String[]{"sand", "sugar", "coal"}, new long[]{EarlyResearch.FAILED_SAND, EarlyResearch.FAILED_SUGAR, 0L}),
        new Node("reactive", EarlyResearch.COMPLETED, EarlyResearch.REACTIVE_DISCOVERED, EarlyResearch.REACTIVE_COMPLETED, 172, -40,
            new ItemStack(ModItems.FIRE_DUST), new String[]{"feather", "redstone", "clay"}, new long[]{EarlyResearch.FAILED_FEATHER, 0L, EarlyResearch.FAILED_CLAY}),
        new Node("raw", EarlyResearch.COMPLETED, EarlyResearch.RAW_DISCOVERED, EarlyResearch.RAW_COMPLETED, 172, 40,
            new ItemStack(ModBlocks.DISTILLERY), new String[]{"copper", "iron", "cobble"}, new long[]{0L, EarlyResearch.FAILED_IRON, EarlyResearch.FAILED_COBBLE}),
        new Node("sight", EarlyResearch.RAW_COMPLETED | EarlyResearch.REACTIVE_COMPLETED, EarlyResearch.SIGHT_DISCOVERED, EarlyResearch.SIGHT_COMPLETED, 248, 0,
            new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)), new String[]{"glass", "eye", "torch"}, new long[]{0L, EarlyResearch.FAILED_EYE, EarlyResearch.FAILED_TORCH}),
        new Node("chain_ignis", EarlyResearch.SIGHT_COMPLETED, EarlyResearch.IGNIS_DISCOVERED, EarlyResearch.IGNIS_COMPLETED, 324, -40,
            new ItemStack(Items.BLAZE_ROD), new String[]{"cane", "feather", "coal"}, new long[]{EarlyResearch.FAILED_CANE, EarlyResearch.FAILED_IGNIS_FEATHER, 0L}),
        new Node("chain_aqua", EarlyResearch.SIGHT_COMPLETED, EarlyResearch.AQUA_DISCOVERED, EarlyResearch.AQUA_COMPLETED, 324, 40,
            new ItemStack(Items.PRISMARINE_SHARD), new String[]{"kelp", "sand", "ice"}, new long[]{0L, EarlyResearch.FAILED_AQUA_SAND, EarlyResearch.FAILED_ICE}),
        new Node("binding", EarlyResearch.IGNIS_COMPLETED | EarlyResearch.AQUA_COMPLETED, EarlyResearch.BINDING_DISCOVERED, EarlyResearch.BINDING_COMPLETED, 400, 0,
            new ItemStack(ModItems.BINDING_SALT), new String[]{"string", "slime", "bonemeal"}, new long[]{0L, EarlyResearch.FAILED_SLIME, EarlyResearch.FAILED_BONE_MEAL}),
        new Node("the_alkahest", EarlyResearch.BINDING_COMPLETED, EarlyResearch.ALKAHEST_DISCOVERED, EarlyResearch.ALKAHEST_COMPLETED, 476, 0,
            new ItemStack(ModItems.ALKAHEST_VIAL), new String[]{"diamond", "gold", "obsidian"}, new long[]{0L, EarlyResearch.FAILED_GOLD, EarlyResearch.FAILED_OBSIDIAN})
    };
    private final long researchFlags;
    private boolean guide;
    private Node selected;
    private int guidePage;
    private int scroll, maxScroll;
    private double panX, panY;
    private boolean dragging;
    private int left, top, bookWidth, bookHeight;

    public JournalScreen(long researchFlags) { super(Text.translatable("screen.alkahest.journal")); this.researchFlags = researchFlags; }

    @Override protected void init() {
        bookWidth = Math.min(408, width - 12);
        bookHeight = Math.min(236, height - 12);
        left = (width - bookWidth) / 2;
        top = (height - bookHeight) / 2;
    }
    private int mapX() { return left + 15; }
    private int mapY() { return top + 57; }
    private int mapW() { return bookWidth - 30; }
    private int mapH() { return bookHeight - 89; }
    private int nx(Node node) { return mapX() + node.x() + (int)panX; }
    private int ny(Node node) { return mapY() + mapH() / 2 + node.y() + (int)panY; }
    private boolean within(double x, double y, int x1, int y1, int x2, int y2) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    private String state(Node node) {
        if ((researchFlags & node.parents()) != node.parents()) return "hidden";
        if ((researchFlags & node.completed()) != 0) return "completed";
        if ((researchFlags & node.discovered()) != 0) return "hypothesis";
        return "locked";
    }
    private Text label(Node node) { return Text.translatable("screen.alkahest." + node.key() + ".title"); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawTexture(BACKGROUND, left, top, bookWidth, bookHeight, 0, 0, 512, 256, 512, 256);
        context.fill(left + 10, top + 10, left + bookWidth - 10, top + 28, 0xCE081223);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.journal"), left + 18, top + 15, 0xFFE6CA86);
        context.drawTextWithShadow(textRenderer, "✦", left + bookWidth - 25, top + 15, 0xFFD5B873);
        tab(context, left + 14, top + 32, 116, "screen.alkahest.journal.map", !guide);
        tab(context, left + 134, top + 32, 100, "screen.alkahest.journal.notes", guide);
        if (guide) renderGuide(context, mouseX, mouseY);
        else if (selected != null) renderDetails(context, selected);
        else renderMap(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }
    private void tab(DrawContext c, int x, int y, int w, String key, boolean active) {
        c.fill(x, y, x + w, y + 20, active ? 0xFFB49152 : 0xFF4E4B4D);
        c.fill(x + 1, y + 1, x + w - 1, y + 19, active ? 0xE8243042 : 0xDF141A2C);
        c.drawCenteredTextWithShadow(textRenderer, Text.translatable(key), x + w / 2, y + 6, active ? 0xFFFFE3A0 : 0xFFBBB5A8);
    }
    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xFF957948);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xE60B1426);
    }
    private void renderMap(DrawContext c, int mouseX, int mouseY) {
        int x = mapX(), y = mapY(), w = mapW(), h = mapH();
        panel(c, x, y, w, h);
        c.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        link(c, NODES[0], NODES[1]); link(c, NODES[1], NODES[2]); link(c, NODES[1], NODES[3]);
        link(c, NODES[2], NODES[4]); link(c, NODES[3], NODES[4]);
        link(c, NODES[4], NODES[5]); link(c, NODES[4], NODES[6]);
        link(c, NODES[5], NODES[7]); link(c, NODES[6], NODES[7]);
        link(c, NODES[7], NODES[8]);
        for (Node node : NODES) if (!state(node).equals("hidden")) drawNode(c, node, mouseX, mouseY);
        c.disableScissor();
        c.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.alkahest.journal.map_hint"), left + bookWidth / 2, top + bookHeight - 24, 0xFFD4C4A0);
        if (within(mouseX, mouseY, x, y, x + w, y + h)) {
            for (Node node : NODES) if (!state(node).equals("hidden") && hitNode(node, mouseX, mouseY)) {
                Text hint = state(node).equals("locked") ? Text.translatable("screen.alkahest.journal.unknown") : label(node);
                c.drawTooltip(textRenderer, hint, mouseX, mouseY);
                break;
            }
        }
    }
    private void link(DrawContext c, Node a, Node b) {
        if (state(a).equals("hidden") || state(b).equals("hidden")) return;
        int x1 = nx(a), y1 = ny(a), x2 = nx(b), y2 = ny(b), middle = (x1 + x2) / 2;
        int color = state(b).equals("locked") ? 0xFF69645A : 0xFFC1A362;
        c.drawHorizontalLine(Math.min(x1, middle), Math.max(x1, middle), y1, color);
        c.drawVerticalLine(middle, Math.min(y1, y2), Math.max(y1, y2), color);
        c.drawHorizontalLine(Math.min(middle, x2), Math.max(middle, x2), y2, color);
    }
    private boolean hitNode(Node n, double x, double y) { return within(x, y, nx(n) - 16, ny(n) - 16, nx(n) + 16, ny(n) + 16); }
    private void drawNode(DrawContext c, Node node, int mx, int my) {
        int x = nx(node), y = ny(node);
        String state = state(node);
        boolean hover = hitNode(node, mx, my) && within(mx, my, mapX(), mapY(), mapX() + mapW(), mapY() + mapH());
        int border = state.equals("completed") ? 0xFFE5C16E : state.equals("hypothesis") ? 0xFF82DCD9 : 0xFF76716C;
        if (hover) { c.fill(x - 19, y - 19, x + 19, y + 19, 0x664FD3D4); border = 0xFFFFE6AA; }
        c.fill(x - 16, y - 16, x + 16, y + 16, border);
        c.fill(x - 14, y - 14, x + 14, y + 14, 0xFF1A2231);
        c.fill(x - 12, y - 12, x + 12, y + 12, 0xFF30384B);
        if (state.equals("locked")) c.drawCenteredTextWithShadow(textRenderer, "?", x, y - 4, 0xFFD0CAC2);
        else c.drawItem(node.icon(), x - 8, y - 8);
        if (state.equals("hypothesis")) c.fill(x + 11, y - 17, x + 17, y - 11, 0xFF90EBE2);
    }
    private int wrapped(DrawContext c, Text text, int x, int y, int w, int color) {
        c.drawTextWrapped(textRenderer, text, x, y, w, color);
        return textRenderer.wrapLines(text, w).size() * textRenderer.fontHeight;
    }
    private void renderDetails(DrawContext c, Node node) {
        int x = mapX(), y = mapY(), w = mapW(), h = mapH();
        panel(c, x, y, w, h);
        c.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xF4E7D4A7);
        c.enableScissor(x + 7, y + 7, x + w - 7, y + h - 7);
        String state = state(node), key = "screen.alkahest." + node.key() + ".";
        Text title = state.equals("locked") ? Text.translatable("screen.alkahest.journal.unknown") : label(node);
        c.drawText(textRenderer, title, x + 12, y + 11, 0xFF4F3520, false);
        c.fill(x + 12, y + 23, x + w - 12, y + 24, 0xFFB19A6E);
        int line = y + 31 - scroll;
        line += wrapped(c, Text.translatable(key + state), x + 12, line, w - 24, 0xFF30251C) + 6;
        if (state.equals("hypothesis")) {
            line += wrapped(c, Text.translatable(key + "instructions"), x + 12, line, w - 24, 0xFF594633) + 5;
            for (int i = 0; i < node.choices().length; i++) {
                boolean failed = (researchFlags & node.failed()[i]) != 0;
                Text choice = failed ? Text.translatable(key + node.choices()[i]).formatted(Formatting.RED, Formatting.STRIKETHROUGH) : Text.translatable(key + node.choices()[i]);
                c.drawText(textRenderer, choice, x + 16, line, failed ? 0xFF9F302C : 0xFF382B20, false);
                line += 12;
            }
            line += 4;
            line += wrapped(c, Text.translatable("screen.alkahest.journal.experiment_note"), x + 12, line, w - 24, 0xFF594633);
        }
        maxScroll = Math.max(0, line + scroll - (y + h - 9));
        c.disableScissor();
        if (maxScroll > 0) c.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.journal.scroll"), left + bookWidth - 100, top + bookHeight - 21, 0xFFD8C69D);
        button(c, left + 15, top + bookHeight - 25, 92, "screen.alkahest.journal.back");
    }
    private void renderGuide(DrawContext c, int mouseX, int mouseY) {
        int x = mapX(), y = mapY(), w = mapW(), h = mapH();
        panel(c, x, y, w, h);
        c.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xF4E7D4A7);
        if (guidePage == RECIPES_PAGE) {
            renderRecipes(c, x, y, w, h, mouseX, mouseY);
        } else {
            c.enableScissor(x + 7, y + 7, x + w - 7, y + h - 7);
            int contentHeight = wrapped(c, Text.translatable("screen.alkahest.journal.page." + guidePage), x + 12, y + 12 - scroll, w - 24, 0xFF30251C);
            maxScroll = Math.max(0, contentHeight - (h - 21));
            c.disableScissor();
        }
        if (maxScroll > 0) c.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.journal.scroll"), left + bookWidth / 2 - 45, top + bookHeight - 34, 0xFFD8C69D);
        button(c, left + 15, top + bookHeight - 25, 44, "screen.alkahest.journal.prev");
        button(c, left + bookWidth - 59, top + bookHeight - 25, 44, "screen.alkahest.journal.next");
        c.drawCenteredTextWithShadow(textRenderer, (guidePage + 1) + " / " + GUIDE_PAGES, left + bookWidth / 2, top + bookHeight - 21, 0xFFD8C69D);
    }
    /** The synthesis page: one row per Matrix recipe, icons first, names and prices on hover. */
    private void renderRecipes(DrawContext c, int x, int y, int w, int h, int mouseX, int mouseY) {
        c.drawText(textRenderer, Text.translatable("screen.alkahest.journal.recipes"), x + 12, y + 10, 0xFF4F3520, false);
        c.fill(x + 12, y + 22, x + w - 12, y + 23, 0xFFB19A6E);
        List<MatrixRecipe> recipes = MatrixRecipe.all();
        int top = y + 27, bottom = y + h - 6;
        maxScroll = Math.max(0, recipes.size() * 17 - Math.max(0, bottom - top));
        scroll = MathHelper.clamp(scroll, 0, maxScroll);
        c.enableScissor(x + 7, top, x + w - 7, bottom);
        for (int i = 0; i < recipes.size(); i++) {
            int rowY = top + i * 17 - scroll;
            if (rowY + 16 <= top) continue;
            if (rowY >= bottom) break;
            if (i % 2 == 0) c.fill(x + 8, rowY, x + w - 8, rowY + 16, 0x1A4F3520);
            drawRecipeRow(c, recipes.get(i), x + 12, rowY);
        }
        c.disableScissor();
        if (mouseX < x + 8 || mouseX >= x + w - 8 || mouseY < top || mouseY >= bottom) return;
        for (int i = 0; i < recipes.size(); i++) {
            int rowY = top + i * 17 - scroll;
            if (rowY + 16 <= top) continue;
            if (rowY >= bottom) break;
            if (mouseY >= rowY && mouseY < rowY + 16) {
                c.drawTooltip(textRenderer, describe(recipes.get(i)), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawRecipeRow(DrawContext c, MatrixRecipe recipe, int iconX, int iconY) {
        int cursor = iconX;
        // The framed icon is the base placed in the core; following icons go on pedestals.
        c.fill(cursor - 1, iconY, cursor + 16, iconY + 16, 0xAAAF8B40);
        c.drawItem(new ItemStack(recipe.base()), cursor, iconY);
        cursor += 17;
        for (Item component : recipe.components()) {
            c.drawText(textRenderer, "+", cursor, iconY + 4, 0xFF594633, false);
            cursor += 7;
            c.drawItem(new ItemStack(component), cursor, iconY);
            cursor += 17;
        }
        c.drawText(textRenderer, "→", cursor, iconY + 4, 0xFF977E50, false);
        cursor += 11;
        ItemStack output = recipe.output();
        c.drawItem(output, cursor, iconY);
        if (output.getCount() > 1) {
            String amount = String.valueOf(output.getCount());
            c.drawText(textRenderer, amount, cursor + 17 - textRenderer.getWidth(amount), iconY + 9, 0xFF20140C, false);
        }
        String price = "◆ " + MatrixRecipe.price(recipe.cost());
        c.drawText(textRenderer, price, mapX() + mapW() - 12 - textRenderer.getWidth(price), iconY + 4, 0xFF7A5A20, false);
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
        lines.add(Text.translatable("screen.alkahest.matrix.index_base", recipe.base().getName())
            .formatted(Formatting.GOLD));
        lines.add(Text.translatable("screen.alkahest.matrix.index_parts",
            parts.length() > 0 ? parts.toString() : Text.translatable("screen.alkahest.matrix.index_none").getString())
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

    private void button(DrawContext c, int x, int y, int width, String key) {
        c.fill(x, y, x + width, y + 16, 0xFF977E50);
        c.fill(x + 1, y + 1, x + width - 1, y + 15, 0xFF222B39);
        c.drawCenteredTextWithShadow(textRenderer, Text.translatable(key), x + width / 2, y + 4, 0xFFFFE1A1);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return super.mouseClicked(x, y, button);
        if (within(x, y, left + 14, top + 32, left + 130, top + 52)) { guide = false; selected = null; scroll = 0; return true; }
        if (within(x, y, left + 134, top + 32, left + 234, top + 52)) { guide = true; selected = null; scroll = 0; return true; }
        if (guide) {
            if (within(x, y, left + 15, top + bookHeight - 25, left + 59, top + bookHeight - 9)) { guidePage = Math.floorMod(guidePage - 1, GUIDE_PAGES); scroll = 0; return true; }
            if (within(x, y, left + bookWidth - 59, top + bookHeight - 25, left + bookWidth - 15, top + bookHeight - 9)) { guidePage = (guidePage + 1) % GUIDE_PAGES; scroll = 0; return true; }
        } else if (selected != null) {
            if (within(x, y, left + 15, top + bookHeight - 25, left + 107, top + bookHeight - 9)) { selected = null; scroll = 0; return true; }
        } else if (within(x, y, mapX(), mapY(), mapX() + mapW(), mapY() + mapH())) {
            for (Node node : NODES) if (!state(node).equals("hidden") && hitNode(node, x, y)) { selected = node; scroll = 0; return true; }
            dragging = true; return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging && !guide && selected == null) {
            panX = MathHelper.clamp(panX + dx, -300, 120);
            panY = MathHelper.clamp(panY + dy, -70, 70);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount) {
        if ((guide || selected != null) && within(x, y, mapX(), mapY(), mapX() + mapW(), mapY() + mapH())) {
            scroll = MathHelper.clamp(scroll - (int)(verticalAmount * 18), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
    }
    @Override public boolean shouldPause() { return false; }
}
