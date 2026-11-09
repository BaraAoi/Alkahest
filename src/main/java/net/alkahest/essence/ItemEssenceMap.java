package net.alkahest.essence;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

import static net.alkahest.essence.EssenceType.*;

/** Default values for the early distillation/monocle systems. Values are per item. */
public final class ItemEssenceMap {
    private static final TagKey<Item> FLOWERS = tag("minecraft", "flowers");
    private static final TagKey<Item> LEAVES = tag("minecraft", "leaves");
    private static final TagKey<Item> LOGS = tag("minecraft", "logs");
    private static final TagKey<Item> PLANKS = tag("minecraft", "planks");
    private static final TagKey<Item> SEEDS = tag("c", "seeds");
    private static final Map<Item, List<EssenceStack>> VALUES = Map.ofEntries(
        e(Items.APPLE, s(HERBA, 3), s(AQUA, 1), s(ORDO, 1)),
        e(Items.BREAD, s(HERBA, 3), s(ORDO, 1)),
        e(Items.CARROT, s(HERBA, 3), s(AQUA, 1)),
        e(Items.COBBLESTONE, s(TERRA, 2), s(PERDITIO, 1)),
        e(Items.STONE, s(TERRA, 2), s(ORDO, 1)),
        e(Items.COAL, s(IGNIS, 3), s(TERRA, 1)),
        e(Items.BLAZE_ROD, s(IGNIS, 8), s(POTENTIA, 4)),
        e(Items.WATER_BUCKET, s(AQUA, 10)),
        e(Items.PRISMARINE_SHARD, s(AQUA, 5), s(TERRA, 2)),
        e(Items.FEATHER, s(AER, 4), s(MOTUS, 2)),
        e(Items.SUGAR, s(AER, 2), s(MOTUS, 1)),
        e(Items.REDSTONE, s(POTENTIA, 4), s(ORDO, 2)),
        e(Items.GOLD_INGOT, s(METALLUM, 4), s(ORDO, 6), s(LUX, 2)),
        e(Items.IRON_INGOT, s(METALLUM, 6), s(TERRA, 2)),
        e(Items.COPPER_INGOT, s(METALLUM, 4), s(POTENTIA, 1)),
        e(Items.DIAMOND, s(TERRA, 15), s(ORDO, 15), s(LUX, 10)),
        e(Items.ROTTEN_FLESH, s(MORTUUS, 4), s(PERDITIO, 3)),
        e(Items.BONE, s(MORTUUS, 4), s(TERRA, 2)),
        e(Items.SPIDER_EYE, s(VITIUM, 3), s(PERDITIO, 2)),
        e(Items.GUNPOWDER, s(PERDITIO, 5), s(IGNIS, 4)),
        e(Items.ENDER_PEARL, s(SPIRITUS, 6), s(MOTUS, 6), s(PERDITIO, 2)),
        e(Items.AMETHYST_SHARD, s(ORDO, 5), s(LUX, 3))
    );

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.of(RegistryKeys.ITEM, Identifier.of(namespace, path));
    }
    private static EssenceStack s(EssenceType type, int amount) { return new EssenceStack(type, amount, 1.0f); }
    private static Map.Entry<Item, List<EssenceStack>> e(Item item, EssenceStack... values) { return Map.entry(item, List.of(values)); }

    public static List<EssenceStack> get(ItemStack stack) {
        List<EssenceStack> exact = VALUES.get(stack.getItem());
        if (exact != null) return exact;
        if (stack.isIn(FLOWERS) || stack.isIn(LEAVES) || stack.isIn(SEEDS)
            || stack.isOf(Items.WHEAT_SEEDS) || stack.isOf(Items.BEETROOT_SEEDS)
            || stack.isOf(Items.MELON_SEEDS) || stack.isOf(Items.PUMPKIN_SEEDS)
            || stack.isOf(Items.TORCHFLOWER_SEEDS) || stack.isOf(Items.PITCHER_POD)) {
            return List.of(s(HERBA, 3), s(AQUA, 1));
        }
        if (stack.isIn(LOGS) || stack.isIn(PLANKS)) return List.of(s(HERBA, 4), s(TERRA, 2));
        return List.of();
    }
    private ItemEssenceMap() { }
}
