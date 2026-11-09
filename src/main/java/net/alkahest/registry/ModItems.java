package net.alkahest.registry;

import dev.emi.trinkets.api.TrinketItem;
import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final Item ALCHEMISTS_MONOCLE = item("alchemists_monocle", new TrinketItem(new Item.Settings().maxCount(1)));
    public static final Map<EssenceType, Item> CRYSTALS = crystals();
    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Alkahest.id("main"),
        FabricItemGroup.builder().displayName(Text.translatable("itemGroup.alkahest.main"))
            .icon(() -> new ItemStack(ALCHEMISTS_MONOCLE))
            .entries((context, entries) -> {
                entries.add(ALCHEMISTS_MONOCLE);
                for (EssenceType type : EssenceType.values()) {
                    Item item = CRYSTALS.get(type);
                    if (item != null) entries.add(item);
                }
            }).build());

    private static Map<EssenceType, Item> crystals() {
        Map<EssenceType, Item> result = new EnumMap<>(EssenceType.class);
        for (EssenceType type : EssenceType.values()) {
            if (type == EssenceType.VACUUM) continue;
            result.put(type, item("crystal_" + type.asString(), new Item(new Item.Settings())));
        }
        return Map.copyOf(result);
    }
    private static Item item(String name, Item item) { return Registry.register(Registries.ITEM, Alkahest.id(name), item); }
    public static void register() { }
    private ModItems() { }
}
