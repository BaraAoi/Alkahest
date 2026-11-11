package net.alkahest.essence;

import net.alkahest.network.EssenceMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
        for (var entry : server.getRecipeManager().getAllOfType(RecipeType.CRAFTING)) {
            var recipe = entry.value();
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
        var computed = RecipeEssenceCalculator.calculate(seeds, recipes);
        Map<Item, List<EssenceStack>> snapshot = new HashMap<>();
        for (var entry : computed.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null && Registries.ITEM.containsId(id)) {
                List<EssenceStack> values = RecipeEssenceCalculator.rounded(entry.getValue());
                if (!values.isEmpty()) snapshot.put(Registries.ITEM.get(id), values);
            }
        }
        SERVERS.put(server, Map.copyOf(snapshot));
        LoggerFactory.getLogger("Alkahest").info("Calculated essence compositions for {} items from {} crafting recipes", snapshot.size(), recipes.size());
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
