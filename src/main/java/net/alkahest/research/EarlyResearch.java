package net.alkahest.research;

import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
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

/** Server-owned early research, persisted per player by hidden vanilla advancements. */
public final class EarlyResearch {
    public static final int ROOT = 1, DISCOVERED = 2, FAILED_SAND = 4, FAILED_SUGAR = 8, COMPLETED = 16;
    public static final int REACTIVE_DISCOVERED = 32, FAILED_FEATHER = 64, FAILED_CLAY = 128, REACTIVE_COMPLETED = 256;
    public static final int RAW_DISCOVERED = 512, FAILED_IRON = 1024, FAILED_COBBLE = 2048, RAW_COMPLETED = 4096;
    public static final int SIGHT_DISCOVERED = 8192, FAILED_EYE = 16384, FAILED_TORCH = 32768, SIGHT_COMPLETED = 65536;
    private static final String[] IDS = {"root", "cauldron_discovered", "cauldron_sand", "cauldron_sugar", "cauldron_complete",
        "reactive_discovered", "reactive_feather", "reactive_clay", "reactive_complete",
        "raw_discovered", "raw_iron", "raw_cobble", "raw_complete",
        "sight_discovered", "sight_eye", "sight_torch", "sight_complete"};
    private static final int[] BITS = {ROOT, DISCOVERED, FAILED_SAND, FAILED_SUGAR, COMPLETED,
        REACTIVE_DISCOVERED, FAILED_FEATHER, FAILED_CLAY, REACTIVE_COMPLETED,
        RAW_DISCOVERED, FAILED_IRON, FAILED_COBBLE, RAW_COMPLETED,
        SIGHT_DISCOVERED, FAILED_EYE, FAILED_TORCH, SIGHT_COMPLETED};
    public enum Attempt { NONE, SAND, SUGAR, COAL, FEATHER, CLAY, REDSTONE,
        RAW_IRON, RAW_COBBLE, RAW_COPPER, SIGHT_EYE, SIGHT_TORCH, SIGHT_GLASS, ALREADY_FAILED }

    private static final Map<EssenceType, Integer> BASICS_COST = Map.of(IGNIS, 3, TERRA, 1);
    private static final Map<EssenceType, Integer> REACTIVE_COST = Map.of(PERDITIO, 5, IGNIS, 4);
    private static final Map<EssenceType, Integer> RAW_COST = Map.of(HERBA, 3, AQUA, 1);
    private static final Map<EssenceType, Integer> SIGHT_COST = Map.of(ORDO, 1);

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
        return Attempt.NONE;
    }

    public static Map<EssenceType, Integer> cost(Attempt attempt) {
        return switch (attempt) {
            case SAND, SUGAR, COAL -> BASICS_COST;
            case FEATHER, CLAY, REDSTONE -> REACTIVE_COST;
            case RAW_IRON, RAW_COBBLE, RAW_COPPER -> RAW_COST;
            case SIGHT_EYE, SIGHT_TORCH, SIGHT_GLASS -> SIGHT_COST;
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
            default -> null;
        };
        if (id == null || !grant(player, id)) return false;
        String result = switch (attempt) {
            case SAND -> "sand"; case SUGAR -> "sugar"; case COAL -> "success";
            case FEATHER -> "feather"; case CLAY -> "clay"; case REDSTONE -> "reactive_success";
            case RAW_IRON -> "raw_iron"; case RAW_COBBLE -> "raw_cobble"; case RAW_COPPER -> "raw_success";
            case SIGHT_EYE -> "sight_eye"; case SIGHT_TORCH -> "sight_torch"; case SIGHT_GLASS -> "sight_success";
            default -> throw new IllegalStateException("Unexpected attempt");
        };
        player.sendMessage(Text.translatable("message.alkahest.research." + result), false);
        boolean success = attempt == Attempt.COAL || attempt == Attempt.REDSTONE || attempt == Attempt.RAW_COPPER || attempt == Attempt.SIGHT_GLASS;
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
