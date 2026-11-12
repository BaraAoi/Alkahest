package net.alkahest.client.gui;

import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.transmutation.TransmutationRecipe;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.research.PracticalResearchPlan;
import net.alkahest.research.BookPagination;
import net.alkahest.research.JournalLayout;
import net.alkahest.research.ResearchJournalContent;
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

/** Full-screen research atlas with an independently paginated modal for each entry. */
public final class JournalScreen extends Screen {
    private static final Identifier BACKGROUND = Alkahest.id("textures/gui/research_atlas.png");
    private static final Identifier BOOK = Alkahest.id("textures/gui/journal_book.png");
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
    private Node selected;
    private int detailPage, detailPages = 1;
    private final net.alkahest.research.ResearchMapView camera = new net.alkahest.research.ResearchMapView();
    private boolean dragging;
    private ItemStack diagramTooltip = ItemStack.EMPTY;
    private JournalLayout layout;

    public JournalScreen(long researchFlags) { super(Text.translatable("screen.alkahest.journal")); this.researchFlags = researchFlags; }

    @Override protected void init() {
        layout = JournalLayout.of(width, height);
        clampCamera();
    }
    private int mapX() { return layout.map().x(); }
    private int mapY() { return layout.map().y(); }
    private int mapW() { return layout.map().width(); }
    private int mapH() { return layout.map().height(); }
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
        // One quiet, opaque surface for the atlas; only the selected entry uses a book.
        context.drawTexture(BACKGROUND, 0, 0, width, height, 0, 0, 1376, 768, 1376, 768);
        context.drawText(textRenderer, Text.translatable("screen.alkahest.journal.map"), 21, 14, 0xFFE2D7B8, false);
        context.fill(20, 28, width - 20, 29, 0x806E8393);
        renderMap(context, selected == null ? mouseX : -10000, selected == null ? mouseY : -10000);
        if (selected != null) {
            // drawItem uses an elevated GUI depth; draw the *entire* modal above those items,
            // not just later in call order, so map icons cannot pierce the book or dimmer.
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.fill(0, 0, width, height, 0xA0000000);
            var book = layout.detail();
            context.fill(book.x() + 4, book.y() + 5, book.x() + book.width() + 5, book.y() + book.height() + 5, 0x66000000);
            context.drawTexture(BOOK, book.x(), book.y(), book.width(), book.height(), 0, 0, 1024, 512, 1024, 512);
            renderDetails(context, selected, mouseX, mouseY);
            context.getMatrices().pop();
        }
        super.render(context, mouseX, mouseY, delta);
    }
    private void centered(DrawContext c, Text text, int cx, int y, int color) {
        c.drawText(textRenderer, text, cx - textRenderer.getWidth(text) / 2, y, color, false);
    }
    private void renderMap(DrawContext c, int mouseX, int mouseY) {
        int x = mapX(), y = mapY(), w = mapW(), h = mapH();
        c.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        // Parent bits are the single source of truth for both progression and connections.
        for (Node child : NODES) for (Node parent : NODES)
            if ((child.parents() & parent.completed()) != 0) link(c, parent, child);
        for (Node node : NODES) if (!state(node).equals("hidden")) drawNode(c, node, mouseX, mouseY);
        c.disableScissor();
        String mapHint = textRenderer.trimToWidth(Text.translatable("screen.alkahest.journal.map_hint").getString(), Math.max(20, width - 40));
        centered(c, Text.literal(mapHint), width / 2, height - 18, 0xFFBCCBD0);
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
        if (ResearchJournalContent.catalogue(node.key()) != ResearchJournalContent.Catalogue.NONE) {
            lines.addAll(textRenderer.wrapLines(Text.translatable("screen.alkahest.journal.catalogue_later"), Math.max(20, width)));
            lines.add(net.minecraft.text.OrderedText.EMPTY);
        }
        for (int chapter : ResearchJournalContent.chapters(node.key())) {
            lines.addAll(textRenderer.wrapLines(Text.translatable("screen.alkahest.journal.page." + chapter), Math.max(20, width)));
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
        var content = layout.detailContent();
        int x = content.x(), y = content.y(), w = content.width(), h = content.height(), half = w / 2;
        int textWidth = Math.max(20, half - 23);
        int rows = Math.max(1, (h - 34) / textRenderer.fontHeight);
        var lines = detailLines(node, textWidth);
        int diagrams = diagramCount(node);
        int textSpreads = BookPagination.detailSpreads(lines.size(), rows, diagrams);
        int cataloguePages = cataloguePages(node, h);
        detailPages = textSpreads + cataloguePages;
        detailPage = MathHelper.clamp(detailPage, 0, detailPages - 1);
        diagramTooltip = ItemStack.EMPTY;
        if (detailPage >= textSpreads) {
            int page = detailPage - textSpreads;
            switch (ResearchJournalContent.catalogue(node.key())) {
                case CAULDRON -> renderCauldronIndex(c, x, y, w, h, page, mouseX, mouseY);
                case MATRIX -> renderRecipes(c, x, y, w, h, page, mouseX, mouseY);
                case TRANSMUTATOR -> renderTransmutations(c, x, y, w, h, page, mouseX, mouseY);
                default -> { }
            }
        } else for (int column = 0; column < 2; column++) {
            int px = x + column * half + 11;
            c.enableScissor(x + column * half + 7, y + 7, x + (column + 1) * half - 5, y + h - 7);
            int first = BookPagination.detailStart(detailPage, column, rows, diagrams);
            if (first >= 0) {
                Text heading = column == 0 ? label(node) : Text.translatable("screen.alkahest.journal.continued");
                c.drawText(textRenderer, textRenderer.trimToWidth(heading.getString(), textWidth), px, y + 10, 0xFF65462B, false);
                c.fill(px, y + 21, px + textWidth, y + 22, 0x889C8053);
                for (int i = 0; i < rows && first + i < lines.size(); i++)
                    c.drawText(textRenderer, lines.get(first + i), px, y + 25 + i * textRenderer.fontHeight, 0xFF30251C, false);
            } else {
                int diagramX = px, diagramY = y + 10;
                float scale = Math.max(0.1f, Math.min(1.15f, Math.min((half - 22) / 110f, (h - 20) / 120f)));
                c.getMatrices().push();
                c.getMatrices().translate(diagramX, diagramY, 0);
                c.getMatrices().scale(scale, scale, 1);
                renderDiagram(c, node, detailPage, 0, 0, Math.max(20, (int)((half - 22) / scale)),
                    (int)Math.floor((mouseX - diagramX) / scale), (int)Math.floor((mouseY - diagramY) / scale));
                c.getMatrices().pop();
            }
            c.disableScissor();
        }
        if (!diagramTooltip.isEmpty()) c.drawTooltip(textRenderer, diagramTooltip.getName(), mouseX, mouseY);
        var book = layout.detail();
        button(c, book.x() + 15, book.y() + book.height() - 25, 70, "screen.alkahest.journal.back");
        button(c, book.x() + book.width() - 110, book.y() + book.height() - 25, 44, "screen.alkahest.journal.prev");
        button(c, book.x() + book.width() - 59, book.y() + book.height() - 25, 44, "screen.alkahest.journal.next");
        centered(c, Text.literal((detailPage + 1) + " / " + detailPages), book.x() + book.width() / 2, book.y() + book.height() - 21, 0xFF725536);
    }

    private void diagramItem(DrawContext c, int mouseX, int mouseY, ItemStack stack, int x, int y) {
        c.drawItem(stack, x, y);
        if (within(mouseX, mouseY, x, y, x + 16, y + 16)) diagramTooltip = stack;
    }
    private void renderDiagram(DrawContext c, Node node, int page, int x, int y, int width, int mouseX, int mouseY) {
        var crafts = JournalCraftingRecipes.BY_RESEARCH.getOrDefault(node.key(), List.of());
        int pageLeft = x, center = x + width / 2;
        x += Math.max(0, (width - 92) / 2);
        if (page < crafts.size()) {
            var recipe = crafts.get(page);
            diagramHeader(c, Text.translatable(recipe.shapeless() ? "screen.alkahest.journal.shapeless" : "screen.alkahest.journal.shaped"), center, y, width);
            for (int i = 0; i < 9; i++) {
                int sx = x + (i % 3) * 18, sy = y + 15 + (i / 3) * 18;
                c.fill(sx, sy, sx + 17, sy + 17, 0xFFAA8D63);
                c.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0xFFE0CDA5);
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
            diagramHeader(c, Text.translatable("screen.alkahest.journal.inventory_mix"), center, y, width);
            diagramItem(c, mouseX, mouseY, new ItemStack(node.key().equals("napalm") ? ModItems.FIRE_DUST : ModItems.CAUSTIC_SLIME), x, y + 18);
            c.drawText(textRenderer, "+", x + 20, y + 22, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, new ItemStack(node.key().equals("napalm") ? Items.SLIME_BALL : Items.GUNPOWDER), x + 30, y + 18);
            c.drawText(textRenderer, "→", x + 50, y + 22, 0xFF594633, false);
            diagramItem(c, mouseX, mouseY, node.icon(), x + 65, y + 18);
            return;
        }
        if (node.key().equals("alkahest")) {
            diagramHeader(c, Text.translatable("screen.alkahest.journal.cauldron_diagram"), center, y, width);
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
            wrapped(c, Text.translatable("screen.alkahest.journal.alkahest_hint"), pageLeft, y + 87, Math.max(20, width), 0xFF594633);
            return;
        }
        var recipe = net.alkahest.recipe.CauldronEssenceRecipes.all().stream().filter(r -> r.result() == node.icon().getItem()).findFirst();
        recipe.ifPresent(brew -> renderCauldronDiagram(c, brew, pageLeft, y, width, mouseX, mouseY));
    }
    private void diagramHeader(DrawContext c, Text text, int center, int y, int width) {
        centered(c, Text.literal(textRenderer.trimToWidth(text.getString(), Math.max(20, width))), center, y, 0xFF65462B);
    }
    private void renderCauldronDiagram(DrawContext c, net.alkahest.recipe.CauldronEssenceRecipes.Recipe brew,
            int x, int y, int width, int mouseX, int mouseY) {
        int pageLeft = x;
        diagramHeader(c, Text.translatable("screen.alkahest.journal.cauldron_diagram"), x + width / 2, y, width);
        x += Math.max(0, (width - 92) / 2);
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
        wrapped(c, Text.translatable("screen.alkahest.journal.cauldron_hint"), pageLeft, y + 90, Math.max(20, width), 0xFF594633);
    }
    private int listRows(int height, int areaHeight) { return Math.max(1, (areaHeight - 33) / height); }
    private int cataloguePages(Node node, int h) {
        return switch (ResearchJournalContent.catalogue(node.key())) {
            case CAULDRON -> BookPagination.pages(net.alkahest.recipe.CauldronEssenceRecipes.all().size(), 2);
            case MATRIX -> BookPagination.pages(MatrixRecipe.all().size(), listRows(21, h) * 2);
            case TRANSMUTATOR -> BookPagination.pages(TransmutationRecipe.all().size(), listRows(21, h) * 2);
            default -> 0;
        };
    }
    /** Two illustrated recipes per spread, derived from the actual cauldron registry. */
    private void renderCauldronIndex(DrawContext c, int x, int y, int w, int h, int page, int mouseX, int mouseY) {
        var recipes = net.alkahest.recipe.CauldronEssenceRecipes.all();
        int half = w / 2;
        diagramTooltip = ItemStack.EMPTY;
        for (int column = 0; column < 2; column++) {
            int index = BookPagination.index(page, column, 2);
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
                (int)Math.floor((mouseX - originX) / scale), (int)Math.floor((mouseY - originY) / scale));
            c.getMatrices().pop();
            c.disableScissor();
        }
        if (!diagramTooltip.isEmpty()) c.drawTooltip(textRenderer, diagramTooltip.getName(), mouseX, mouseY);
    }

    /** Each catalogue page is confined to one side of the binding. Hover for full ingredients. */
    private void renderRecipes(DrawContext c, int x, int y, int w, int h, int page, int mouseX, int mouseY) {
        var recipes = MatrixRecipe.all();
        int half = w / 2, rows = listRows(21, h);
        for (int column = 0; column < 2; column++) {
            int px = x + column * half + 10;
            c.enableScissor(px, y + 8, x + (column + 1) * half - 7, y + h - 6);
            c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("screen.alkahest.journal.recipes").getString(), half - 20), px, y + 10, 0xFF65462B, false);
            for (int row = 0; row < rows; row++) {
                int index = BookPagination.index(page, column * rows + row, rows * 2);
                if (index >= recipes.size()) break;
                var recipe = recipes.get(index);
                int ry = y + 27 + row * 21;
                if ((row & 1) == 0) c.fill(px, ry, x + (column + 1) * half - 8, ry + 20, 0x1A694C2C);
                c.drawItem(new ItemStack(recipe.base()), px + 1, ry + 2);
                c.drawText(textRenderer, "→", px + 19, ry + 6, 0xFF765A39, false);
                c.drawItem(recipe.output(), px + 29, ry + 2);
                String name = recipe.output().getName().getString();
                c.drawText(textRenderer, textRenderer.trimToWidth(name, Math.max(10, half - 70)), px + 48, ry + 2, 0xFF483523, false);
                c.drawText(textRenderer, "◆ " + MatrixRecipe.price(recipe.cost()), px + 48, ry + 11, 0xFF886532, false);
            }
            c.disableScissor();
        }
        for (int column = 0; column < 2; column++) for (int row = 0; row < rows; row++) {
            int px = x + column * half + 10, ry = y + 27 + row * 21;
            int index = BookPagination.index(page, column * rows + row, rows * 2);
            if (index < recipes.size() && within(mouseX, mouseY, px, ry, x + (column + 1) * half - 8, Math.min(ry + 20, y + h - 6))) {
                c.drawTooltip(textRenderer, describe(recipes.get(index)), mouseX, mouseY); return;
            }
        }
    }

    private void renderTransmutations(DrawContext c, int x, int y, int w, int h, int page, int mouseX, int mouseY) {
        var recipes = TransmutationRecipe.all();
        int half = w / 2, rows = listRows(21, h);
        for (int column = 0; column < 2; column++) {
            int px = x + column * half + 10;
            c.enableScissor(px, y + 8, x + (column + 1) * half - 7, y + h - 6);
            c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("screen.alkahest.journal.transmutations").getString(), half - 20), px, y + 10, 0xFF65462B, false);
            for (int row = 0; row < rows; row++) {
                int index = BookPagination.index(page, column * rows + row, rows * 2);
                if (index >= recipes.size()) break;
                var recipe = recipes.get(index);
                int ry = y + 27 + row * 21;
                if ((row & 1) == 0) c.fill(px, ry, x + (column + 1) * half - 8, ry + 20, 0x1A694C2C);
                c.drawItem(recipe.input(), px + 1, ry + 2);
                c.drawText(textRenderer, "→", px + 19, ry + 6, 0xFF765A39, false);
                c.drawItem(recipe.output(), px + 29, ry + 2);
                c.drawText(textRenderer, textRenderer.trimToWidth(recipe.output().getName().getString(), Math.max(10, half - 70)), px + 48, ry + 2, 0xFF483523, false);
                c.drawText(textRenderer, "◆ " + MatrixRecipe.price(recipe.cost()), px + 48, ry + 11, 0xFF886532, false);
            }
            c.disableScissor();
        }
        for (int column = 0; column < 2; column++) for (int row = 0; row < rows; row++) {
            int px = x + column * half + 10, ry = y + 27 + row * 21;
            int index = BookPagination.index(page, column * rows + row, rows * 2);
            if (index < recipes.size() && within(mouseX, mouseY, px, ry, x + (column + 1) * half - 8, Math.min(ry + 20, y + h - 6))) {
                var recipe = recipes.get(index);
                c.drawTooltip(textRenderer, List.of(
                    Text.translatable("screen.alkahest.journal.transmutation_input", recipe.input().getName()),
                    Text.translatable("screen.alkahest.journal.transmutation_output", recipe.output().getName()),
                    Text.translatable("screen.alkahest.matrix.index_cost").copy().append(": " + MatrixRecipe.price(recipe.cost()))
                ), mouseX, mouseY); return;
            }
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
        c.fill(x, y, x + width, y + 16, 0x66B79C6C);
        c.fill(x + 2, y + 14, x + width - 2, y + 15, 0x997C603B);
        centered(c, Text.translatable(key), x + width / 2, y + 4, 0xFF5B4028);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (selected != null) {
            if (button == 0) {
                var book = layout.detail();
                int bottom = book.y() + book.height();
                if (!book.contains(x, y) || within(x, y, book.x() + 15, bottom - 25, book.x() + 85, bottom - 9)) selected = null;
                else if (within(x, y, book.x() + book.width() - 110, bottom - 25, book.x() + book.width() - 66, bottom - 9)) detailPage = Math.max(0, detailPage - 1);
                else if (within(x, y, book.x() + book.width() - 59, bottom - 25, book.x() + book.width() - 15, bottom - 9)) detailPage = Math.min(detailPages - 1, detailPage + 1);
            }
            return true; // Modal consumes all clicks, including clicks outside the book.
        }
        if (button != 0) return super.mouseClicked(x, y, button);
        if (layout.map().contains(x, y)) {
            for (Node node : NODES) if (!state(node).equals("hidden") && hitNode(node, x, y)) { selected = node; detailPage = 0; dragging = false; return true; }
            dragging = true; return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && selected != null) { selected = null; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging && selected == null) {
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
        if (selected != null) {
            if (layout.detail().contains(x, y) && verticalAmount != 0)
                detailPage = MathHelper.clamp(detailPage + (verticalAmount < 0 ? 1 : -1), 0, detailPages - 1);
            return true;
        }
        if (layout.map().contains(x, y)) {
            if (verticalAmount != 0) {
                camera.zoomAt(Math.pow(1.15, verticalAmount), x - mapX(), y - (mapY() + mapH() / 2.0));
                clampCamera();
            }
            return true;
        }
        return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
    }
    @Override public boolean shouldPause() { return false; }
}
