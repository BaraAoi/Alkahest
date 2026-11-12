package net.alkahest.client.gui;

import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.transmutation.TransmutationRecipe;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.research.PracticalResearchPlan;
import net.alkahest.research.BookPagination;
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
    private static final int GUIDE_PAGES = 11;
    /** The last page of the field notes is not text: it lists every synthesis of the Matrix. */
    private static final int CAULDRON_PAGE = 3;
    private static final int RECIPES_PAGE = 9;
    private static final int TRANSMUTATION_PAGE = 10;
    private record Node(String key, long parents, long completed, int x, int y, ItemStack icon) { }
    private static Node node(int index, int x, int y) {
        var step = PracticalResearchPlan.STEPS.get(index);
        var item = net.minecraft.registry.Registries.ITEM.get(Identifier.of(step.itemId()));
        return new Node(step.id(), step.parents(), step.bit(), x, y, new ItemStack(item));
    }
    private static final Node[] NODES = {
        node(0, 30, 0), node(1, 155, -65), node(2, 155, 100), node(3, 295, 20),
        node(4, 425, -5), node(5, 425, 60), node(6, 555, 25), node(7, 690, 25), node(8, 820, -50),
        node(9, 300, 160), node(10, 300, 220), node(11, 820, 70), node(13, 820, -130),
        node(14, 960, 155), node(17, 960, -130),
        node(19, 560, -220), node(20, 430, -145), node(21, 430, -80),
        node(22, 290, -15), node(23, 290, -270), node(24, 430, -270), node(25, 430, -220),
        node(26, 300, 280), node(29, 430, 280), node(30, 295, 80),
        node(31, 290, -210), node(32, 290, -145), node(33, 290, -80)
    };
    private final long researchFlags;
    private boolean guide;
    private Node selected;
    private int guidePage, guideSubPage, detailPage, detailPages = 1;
    private int scroll, maxScroll;
    private final net.alkahest.research.ResearchMapView camera = new net.alkahest.research.ResearchMapView();
    private boolean dragging;
    private ItemStack diagramTooltip = ItemStack.EMPTY;
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
    private int nx(Node node) { return mapX() + (int)Math.round(camera.x(node.x())); }
    private int ny(Node node) { return mapY() + mapH() / 2 + (int)Math.round(camera.y(node.y())); }
    private boolean within(double x, double y, int x1, int y1, int x2, int y2) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    private String state(Node node) {
        if ((researchFlags & node.completed()) != 0) return "completed";
        if ((researchFlags & node.parents()) != node.parents()) return "hidden";
        return "active";
    }
    private Text label(Node node) { return Text.translatable("screen.alkahest.practice." + node.key() + ".title"); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawTexture(BACKGROUND, left, top, bookWidth, bookHeight, 0, 0, 512, 256, 512, 256);
        context.fill(left + 10, top + 10, left + bookWidth - 10, top + 28, 0xCE081223);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.alkahest.journal"), left + 18, top + 15, 0xFFE6CA86);
        context.drawTextWithShadow(textRenderer, "✦", left + bookWidth - 25, top + 15, 0xFFD5B873);
        tab(context, left + 14, top + 32, 116, "screen.alkahest.journal.map", !guide);
        tab(context, left + 134, top + 32, 100, "screen.alkahest.journal.notes", guide);
        if (guide) renderGuide(context, mouseX, mouseY);
        else if (selected != null) renderDetails(context, selected, mouseX, mouseY);
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
        // Parent bits are the single source of truth for both progression and connections.
        for (Node child : NODES) for (Node parent : NODES)
            if ((child.parents() & parent.completed()) != 0) link(c, parent, child);
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
    private boolean hitNode(Node n, double x, double y) {
        double r = camera.radius();
        return x >= nx(n) - r && x < nx(n) + r && y >= ny(n) - r && y < ny(n) + r;
    }
    private void drawNode(DrawContext c, Node node, int mx, int my) {
        int x = nx(node), y = ny(node);
        String state = state(node);
        boolean hover = hitNode(node, mx, my) && within(mx, my, mapX(), mapY(), mapX() + mapW(), mapY() + mapH());
        int border = state.equals("completed") ? 0xFFE5C16E : state.equals("active") ? 0xFF82DCD9 : 0xFF76716C;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        float zoom = (float) camera.zoom();
        c.getMatrices().scale(zoom, zoom, 1);
        if (hover) { c.fill(-19, -19, 19, 19, 0x664FD3D4); border = 0xFFFFE6AA; }
        c.fill(-16, -16, 16, 16, border);
        c.fill(-14, -14, 14, 14, 0xFF1A2231);
        c.fill(-12, -12, 12, 12, 0xFF30384B);
        if (state.equals("locked")) c.drawCenteredTextWithShadow(textRenderer, "?", 0, -4, 0xFFD0CAC2);
        else c.drawItem(node.icon(), -8, -8);
        if (state.equals("active")) c.fill(11, -17, 17, -11, 0xFF90EBE2);
        c.getMatrices().pop();
    }
    private int wrapped(DrawContext c, Text text, int x, int y, int w, int color) {
        c.drawTextWrapped(textRenderer, text, x, y, w, color);
        return textRenderer.wrapLines(text, w).size() * textRenderer.fontHeight;
    }
    private void paper(DrawContext c, int x, int y, int w, int h) {
        panel(c, x, y, w, h);
        c.fill(x + 5, y + 5, x + w / 2 - 1, y + h - 5, 0xFFF0DFB7);
        c.fill(x + w / 2 + 1, y + 5, x + w - 5, y + h - 5, 0xFFF0DFB7);
        c.fill(x + w / 2 - 1, y + 6, x + w / 2 + 1, y + h - 6, 0xFFAC956A);
    }
    private List<net.minecraft.text.OrderedText> detailLines(Node node, int width) {
        String state = state(node);
        String stateKey = state.equals("active") && PracticalResearchPlan.isAssembly(node.completed()) ? "assembly_active"
            : state.equals("completed") && PracticalResearchPlan.isAssembly(node.completed()) ? "assembly_completed" : state;
        List<net.minecraft.text.OrderedText> lines = new ArrayList<>();
        for (Text paragraph : List.of(Text.translatable("screen.alkahest.practice." + stateKey),
                Text.translatable("screen.alkahest.practice." + node.key() + ".recipe"),
                Text.translatable("screen.alkahest.practice.free_recipes"))) {
            lines.addAll(textRenderer.wrapLines(paragraph, Math.max(20, width)));
            lines.add(net.minecraft.text.OrderedText.EMPTY);
        }
        return lines;
    }
    private int diagramCount(Node node) {
        int crafts = JournalCraftingRecipes.BY_RESEARCH.getOrDefault(node.key(), List.of()).size();
        if (crafts > 0) return crafts;
        if (node.key().equals("alkahest") || node.key().equals("napalm") || node.key().equals("acid")) return 1;
        return (int)net.alkahest.recipe.CauldronEssenceRecipes.all().stream().filter(r -> r.result() == node.icon().getItem()).count();
    }
    private void renderDetails(DrawContext c, Node node, int mouseX, int mouseY) {
        int x = mapX(), y = mapY(), w = mapW(), h = mapH(), half = w / 2;
        paper(c, x, y, w, h);
        int textWidth = Math.max(20, half - 23);
        int rows = Math.max(1, (h - 34) / textRenderer.fontHeight);
        var lines = detailLines(node, textWidth);
        detailPages = Math.max(BookPagination.pages(lines.size(), rows), diagramCount(node));
        detailPage = MathHelper.clamp(detailPage, 0, detailPages - 1);
        c.enableScissor(x + 7, y + 7, x + half - 4, y + h - 7);
        c.drawText(textRenderer, textRenderer.trimToWidth(label(node).getString(), textWidth), x + 11, y + 10, 0xFF4F3520, false);
        for (int i = 0; i < rows && detailPage * rows + i < lines.size(); i++)
            c.drawText(textRenderer, lines.get(detailPage * rows + i), x + 11, y + 25 + i * textRenderer.fontHeight, 0xFF30251C, false);
        c.disableScissor();
        c.enableScissor(x + half + 3, y + 6, x + w - 6, y + h - 6);
        diagramTooltip = ItemStack.EMPTY;
        int diagramX = x + half + 9, diagramY = y + 10;
        float scale = Math.max(0.1f, Math.min(1f, Math.min((half - 20) / 92f, (h - 20) / 120f)));
        c.getMatrices().push();
        c.getMatrices().translate(diagramX, diagramY, 0);
        c.getMatrices().scale(scale, scale, 1);
        renderDiagram(c, node, detailPage, 0, 0, Math.max(20, (int)((half - 20) / scale)),
            (int)((mouseX - diagramX) / scale), (int)((mouseY - diagramY) / scale));
        c.getMatrices().pop();
        c.disableScissor();
        if (!diagramTooltip.isEmpty()) c.drawTooltip(textRenderer, diagramTooltip.getName(), mouseX, mouseY);
        button(c, left + 15, top + bookHeight - 25, 70, "screen.alkahest.journal.back");
        button(c, left + bookWidth - 110, top + bookHeight - 25, 44, "screen.alkahest.journal.prev");
        button(c, left + bookWidth - 59, top + bookHeight - 25, 44, "screen.alkahest.journal.next");
        c.drawCenteredTextWithShadow(textRenderer, (detailPage + 1) + " / " + detailPages, left + bookWidth / 2, top + bookHeight - 21, 0xFFD8C69D);
    }
    private void diagramItem(DrawContext c, int mouseX, int mouseY, ItemStack stack, int x, int y) {
        c.drawItem(stack, x, y);
        if (within(mouseX, mouseY, x, y, x + 16, y + 16)) diagramTooltip = stack;
    }
    private void renderDiagram(DrawContext c, Node node, int page, int x, int y, int width, int mouseX, int mouseY) {
        var crafts = JournalCraftingRecipes.BY_RESEARCH.getOrDefault(node.key(), List.of());
        if (page < crafts.size()) {
            var recipe = crafts.get(page);
            c.drawText(textRenderer, Text.translatable(recipe.shapeless() ? "screen.alkahest.journal.shapeless" : "screen.alkahest.journal.shaped"), x, y, 0xFF4F3520, false);
            for (int i = 0; i < 9; i++) {
                int sx = x + (i % 3) * 18, sy = y + 15 + (i / 3) * 18;
                c.fill(sx, sy, sx + 17, sy + 17, 0xFFBDA77D);
                String id = recipe.grid().get(i);
                if (!id.isEmpty()) diagramItem(c, mouseX, mouseY, new ItemStack(net.minecraft.registry.Registries.ITEM.get(Identifier.of(id))), sx, sy);
            }
            c.drawText(textRenderer, "→", x + 58, y + 37, 0xFF594633, false);
            var output = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Identifier.of(recipe.output())), recipe.count());
            diagramItem(c, mouseX, mouseY, output, x + 72, y + 33);
            if (recipe.count() > 1) c.drawText(textRenderer, Integer.toString(recipe.count()), x + 81, y + 43, 0xFF30251C, false);
            return;
        }
        if (page != 0) return;
        if (node.key().equals("napalm") || node.key().equals("acid")) {
            c.drawText(textRenderer, Text.translatable("screen.alkahest.journal.inventory_mix"), x, y, 0xFF4F3520, false);
            diagramItem(c, mouseX, mouseY, new ItemStack(node.key().equals("napalm") ? ModItems.FIRE_DUST : ModItems.CAUSTIC_SLIME), x, y + 18);
            c.drawText(textRenderer, "+", x + 20, y + 22, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, new ItemStack(node.key().equals("napalm") ? Items.SLIME_BALL : Items.GUNPOWDER), x + 30, y + 18);
            c.drawText(textRenderer, "→", x + 50, y + 22, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, node.icon(), x + 65, y + 18);
            return;
        }
        if (node.key().equals("alkahest")) {
            c.drawText(textRenderer, Text.translatable("screen.alkahest.journal.cauldron_diagram"), x, y, 0xFF4F3520, false);
            Item[] dissolved = {ModBlocks.ALCHEMICAL_MOSS.asItem(), Items.ENDER_PEARL,
                ModItems.CRYSTALS.get(EssenceType.IGNIS), ModItems.CRYSTALS.get(EssenceType.AQUA),
                ModItems.CRYSTALS.get(EssenceType.TERRA), ModItems.CRYSTALS.get(EssenceType.AER),
                ModItems.CRYSTALS.get(EssenceType.ORDO), ModItems.CRYSTALS.get(EssenceType.PERDITIO)};
            for (int i = 0; i < dissolved.length; i++) diagramItem(c, mouseX, mouseY, new ItemStack(dissolved[i]), x + i % 4 * 19, y + 15 + i / 4 * 19);
            c.drawText(textRenderer, "↓", x + 35, y + 53, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, new ItemStack(Items.DIAMOND), x, y + 66);
            c.drawText(textRenderer, "→", x + 19, y + 70, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, new ItemStack(ModBlocks.ALCHEMY_CAULDRON), x + 35, y + 66);
            c.drawText(textRenderer, "→", x + 55, y + 70, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, node.icon(), x + 70, y + 66);
            wrapped(c, Text.translatable("screen.alkahest.journal.alkahest_hint"), x, y + 87, Math.max(20, width), 0xFF594633);
            return;
        }
        var recipe = net.alkahest.recipe.CauldronEssenceRecipes.all().stream().filter(r -> r.result() == node.icon().getItem()).findFirst();
        recipe.ifPresent(brew -> renderCauldronDiagram(c, brew, x, y, width, mouseX, mouseY));
    }
    private void renderCauldronDiagram(DrawContext c, net.alkahest.recipe.CauldronEssenceRecipes.Recipe brew,
            int x, int y, int width, int mouseX, int mouseY) {
        c.drawText(textRenderer, Text.translatable("screen.alkahest.journal.cauldron_diagram"), x, y, 0xFF4F3520, false);
        diagramItem(c, mouseX, mouseY, new ItemStack(ModBlocks.ALCHEMY_CAULDRON), x + 35, y + 35);
        diagramItem(c, mouseX, mouseY, new ItemStack(brew.catalyst()), x, y + 35);
        c.drawText(textRenderer, "→", x + 19, y + 39, 0xFF594633, false);
        c.drawText(textRenderer, "→", x + 55, y + 39, 0xFF594633, false);
        diagramItem(c, mouseX, mouseY, new ItemStack(brew.result(), brew.count()), x + 70, y + 35);
        if (brew.count() > 1) c.drawText(textRenderer, Integer.toString(brew.count()), x + 79, y + 45, 0xFF30251C, false);
        c.drawText(textRenderer, "↑", x + 36, y + 54, 0xFF594633, false);
        int i = 0;
        for (EssenceType type : EssenceType.values()) if (brew.cost().containsKey(type)) {
            int sx = x + (i % 4) * 22, sy = y + 65 + (i / 4) * 23;
            diagramItem(c, mouseX, mouseY, new ItemStack(ModItems.CRYSTALS.get(type)), sx, sy);
            c.drawText(textRenderer, Integer.toString(brew.cost().get(type)), sx + 10, sy + 10, 0xFF30251C, false);
            i++;
        }
        wrapped(c, Text.translatable("screen.alkahest.journal.cauldron_hint"), x, y + 90, Math.max(20, width), 0xFF594633);
    }
    private int guidePageCount(int chapter) {
        int h = mapH();
        if (chapter == CAULDRON_PAGE) return BookPagination.pages(net.alkahest.recipe.CauldronEssenceRecipes.all().size(), 2);
        if (chapter == RECIPES_PAGE) return Math.max(1, BookPagination.pages(MatrixRecipe.all().size(), listRows(17)));
        if (chapter == TRANSMUTATION_PAGE) return Math.max(1, BookPagination.pages(TransmutationRecipe.all().size(), listRows(19)));
        int rows = Math.max(1, (h - 24) / textRenderer.fontHeight);
        int count = textRenderer.wrapLines(Text.translatable("screen.alkahest.journal.page." + chapter), Math.max(20, mapW() / 2 - 24)).size();
        return BookPagination.pages(count, rows * 2);
    }
    private int listRows(int height) { return Math.max(1, (mapH() - 33) / height); }
    private void renderGuide(DrawContext c, int mouseX, int mouseY) {
        int x = mapX(), y = mapY(), w = mapW(), h = mapH();
        paper(c, x, y, w, h);
        guideSubPage = MathHelper.clamp(guideSubPage, 0, guidePageCount(guidePage) - 1);
        if (guidePage == CAULDRON_PAGE) {
            renderCauldronIndex(c, x, y, w, h, mouseX, mouseY);
        } else if (guidePage == RECIPES_PAGE) {
            scroll = guideSubPage * listRows(17) * 17;
            renderRecipes(c, x, y, w, h, mouseX, mouseY);
        } else if (guidePage == TRANSMUTATION_PAGE) {
            scroll = guideSubPage * listRows(19) * 19;
            renderTransmutations(c, x, y, w, h, mouseX, mouseY);
        } else {
            int half = w / 2, rows = Math.max(1, (h - 24) / textRenderer.fontHeight);
            var lines = textRenderer.wrapLines(Text.translatable("screen.alkahest.journal.page." + guidePage), Math.max(20, half - 24));
            for (int column = 0; column < 2; column++) {
                int px = x + column * half + 12;
                c.enableScissor(px - 2, y + 7, x + (column + 1) * half - 5, y + h - 7);
                for (int i = 0; i < rows; i++) {
                    int line = BookPagination.index(guideSubPage, column * rows + i, rows * 2);
                    if (line < lines.size()) c.drawText(textRenderer, lines.get(line), px, y + 12 + i * textRenderer.fontHeight, 0xFF30251C, false);
                }
                c.disableScissor();
            }
        }
        button(c, left + 15, top + bookHeight - 25, 44, "screen.alkahest.journal.prev");
        button(c, left + bookWidth - 59, top + bookHeight - 25, 44, "screen.alkahest.journal.next");
        c.drawCenteredTextWithShadow(textRenderer, (guidePage + 1) + "." + (guideSubPage + 1) + " / " + GUIDE_PAGES + "." + guidePageCount(guidePage), left + bookWidth / 2, top + bookHeight - 21, 0xFFD8C69D);
    }
    /** Two illustrated recipes per spread, derived from the actual cauldron registry. */
    private void renderCauldronIndex(DrawContext c, int x, int y, int w, int h, int mouseX, int mouseY) {
        var recipes = net.alkahest.recipe.CauldronEssenceRecipes.all();
        int half = w / 2;
        diagramTooltip = ItemStack.EMPTY;
        for (int column = 0; column < 2; column++) {
            int index = BookPagination.index(guideSubPage, column, 2);
            if (index >= recipes.size()) break;
            var brew = recipes.get(index);
            int leftPage = x + column * half;
            c.enableScissor(leftPage + 5, y + 6, leftPage + half - 5, y + h - 6);
            c.drawText(textRenderer, textRenderer.trimToWidth(new ItemStack(brew.result()).getName().getString(), Math.max(20, half - 22)),
                leftPage + 10, y + 10, 0xFF4F3520, false);
            int originX = leftPage + 10, originY = y + 25;
            float scale = Math.max(0.1f, Math.min(1f, Math.min((half - 20) / 92f, (h - 34) / 120f)));
            c.getMatrices().push();
            c.getMatrices().translate(originX, originY, 0);
            c.getMatrices().scale(scale, scale, 1);
            renderCauldronDiagram(c, brew, 0, 0, Math.max(20, (int)((half - 20) / scale)),
                (int)((mouseX - originX) / scale), (int)((mouseY - originY) / scale));
            c.getMatrices().pop();
            c.disableScissor();
        }
        if (!diagramTooltip.isEmpty()) c.drawTooltip(textRenderer, diagramTooltip.getName(), mouseX, mouseY);
    }

    /** The synthesis page: one row per Matrix recipe, icons first, names and prices on hover. */
    private void renderRecipes(DrawContext c, int x, int y, int w, int h, int mouseX, int mouseY) {
        c.drawText(textRenderer, Text.translatable("screen.alkahest.journal.recipes"), x + 12, y + 10, 0xFF4F3520, false);
        c.fill(x + 12, y + 22, x + w - 12, y + 23, 0xFFB19A6E);
        List<MatrixRecipe> recipes = MatrixRecipe.all();
        int top = y + 27, bottom = Math.min(y + h - 6, y + 27 + listRows(17) * 17);
        maxScroll = Math.max(0, recipes.size() * 17 - Math.max(0, bottom - top));
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

    /** A scrollable index, read directly from the machine's authoritative recipe registry. */
    private void renderTransmutations(DrawContext c, int x, int y, int w, int h, int mouseX, int mouseY) {
        c.drawText(textRenderer, Text.translatable("screen.alkahest.journal.transmutations"), x + 12, y + 10, 0xFF4F3520, false);
        c.fill(x + 12, y + 22, x + w - 12, y + 23, 0xFFB19A6E);
        var recipes = TransmutationRecipe.all();
        int top = y + 27, bottom = Math.min(y + h - 6, y + 27 + listRows(19) * 19);
        maxScroll = Math.max(0, recipes.size() * 19 - Math.max(0, bottom - top));
        c.enableScissor(x + 7, top, x + w - 7, bottom);
        for (int i = 0; i < recipes.size(); i++) {
            int row = top + i * 19 - scroll;
            if (row + 18 <= top || row >= bottom) continue;
            var recipe = recipes.get(i);
            if (i % 2 == 0) c.fill(x + 8, row, x + w - 8, row + 18, 0x1A4F3520);
            c.drawItem(recipe.input(), x + 12, row + 1);
            c.drawText(textRenderer, "→", x + 32, row + 5, 0xFF594633, false);
            c.drawItem(recipe.output(), x + 45, row + 1);
            String cost = "◆ " + MatrixRecipe.price(recipe.cost());
            c.drawText(textRenderer, cost, x + w - 12 - textRenderer.getWidth(cost), row + 5, 0xFF7A5A20, false);
        }
        c.disableScissor();
        if (!within(mouseX, mouseY, x + 8, top, x + w - 8, bottom)) return;
        for (int i = 0; i < recipes.size(); i++) {
            int row = top + i * 19 - scroll;
            if (mouseY >= row && mouseY < row + 18) {
                var recipe = recipes.get(i);
                c.drawTooltip(textRenderer, List.of(
                    Text.translatable("screen.alkahest.journal.transmutation_input", recipe.input().getName()),
                    Text.translatable("screen.alkahest.journal.transmutation_output", recipe.output().getName()),
                    Text.translatable("screen.alkahest.matrix.index_cost").copy().append(": " + MatrixRecipe.price(recipe.cost()))
                ), mouseX, mouseY);
                break;
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
            if (within(x, y, left + 15, top + bookHeight - 25, left + 59, top + bookHeight - 9)) { if (guideSubPage > 0) guideSubPage--; else if (guidePage > 0) { guidePage--; guideSubPage = guidePageCount(guidePage) - 1; } scroll = 0; return true; }
            if (within(x, y, left + bookWidth - 59, top + bookHeight - 25, left + bookWidth - 15, top + bookHeight - 9)) { if (guideSubPage + 1 < guidePageCount(guidePage)) guideSubPage++; else if (guidePage + 1 < GUIDE_PAGES) { guidePage++; guideSubPage = 0; } scroll = 0; return true; }
        } else if (selected != null) {
            if (within(x, y, left + 15, top + bookHeight - 25, left + 85, top + bookHeight - 9)) { selected = null; scroll = 0; return true; }
            if (within(x, y, left + bookWidth - 110, top + bookHeight - 25, left + bookWidth - 66, top + bookHeight - 9)) { detailPage = Math.max(0, detailPage - 1); return true; }
            if (within(x, y, left + bookWidth - 59, top + bookHeight - 25, left + bookWidth - 15, top + bookHeight - 9)) { detailPage = Math.min(detailPages - 1, detailPage + 1); return true; }
        } else if (within(x, y, mapX(), mapY(), mapX() + mapW(), mapY() + mapH())) {
            for (Node node : NODES) if (!state(node).equals("hidden") && hitNode(node, x, y)) { selected = node; scroll = 0; detailPage = 0; return true; }
            dragging = true; return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging && !guide && selected == null) {
            camera.pan(dx, dy);
            clampCamera();
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    private void clampCamera() {
        int minX = java.util.Arrays.stream(NODES).mapToInt(Node::x).min().orElse(0);
        int maxX = java.util.Arrays.stream(NODES).mapToInt(Node::x).max().orElse(0);
        int minY = java.util.Arrays.stream(NODES).mapToInt(Node::y).min().orElse(0);
        int maxY = java.util.Arrays.stream(NODES).mapToInt(Node::y).max().orElse(0);
        camera.clamp(mapW(), mapH(), minX, maxX, minY, maxY);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount) {
        if (!guide && selected == null && within(x, y, mapX(), mapY(), mapX() + mapW(), mapY() + mapH())) {
            if (verticalAmount != 0) {
                camera.zoomAt(Math.pow(1.15, verticalAmount), x - mapX(), y - (mapY() + mapH() / 2.0));
                clampCamera();
            }
            return true;
        }
        if ((guide || selected != null) && within(x, y, mapX(), mapY(), mapX() + mapW(), mapY() + mapH())) {
            if (verticalAmount != 0 && selected != null) detailPage = MathHelper.clamp(detailPage + (verticalAmount < 0 ? 1 : -1), 0, detailPages - 1);
            else if (verticalAmount != 0 && guide) {
                if (verticalAmount < 0) {
                    if (guideSubPage + 1 < guidePageCount(guidePage)) guideSubPage++;
                    else if (guidePage + 1 < GUIDE_PAGES) { guidePage++; guideSubPage = 0; }
                } else if (guideSubPage > 0) guideSubPage--;
                else if (guidePage > 0) { guidePage--; guideSubPage = guidePageCount(guidePage) - 1; }
            }
            return true;
        }
        return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
    }
    @Override public boolean shouldPause() { return false; }
}
