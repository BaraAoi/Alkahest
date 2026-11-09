package net.alkahest.research;

import net.alkahest.Alkahest;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import java.util.List;

/** Server-owned early research, persisted per player by hidden vanilla advancements. */
public final class EarlyResearch {
    public static final int ROOT = 1, DISCOVERED = 2, FAILED_SAND = 4, FAILED_SUGAR = 8, COMPLETED = 16;
    private static final String[] IDS = {"root", "cauldron_discovered", "cauldron_sand", "cauldron_sugar", "cauldron_complete"};
    private static final int[] BITS = {ROOT, DISCOVERED, FAILED_SAND, FAILED_SUGAR, COMPLETED};
    public enum Attempt { NONE, SAND, SUGAR, COAL, ALREADY_FAILED }

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
        if (grant(player, "cauldron_discovered")) {
            player.sendMessage(Text.translatable("message.alkahest.research.insight"), false);
            player.sendMessage(Text.translatable("message.alkahest.research.insight"), true);
            world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
    }
    /** Only exact two-item inputs count; unrelated recipes are never intercepted. */
    public static Attempt identify(int flags, List<ItemStack> inputs) {
        if ((flags & DISCOVERED) == 0 || (flags & COMPLETED) != 0 || inputs.size() != 2) return Attempt.NONE;
        boolean charcoal = inputs.stream().anyMatch(s -> s.isOf(Items.CHARCOAL));
        if (!charcoal) return Attempt.NONE;
        if (inputs.stream().anyMatch(s -> s.isOf(Items.SAND))) return (flags & FAILED_SAND) == 0 ? Attempt.SAND : Attempt.ALREADY_FAILED;
        if (inputs.stream().anyMatch(s -> s.isOf(Items.SUGAR))) return (flags & FAILED_SUGAR) == 0 ? Attempt.SUGAR : Attempt.ALREADY_FAILED;
        if (inputs.stream().anyMatch(s -> s.isOf(Items.COAL))) return Attempt.COAL;
        return Attempt.NONE;
    }
    /** Returns true only if persistence succeeded, so missing datapack entries cannot consume inputs. */
    public static boolean record(ServerPlayerEntity player, Attempt attempt, ServerWorld world, BlockPos pos) {
        String id = switch (attempt) {
            case SAND -> "cauldron_sand";
            case SUGAR -> "cauldron_sugar";
            case COAL -> "cauldron_complete";
            default -> null;
        };
        if (id == null || !grant(player, id)) return false;
        String result = attempt == Attempt.COAL ? "success" : attempt == Attempt.SAND ? "sand" : "sugar";
        player.sendMessage(Text.translatable("message.alkahest.research." + result), false);
        world.spawnParticles(attempt == Attempt.COAL ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.SMOKE,
            pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 20, .25, .2, .25, .03);
        return true;
    }
    private EarlyResearch() { }
}
