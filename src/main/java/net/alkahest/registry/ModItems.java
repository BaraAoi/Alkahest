package net.alkahest.registry;

import dev.emi.trinkets.api.TrinketItem;
import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.item.custom.ReagentItem;
import net.alkahest.item.custom.AlchemicalJournalItem;
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
    public static final Item ALCHEMICAL_JOURNAL = item("alchemical_journal", new AlchemicalJournalItem(new Item.Settings().maxCount(1)));
    public static final Item ALCHEMISTS_MONOCLE = item("alchemists_monocle", new TrinketItem(new Item.Settings().maxCount(1)));
    public static final Item SALT = item("salt", new Item(new Item.Settings()));
    public static final Item PURIFIED_WATER = item("purified_water", new Item(new Item.Settings()));
    public static final Item HERBAL_PASTE = item("herbal_paste", new Item(new Item.Settings()));
    public static final Item FIRE_DUST = item("fire_dust", new ReagentItem(new Item.Settings()));
    public static final Item CAUSTIC_SLIME = item("caustic_slime", new ReagentItem(new Item.Settings()));
    public static final Item NAPALM = item("napalm", new ReagentItem(new Item.Settings()));
    public static final Item ACID_BOMB = item("acid_bomb", new ReagentItem(new Item.Settings()));
    public static final Item LIGHTNING_IN_A_BOTTLE = item("lightning_in_a_bottle", new ReagentItem(new Item.Settings()));
    public static final Item MIASMA = item("miasma", new ReagentItem(new Item.Settings()));
    public static final Item BINDING_SALT = item("binding_salt", new Item(new Item.Settings()));
    public static final Item ASH = item("ash", new Item(new Item.Settings()));
    public static final Map<EssenceType, Item> CRYSTALS = crystals();
    public static final Map<EssenceType, Item> WANDS = wands();
    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Alkahest.id("main"),
        FabricItemGroup.builder().displayName(Text.translatable("itemGroup.alkahest.main"))
            .icon(() -> new ItemStack(ALCHEMISTS_MONOCLE))
            .entries((context, entries) -> {
                entries.add(ModBlocks.ALCHEMY_CAULDRON);
                entries.add(ModBlocks.DISTILLERY);
                entries.add(ModBlocks.JAR);
                entries.add(ModBlocks.ESSENCE_CHARGER);
                entries.add(ModBlocks.ESSENCE_SEPARATOR);
                entries.add(ModBlocks.ALCHEMY_PIPE);
                entries.add(ModBlocks.SALT_ORE);
                entries.add(ALCHEMICAL_JOURNAL);
                entries.add(ALCHEMISTS_MONOCLE);
                entries.add(SALT); entries.add(PURIFIED_WATER); entries.add(HERBAL_PASTE);
                entries.add(FIRE_DUST); entries.add(CAUSTIC_SLIME); entries.add(NAPALM); entries.add(ACID_BOMB); entries.add(LIGHTNING_IN_A_BOTTLE); entries.add(MIASMA); entries.add(BINDING_SALT); entries.add(ASH);
                for (EssenceType type : EssenceType.values()) {
                    Item item = CRYSTALS.get(type);
                    if (item != null) entries.add(item);
                    Item wand = WANDS.get(type);
                    if (wand != null) entries.add(wand);
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
    private static Map<EssenceType, Item> wands() {
        Map<EssenceType, Item> result = new EnumMap<>(EssenceType.class);
        for (EssenceType type : EssenceType.values()) {
            if (type.tier() == EssenceType.Tier.PRIMA)
                result.put(type, item("crystal_wand_" + type.asString(), new CrystalWandItem(type)));
        }
        return Map.copyOf(result);
    }
    private static Item item(String name, Item item) { return Registry.register(Registries.ITEM, Alkahest.id(name), item); }
    public static void register() { }
    private ModItems() { }
}
