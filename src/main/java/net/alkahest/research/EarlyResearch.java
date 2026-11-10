package net.alkahest.research;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModItems;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

import static net.alkahest.essence.EssenceType.*;

/** Server-owned early and Era II research, persisted per player by hidden vanilla advancements. */
public final class EarlyResearch {
    public static final int ROOT = 1, DISCOVERED = 2, FAILED_SAND = 4, FAILED_SUGAR = 8, COMPLETED = 16;
    public static final int REACTIVE_DISCOVERED = 32, FAILED_FEATHER = 64, FAILED_CLAY = 128, REACTIVE_COMPLETED = 256;
    public static final int RAW_DISCOVERED = 512, FAILED_IRON = 1024, FAILED_COBBLE = 2048, RAW_COMPLETED = 4096;
    public static final int SIGHT_DISCOVERED = 8192, FAILED_EYE = 16384, FAILED_TORCH = 32768, SIGHT_COMPLETED = 65536;
    public static final int IGNIS_DISCOVERED = 1 << 17, FAILED_CANE = 1 << 18, FAILED_IGNIS_FEATHER = 1 << 19, IGNIS_COMPLETED = 1 << 20;
    public static final int AQUA_DISCOVERED = 1 << 21, FAILED_AQUA_SAND = 1 << 22, FAILED_ICE = 1 << 23, AQUA_COMPLETED = 1 << 24;
    public static final int BINDING_DISCOVERED = 1 << 25, FAILED_SLIME = 1 << 26, FAILED_BONE_MEAL = 1 << 27, BINDING_COMPLETED = 1 << 28;

    private static final String[] IDS = {
        "root", "cauldron_discovered", "cauldron_sand", "cauldron_sugar", "cauldron_complete",
        "reactive_discovered", "reactive_feather", "reactive_clay", "reactive_complete",
        "raw_discovered", "raw_iron", "raw_cobble", "raw_complete",
        "sight_discovered", "sight_eye", "sight_torch", "sight_complete",
        "chain_ignis_discovered", "chain_ignis_cane", "chain_ignis_feather", "chain_ignis_complete",
        "chain_aqua_discovered", "chain_aqua_sand", "chain_aqua_ice", "chain_aqua_complete",
        "binding_discovered", "binding_slime", "binding_bone_meal", "binding_complete"
    };
    private static final int[] BITS = {
        ROOT, DISCOVERED, FAILED_SAND, FAILED_SUGAR, COMPLETED,
        REACTIVE_DISCOVERED, FAILED_FEATHER, FAILED_CLAY, REACTIVE_COMPLETED,
        RAW_DISCOVERED, FAILED_IRON, FAILED_COBBLE, RAW_COMPLETED,
        SIGHT_DISCOVERED, FAILED_EYE, FAILED_TORCH, SIGHT_COMPLETED,
        IGNIS_DISCOVERED, FAILED_CANE, FAILED_IGNIS_FEATHER, IGNIS_COMPLETED,
        AQUA_DISCOVERED, FAILED_AQUA_SAND, FAILED_ICE, AQUA_COMPLETED,
        BINDING_DISCOVERED, FAILED_SLIME, FAILED_BONE_MEAL, BINDING_COMPLETED
    };

    public enum Attempt {
        NONE, SAND, SUGAR, COAL, FEATHER, CLAY, REDSTONE,
        RAW_IRON, RAW_COBBLE, RAW_COPPER, SIGHT_EYE, SIGHT_TORCH, SIGHT_GLASS,
        IGNIS_CANE, IGNIS_FEATHER, IGNIS_COAL,
        AQUA_KELP, AQUA_SAND, AQUA_ICE,
        BINDING_STRING, BINDING_SLIME, BINDING_BONE_MEAL,
        ALREADY_FAILED
    }

    private static final Map<EssenceType, Integer> BASICS_COST = Map.of(IGNIS, 3, TERRA, 1);
    private static final Map<EssenceType, Integer> REACTIVE_COST = Map.of(PERDITIO, 5, IGNIS, 4);
    private static final Map<EssenceType, Integer> RAW_COST = Map.of(HERBA, 3, AQUA, 1);
    private static final Map<EssenceType, Integer> SIGHT_COST = Map.of(ORDO, 1);
    private static final Map<EssenceType, Integer> IGNIS_COST = Map.of(IGNIS, 8, POTENTIA, 4);
    private static final Map<EssenceType, Integer> AQUA_COST = Map.of(AQUA, 5, TERRA, 2);
    private static final Map<EssenceType, Integer> BINDING_COST = Map.of(ORDO, 2, TERRA, 1, AQUA, 1);

    private static AdvancementEntry entry(ServerPlayerEntity player, String name) {
        return player.getServer().getAdvancementLoader().get(Alkahest.id("research/" + name));
    }
    private static boolean has(ServerPlayerEntity player, String name) {
        var entry = entry(player, name);
        return entry != null && player.getAdvancementTracker().getProgress(entry).isDone();
    }
    private static boolean grant(ServerPlayerEntity player, String name) {
        var entry = entry(player, name);
        return entry != null && player.getAdvancementTracker().grantCriterion(entry, "done");
    }
    public static int flags(ServerPlayerEntity player) {
        int flags = 0;
        for (int i = 0; i < IDS.length; i++) if (has(player, IDS[i])) flags |= BITS[i];
        return flags;
    }

    public static boolean hasMonocle(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(ModItems.ALCHEMISTS_MONOCLE)
            || player.getOffHandStack().isOf(ModItems.ALCHEMISTS_MONOCLE)) {
            return true;
        }
        return TrinketsApi.getTrinketComponent(player)
            .map(component -> component.isEquipped(ModItems.ALCHEMISTS_MONOCLE))
            .orElse(false);
    }

    public static void openJournal(ServerPlayerEntity player) { grant(player, "root"); }
    public static void discoverCauldron(ServerPlayerEntity player, World world, BlockPos pos) {
        if (!has(player, "root") || has(player, "cauldron_discovered")) return;
        if (grant(player, "cauldron_discovered")) insight(player, world, pos);
    }
    public static void discoverReactive(ServerPlayerEntity player, World world, BlockPos pos) {
        if (!has(player, "cauldron_complete") || has(player, "reactive_discovered")) return;
        if (grant(player, "reactive_discovered")) insight(player, world, pos);
    }
    public static void discoverRaw(ServerPlayerEntity player, World world, BlockPos pos) {
        if (has(player, "cauldron_complete") && !has(player, "raw_discovered") && grant(player, "raw_discovered"))
            insight(player, world, pos);
    }
    public static void discoverSight(ServerPlayerEntity player, World world, BlockPos pos) {
        if (has(player, "raw_complete") && has(player, "reactive_complete") &&
            !has(player, "sight_discovered") && grant(player, "sight_discovered"))
            insight(player, world, pos);
    }
    public static void discoverIgnis(ServerPlayerEntity player, World world, BlockPos pos) {
        if (has(player, "sight_complete") && !has(player, "chain_ignis_discovered") && grant(player, "chain_ignis_discovered"))
            insight(player, world, pos);
    }
    public static void discoverAqua(ServerPlayerEntity player, World world, BlockPos pos) {
        if (has(player, "sight_complete") && !has(player, "chain_aqua_discovered") && grant(player, "chain_aqua_discovered"))
            insight(player, world, pos);
    }
    public static void discoverBinding(ServerPlayerEntity player, World world, BlockPos pos) {
        if (has(player, "chain_ignis_complete") && has(player, "chain_aqua_complete") &&
            !has(player, "binding_discovered") && grant(player, "binding_discovered"))
            insight(player, world, pos);
    }

    private static void insight(ServerPlayerEntity player, World world, BlockPos pos) {
        player.sendMessage(Text.translatable("message.alkahest.research.insight"), false);
        player.sendMessage(Text.translatable("message.alkahest.research.insight"), true);
        world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.0f);
    }
    private static boolean hasAll(Map<EssenceType, Integer> available, Map<EssenceType, Integer> required) {
        return required.entrySet().stream().allMatch(e -> available.getOrDefault(e.getKey(), 0) >= e.getValue());
    }

    /** Matches an active hypothesis when the subject's essences are dissolved and a candidate catalyst is applied. */
    public static Attempt identify(int flags, Map<EssenceType, Integer> essences, ItemStack catalyst) {
        if (catalyst.isEmpty()) return Attempt.NONE;
        if ((flags & DISCOVERED) != 0 && (flags & COMPLETED) == 0 && hasAll(essences, BASICS_COST)) {
            if (catalyst.isOf(Items.SAND)) return (flags & FAILED_SAND) == 0 ? Attempt.SAND : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.SUGAR)) return (flags & FAILED_SUGAR) == 0 ? Attempt.SUGAR : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.COAL)) return Attempt.COAL;
        }
        if ((flags & REACTIVE_DISCOVERED) != 0 && (flags & REACTIVE_COMPLETED) == 0 && hasAll(essences, REACTIVE_COST)) {
            if (catalyst.isOf(Items.FEATHER)) return (flags & FAILED_FEATHER) == 0 ? Attempt.FEATHER : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.CLAY_BALL)) return (flags & FAILED_CLAY) == 0 ? Attempt.CLAY : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.REDSTONE)) return Attempt.REDSTONE;
        }
        if ((flags & RAW_DISCOVERED) != 0 && (flags & RAW_COMPLETED) == 0 && hasAll(essences, RAW_COST)) {
            if (catalyst.isOf(Items.IRON_INGOT)) return (flags & FAILED_IRON) == 0 ? Attempt.RAW_IRON : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.COBBLESTONE)) return (flags & FAILED_COBBLE) == 0 ? Attempt.RAW_COBBLE : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.COPPER_INGOT)) return Attempt.RAW_COPPER;
        }
        if ((flags & SIGHT_DISCOVERED) != 0 && (flags & SIGHT_COMPLETED) == 0 && hasAll(essences, SIGHT_COST)) {
            if (catalyst.isOf(Items.SPIDER_EYE)) return (flags & FAILED_EYE) == 0 ? Attempt.SIGHT_EYE : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.TORCH)) return (flags & FAILED_TORCH) == 0 ? Attempt.SIGHT_TORCH : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.GLASS_PANE)) return Attempt.SIGHT_GLASS;
        }
        if ((flags & IGNIS_DISCOVERED) != 0 && (flags & IGNIS_COMPLETED) == 0 && hasAll(essences, IGNIS_COST)) {
            if (catalyst.isOf(Items.SUGAR_CANE)) return (flags & FAILED_CANE) == 0 ? Attempt.IGNIS_CANE : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.FEATHER)) return (flags & FAILED_IGNIS_FEATHER) == 0 ? Attempt.IGNIS_FEATHER : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.COAL)) return Attempt.IGNIS_COAL;
        }
        if ((flags & AQUA_DISCOVERED) != 0 && (flags & AQUA_COMPLETED) == 0 && hasAll(essences, AQUA_COST)) {
            if (catalyst.isOf(Items.KELP)) return Attempt.AQUA_KELP;
            if (catalyst.isOf(Items.SAND)) return (flags & FAILED_AQUA_SAND) == 0 ? Attempt.AQUA_SAND : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.ICE)) return (flags & FAILED_ICE) == 0 ? Attempt.AQUA_ICE : Attempt.ALREADY_FAILED;
        }
        if ((flags & BINDING_DISCOVERED) != 0 && (flags & BINDING_COMPLETED) == 0 && hasAll(essences, BINDING_COST)) {
            if (catalyst.isOf(Items.STRING)) return Attempt.BINDING_STRING;
            if (catalyst.isOf(Items.SLIME_BALL)) return (flags & FAILED_SLIME) == 0 ? Attempt.BINDING_SLIME : Attempt.ALREADY_FAILED;
            if (catalyst.isOf(Items.BONE_MEAL)) return (flags & FAILED_BONE_MEAL) == 0 ? Attempt.BINDING_BONE_MEAL : Attempt.ALREADY_FAILED;
        }
        return Attempt.NONE;
    }

    public static Map<EssenceType, Integer> cost(Attempt attempt) {
        return switch (attempt) {
            case SAND, SUGAR, COAL -> BASICS_COST;
            case FEATHER, CLAY, REDSTONE -> REACTIVE_COST;
            case RAW_IRON, RAW_COBBLE, RAW_COPPER -> RAW_COST;
            case SIGHT_EYE, SIGHT_TORCH, SIGHT_GLASS -> SIGHT_COST;
            case IGNIS_CANE, IGNIS_FEATHER, IGNIS_COAL -> IGNIS_COST;
            case AQUA_KELP, AQUA_SAND, AQUA_ICE -> AQUA_COST;
            case BINDING_STRING, BINDING_SLIME, BINDING_BONE_MEAL -> BINDING_COST;
            default -> Map.of();
        };
    }

    /** Returns true only if persistence succeeded, so missing datapack entries cannot consume inputs. */
    public static boolean record(ServerPlayerEntity player, Attempt attempt, ServerWorld world, BlockPos pos) {
        String id = switch (attempt) {
            case SAND -> "cauldron_sand";
            case SUGAR -> "cauldron_sugar";
            case COAL -> "cauldron_complete";
            case FEATHER -> "reactive_feather";
            case CLAY -> "reactive_clay";
            case REDSTONE -> "reactive_complete";
            case RAW_IRON -> "raw_iron"; case RAW_COBBLE -> "raw_cobble"; case RAW_COPPER -> "raw_complete";
            case SIGHT_EYE -> "sight_eye"; case SIGHT_TORCH -> "sight_torch"; case SIGHT_GLASS -> "sight_complete";
            case IGNIS_CANE -> "chain_ignis_cane"; case IGNIS_FEATHER -> "chain_ignis_feather"; case IGNIS_COAL -> "chain_ignis_complete";
            case AQUA_KELP -> "chain_aqua_complete"; case AQUA_SAND -> "chain_aqua_sand"; case AQUA_ICE -> "chain_aqua_ice";
            case BINDING_STRING -> "binding_complete"; case BINDING_SLIME -> "binding_slime"; case BINDING_BONE_MEAL -> "binding_bone_meal";
            default -> null;
        };
        if (id == null || !grant(player, id)) return false;
        String result = switch (attempt) {
            case SAND -> "sand"; case SUGAR -> "sugar"; case COAL -> "success";
            case FEATHER -> "feather"; case CLAY -> "clay"; case REDSTONE -> "reactive_success";
            case RAW_IRON -> "raw_iron"; case RAW_COBBLE -> "raw_cobble"; case RAW_COPPER -> "raw_success";
            case SIGHT_EYE -> "sight_eye"; case SIGHT_TORCH -> "sight_torch"; case SIGHT_GLASS -> "sight_success";
            case IGNIS_CANE -> "ignis_cane"; case IGNIS_FEATHER -> "ignis_feather"; case IGNIS_COAL -> "ignis_success";
            case AQUA_KELP -> "aqua_success"; case AQUA_SAND -> "aqua_sand"; case AQUA_ICE -> "aqua_ice";
            case BINDING_STRING -> "binding_success"; case BINDING_SLIME -> "binding_slime"; case BINDING_BONE_MEAL -> "binding_bone_meal";
            default -> throw new IllegalStateException("Unexpected attempt");
        };
        player.sendMessage(Text.translatable("message.alkahest.research." + result), false);
        boolean success = attempt == Attempt.COAL || attempt == Attempt.REDSTONE || attempt == Attempt.RAW_COPPER
            || attempt == Attempt.SIGHT_GLASS || attempt == Attempt.IGNIS_COAL || attempt == Attempt.AQUA_KELP
            || attempt == Attempt.BINDING_STRING;
        world.spawnParticles(success ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.LARGE_SMOKE,
            pos.getX() + .5, pos.getY() + 1.15, pos.getZ() + .5, success ? 30 : 55, .32, .28, .32, .08);
        if (!success) {
            world.spawnParticles(ParticleTypes.POOF, pos.getX() + .5, pos.getY() + 1.1, pos.getZ() + .5, 18, .28, .2, .28, .02);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, .8f);
        }
        return true;
    }
    private EarlyResearch() { }
}
