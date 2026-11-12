package net.alkahest.essence;

import net.alkahest.network.EssenceMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.alkahest.mixin.SmithingTransformRecipeAccessor;
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
                && type != RecipeType.SMOKING && type != RecipeType.CAMPFIRE_COOKING && type != RecipeType.STONECUTTING
                && !(recipe instanceof SmithingTransformRecipe)) continue;
            if (recipe instanceof SpecialCraftingRecipe) continue;
            try {
                ItemStack output = recipe.getResult(server.getRegistryManager());
                if (output.isEmpty()) continue;
                List<List<String>> slots = new ArrayList<>();
                Map<String, String> remainders = new HashMap<>();
                // Smithing transforms have three real consumed slots, including the template.
                // Trims and arbitrary custom smithing serializers can have dynamic results and are not inferred.
                var ingredients = recipe instanceof SmithingTransformRecipe transform
                    ? List.of(((SmithingTransformRecipeAccessor) transform).alkahest$template(),
                              ((SmithingTransformRecipeAccessor) transform).alkahest$base(),
                              ((SmithingTransformRecipeAccessor) transform).alkahest$addition())
                    : recipe.getIngredients();
                for (var ingredient : ingredients) {
                    if (ingredient.isEmpty()) {
                        if (recipe instanceof SmithingTransformRecipe) slots.add(List.of());
                        continue; // blank shaped-grid position; empty required smithing slot cannot be inferred
                    }
                    List<String> alternatives = new ArrayList<>();
                    for (ItemStack candidate : ingredient.getMatchingStacks()) {
                        if (candidate.isEmpty()) continue;
                        Item item = candidate.getItem();
                        String id = Registries.ITEM.getId(item).toString();
                        alternatives.add(id);
                        if (item.hasRecipeRemainder())
                            remainders.put(id, Registries.ITEM.getId(item.getRecipeRemainder()).toString());
                    }
                    slots.add(alternatives);
                }
                recipes.add(new RecipeEssenceCalculator.Recipe(entry.id().toString(),
                    Registries.ITEM.getId(output.getItem()).toString(), output.getCount(), slots, remainders));
            } catch (RuntimeException ex) {
                LoggerFactory.getLogger("Alkahest").warn("Could not read crafting recipe {} for essence calculation", entry.id(), ex);
            }
        }
        addAlchemyRecipes(recipes);
        addVanillaWorldTransformations(recipes);
        var calculation = RecipeEssenceCalculator.calculateDetailed(seeds, recipes);
        var computed = calculation.values();
        Map<Item, List<EssenceStack>> snapshot = new HashMap<>();
        for (var entry : computed.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null && Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                List<EssenceStack> explicit = ItemEssenceMap.base(new ItemStack(item));
                // Preserve the previous order of explicit values as it affects bellow/output order.
                List<EssenceStack> values = explicit.isEmpty() ? RecipeEssenceCalculator.rounded(entry.getValue(), calculation.depths().getOrDefault(entry.getKey(), 0)) : explicit;
                if (!values.isEmpty()) snapshot.put(item, values);
            }
        }
        SERVERS.put(server, Map.copyOf(snapshot));
        writeReport(server, snapshot);
        LoggerFactory.getLogger("Alkahest").info("Calculated essence compositions for {} items from {} recipes", snapshot.size(), recipes.size());
    }

    /** Actual alchemy machines consume both item inputs and the paid essences. */
    private static void addAlchemyRecipes(List<RecipeEssenceCalculator.Recipe> recipes) {
        // Inventory mixing has no vanilla recipe JSON; account for both consumed inputs.
        addAlchemyRecipe(recipes, "inventory_mix", 0, net.alkahest.registry.ModItems.NAPALM, 1,
            List.of(net.alkahest.registry.ModItems.FIRE_DUST, net.minecraft.item.Items.SLIME_BALL), Map.of());
        addAlchemyRecipe(recipes, "inventory_mix", 1, net.alkahest.registry.ModItems.ACID_BOMB, 1,
            List.of(net.alkahest.registry.ModItems.CAUSTIC_SLIME, net.minecraft.item.Items.GUNPOWDER), Map.of());
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

    /** Known vanilla world interactions with a conserved source (not guesses about modded items). */
    private static void addVanillaWorldTransformations(List<RecipeEssenceCalculator.Recipe> recipes) {
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String color : colors) {
            worldTransform(recipes, color + "_concrete_powder", color + "_concrete");
            // Vanilla special crafting recolours a shulker box using one dye.
            recipes.add(new RecipeEssenceCalculator.Recipe("~world/" + color + "_shulker_box",
                "minecraft:" + color + "_shulker_box", 1,
                List.of(List.of("minecraft:shulker_box"), List.of("minecraft:" + color + "_dye"))));
        }
        worldTransform(recipes, "dirt", "dirt_path");
        worldTransform(recipes, "dirt", "farmland");
        worldTransform(recipes, "pumpkin", "carved_pumpkin");
        worldTransform(recipes, "sponge", "wet_sponge");
        worldTransform(recipes, "anvil", "chipped_anvil");
        worldTransform(recipes, "chipped_anvil", "damaged_anvil");
        // Oxidation changes state but does not consume the underlying copper block or device.
        String[] variants = {"copper", "cut_copper", "cut_copper_slab", "cut_copper_stairs",
            "chiseled_copper", "copper_grate", "copper_bulb", "copper_door", "copper_trapdoor"};
        for (String variant : variants) {
            String original = variant.equals("copper") ? "copper_block" : variant;
            worldTransform(recipes, original, "exposed_" + variant);
            worldTransform(recipes, "exposed_" + variant, "weathered_" + variant);
            worldTransform(recipes, "weathered_" + variant, "oxidized_" + variant);
        }
    }
    private static void worldTransform(List<RecipeEssenceCalculator.Recipe> recipes, String from, String to) {
        recipes.add(new RecipeEssenceCalculator.Recipe("~world/" + to, "minecraft:" + to, 1,
            List.of(List.of("minecraft:" + from))));
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
