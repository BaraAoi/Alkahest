package net.alkahest.essence;

import net.alkahest.network.EssenceMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Builds immutable snapshots after tags and recipes load; never shares client and server state. */
public final class RecipeEssenceService {
    private static final Map<MinecraftServer, Map<Item, List<EssenceStack>>> SERVERS = new WeakHashMap<>();
    private static volatile Map<Item, List<EssenceStack>> client;
    private static Map<Item, List<EssenceStack>> incoming;

    public static Map<Item, List<EssenceStack>> serverValues(MinecraftServer server) { return SERVERS.get(server); }
    public static Map<Item, List<EssenceStack>> clientValues() { return client; }
    public static void clearServer(MinecraftServer server) { SERVERS.remove(server); }
    public static void clearClient() { client = null; incoming = null; }

    public static void rebuild(MinecraftServer server) {
        Map<String, Map<EssenceType, Double>> seeds = new HashMap<>();
        for (Item item : Registries.ITEM) {
            List<EssenceStack> values = ItemEssenceMap.base(new ItemStack(item));
            if (values.isEmpty()) continue;
            EnumMap<EssenceType, Double> counts = new EnumMap<>(EssenceType.class);
            values.forEach(value -> counts.put(value.type(), (double) value.amount()));
            seeds.put(Registries.ITEM.getId(item).toString(), counts);
        }
        List<RecipeEssenceCalculator.Recipe> recipes = new ArrayList<>();
        for (var entry : server.getRecipeManager().values()) {
            var recipe = entry.value();
            var type = recipe.getType();
            if (type != RecipeType.CRAFTING && type != RecipeType.SMELTING && type != RecipeType.BLASTING
                && type != RecipeType.SMOKING && type != RecipeType.CAMPFIRE_COOKING && type != RecipeType.STONECUTTING) continue;
            if (recipe instanceof SpecialCraftingRecipe) continue;
            try {
                ItemStack output = recipe.getResult(server.getRegistryManager());
                if (output.isEmpty()) continue;
                List<List<String>> slots = new ArrayList<>();
                for (var ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) continue; // blank shaped-grid position
                    List<String> alternatives = new ArrayList<>();
                    for (ItemStack candidate : ingredient.getMatchingStacks()) {
                        // Containers/tools returned by crafting are not fully consumed.
                        if (!candidate.isEmpty() && !candidate.getItem().hasRecipeRemainder())
                            alternatives.add(Registries.ITEM.getId(candidate.getItem()).toString());
                    }
                    slots.add(alternatives);
                }
                recipes.add(new RecipeEssenceCalculator.Recipe(entry.id().toString(),
                    Registries.ITEM.getId(output.getItem()).toString(), output.getCount(), slots));
            } catch (RuntimeException ex) {
                LoggerFactory.getLogger("Alkahest").warn("Could not read crafting recipe {} for essence calculation", entry.id(), ex);
            }
        }
        addAlchemyRecipes(recipes);
        var computed = RecipeEssenceCalculator.calculate(seeds, recipes);
        Map<Item, List<EssenceStack>> snapshot = new HashMap<>();
        for (var entry : computed.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null && Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                List<EssenceStack> explicit = ItemEssenceMap.base(new ItemStack(item));
                // Preserve the previous order of explicit values as it affects bellow/output order.
                List<EssenceStack> values = explicit.isEmpty() ? RecipeEssenceCalculator.rounded(entry.getValue()) : explicit;
                if (!values.isEmpty()) snapshot.put(item, values);
            }
        }
        SERVERS.put(server, Map.copyOf(snapshot));
        writeReport(server, snapshot);
        LoggerFactory.getLogger("Alkahest").info("Calculated essence compositions for {} items from {} recipes", snapshot.size(), recipes.size());
    }

    /** Actual alchemy machines consume both item inputs and the paid essences. */
    private static void addAlchemyRecipes(List<RecipeEssenceCalculator.Recipe> recipes) {
        int index = 0;
        for (var recipe : net.alkahest.recipe.CauldronEssenceRecipes.all()) {
            addAlchemyRecipe(recipes, "cauldron", index++, recipe.result(), recipe.count(),
                List.of(recipe.catalyst()), recipe.cost());
        }
        index = 0;
        for (var recipe : net.alkahest.matrix.MatrixRecipe.all()) {
            List<Item> ingredients = new ArrayList<>();
            ingredients.add(recipe.base());
            ingredients.addAll(recipe.components());
            addAlchemyRecipe(recipes, "matrix", index++, recipe.output().getItem(), recipe.output().getCount(),
                ingredients, recipe.cost());
        }
        index = 0;
        for (var recipe : net.alkahest.transmutation.TransmutationRecipe.all()) {
            addAlchemyRecipe(recipes, "transmutator", index++, recipe.output().getItem(), recipe.output().getCount(),
                List.of(recipe.input().getItem()), recipe.cost());
        }
    }

    private static void addAlchemyRecipe(List<RecipeEssenceCalculator.Recipe> recipes, String machine, int index,
            Item output, int count, List<Item> ingredients, Map<EssenceType, Integer> cost) {
        List<List<String>> slots = new ArrayList<>();
        for (Item ingredient : ingredients) slots.add(List.of(Registries.ITEM.getId(ingredient).toString()));
        for (EssenceType type : EssenceType.values()) {
            int amount = cost.getOrDefault(type, 0);
            Item crystal = net.alkahest.registry.ModItems.CRYSTALS.get(type);
            if (amount > 0 && crystal == null) return;
            for (int i = 0; i < amount; i++) slots.add(List.of(Registries.ITEM.getId(crystal).toString()));
        }
        recipes.add(new RecipeEssenceCalculator.Recipe("~alkahest/" + machine + "/" + String.format(java.util.Locale.ROOT, "%03d", index),
            Registries.ITEM.getId(output).toString(), count, slots));
    }

    /** The report describes this world's actual loaded registry and current datapack snapshot. */
    public static Path reportPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("alkahest").resolve("item-essences.tsv");
    }

    private static void writeReport(MinecraftServer server, Map<Item, List<EssenceStack>> snapshot) {
        Path path = reportPath(server);
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            List<Item> items = new ArrayList<>();
            Registries.ITEM.forEach(items::add);
            items.sort(java.util.Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
            StringBuilder report = new StringBuilder("# Alkahest: all registered items for this world; regenerated on startup and successful reload\n")
                .append("# Values are for default item stacks; missing means no reliable source or recipe.\n")
                .append("id\tkind\tstatus\tessences\n");
            for (Item item : items) {
                List<EssenceStack> values = snapshot.getOrDefault(item, List.of());
                String status = values.isEmpty() ? "missing" : ItemEssenceMap.base(new ItemStack(item)).isEmpty() ? "recipe" : "manual";
                report.append(Registries.ITEM.getId(item)).append('\t').append(item instanceof BlockItem ? "block" : "item")
                    .append('\t').append(status).append('\t');
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) report.append(',');
                    report.append(values.get(i).type().asString()).append('=').append(values.get(i).amount());
                }
                report.append('\n');
            }
            Files.writeString(temp, report, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LoggerFactory.getLogger("Alkahest").warn("Could not write essence coverage report {}", path, ex);
        }
    }

    /** Chunk the snapshot below the vanilla STRING codec's 32767-character limit. */
    public static void send(ServerPlayerEntity player) {
        var values = serverValues(player.getServer());
        if (values == null) return;
        ServerPlayNetworking.send(player, new EssenceMapPayload("BEGIN"));
        StringBuilder chunk = new StringBuilder();
        var sorted = values.entrySet().stream().sorted(java.util.Comparator.comparing(e -> Registries.ITEM.getId(e.getKey()).toString())).toList();
        for (var entry : sorted) {
            StringBuilder line = new StringBuilder(Registries.ITEM.getId(entry.getKey()).toString()).append('=');
            for (EssenceStack stack : entry.getValue()) line.append(stack.type().ordinal()).append(':').append(stack.amount()).append(',');
            line.append(';');
            if (chunk.length() + line.length() > 12000 && !chunk.isEmpty()) {
                ServerPlayNetworking.send(player, new EssenceMapPayload(chunk.toString()));
                chunk.setLength(0);
            }
            chunk.append(line);
        }
        if (!chunk.isEmpty()) ServerPlayNetworking.send(player, new EssenceMapPayload(chunk.toString()));
        ServerPlayNetworking.send(player, new EssenceMapPayload("END"));
    }

    /** Called on the client thread. An incomplete snapshot is never exposed to the UI. */
    public static void receive(String chunk) {
        if (chunk.equals("BEGIN")) { incoming = new HashMap<>(); return; }
        if (chunk.equals("END")) {
            if (incoming != null) client = Map.copyOf(incoming);
            incoming = null;
            return;
        }
        if (incoming == null) return;
        for (String line : chunk.split(";")) {
            int equal = line.indexOf('=');
            if (equal < 0) continue;
            Identifier id = Identifier.tryParse(line.substring(0, equal));
            if (id == null || !Registries.ITEM.containsId(id)) continue;
            try {
                List<EssenceStack> list = new ArrayList<>();
                for (String part : line.substring(equal + 1).split(",")) {
                    String[] pair = part.split(":");
                    list.add(new EssenceStack(EssenceType.values()[Integer.parseInt(pair[0])], Integer.parseInt(pair[1]), 1f));
                }
                incoming.put(Registries.ITEM.get(id), List.copyOf(list));
            } catch (RuntimeException ignored) { /* Ignore invalid entries from a malformed packet. */ }
        }
    }
    private RecipeEssenceService() { }
}
