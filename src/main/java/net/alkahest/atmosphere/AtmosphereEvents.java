package net.alkahest.atmosphere;

import net.alkahest.entity.ElementalEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModEntities;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Server-only emissions, player inspection and bounded manifestation attempts. */
public final class AtmosphereEvents {
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(AtmosphereEvents::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
            CommandManager.literal("alkahest_air").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                ServerWorld world = player.getServerWorld(); BlockPos pos = player.getBlockPos();
                AtmosphereState state = AtmosphereState.of(world);
                Map<EssenceType, Double> levels = state.levels(world, pos), base = state.baseline(world, pos);
                BlockPos origin = AtmosphereState.cellOrigin(pos);
                player.sendMessage(Text.translatable("message.alkahest.air_header", origin.getX(), origin.getY(), origin.getZ()), false);
                for (EssenceType type : AtmosphereMath.PRIMALS) {
                    player.sendMessage(Text.translatable("message.alkahest.air_value", type.displayName(),
                        String.format(java.util.Locale.ROOT, "%.1f", levels.get(type)),
                        String.format(java.util.Locale.ROOT, "%.1f", base.get(type)))
                        .styled(style -> style.withColor(type.color())), false);
                }
                player.sendMessage(Text.translatable("message.alkahest.air_chance",
                    String.format(java.util.Locale.ROOT, "%.1f", AtmosphereMath.spawnChance(levels) * 100)), false);
                return 1;
            })));
    }
    public static void emit(World world, BlockPos pos, EssenceType type, double amount) {
        if (world instanceof ServerWorld server) AtmosphereState.of(server).add(server, pos, type, amount);
    }
    public static void emit(World world, BlockPos pos, Map<EssenceType, ? extends Number> amounts, double factor) {
        if (world instanceof ServerWorld server) AtmosphereState.of(server).add(server, pos, amounts, factor);
    }
    /** Stagger telemetry per player; no client controls what area the server scans. */
    public static boolean telemetryDue(long time, java.util.UUID playerId, boolean equipped, boolean receiverAvailable) {
        return equipped && receiverAvailable && Math.floorMod(time + playerId.hashCode(), 40) == 0;
    }
    public static AtmosphereReading reading(ServerWorld world, BlockPos pos) {
        AtmosphereState state = AtmosphereState.of(world);
        return AtmosphereReading.create(world.getRegistryKey().getValue().toString(), pos,
            state.levels(world, pos), state.baseline(world, pos));
    }
    private static void tick(ServerWorld world) {
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.getServerWorld() != world || Math.floorMod(world.getTime() + player.getUuid().hashCode(), 40) != 0) continue;
            BlockPos playerPos = player.getBlockPos();
            BlockPos origin = AtmosphereState.cellOrigin(playerPos);
            if (playerPos.getY() < world.getBottomY() || playerPos.getY() >= world.getTopY()
                    || origin.getY() < -2048 || origin.getY() > 2047 || !world.isChunkLoaded(playerPos)) continue;
            boolean equipped = dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player)
                .map(component -> component.isEquipped(net.alkahest.registry.ModItems.ALCHEMISTS_MONOCLE)).orElse(false);
            if (telemetryDue(world.getTime(), player.getUuid(), equipped,
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, net.alkahest.network.AtmospherePayload.ID))) {
                try {
                    AtmosphereReading sample = reading(world, playerPos);
                    // Do not label a temporary neutral fallback as a measured baseline.
                    if (AtmosphereState.of(world).hasBaseline(playerPos))
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                            new net.alkahest.network.AtmospherePayload(sample.encode()));
                } catch (IllegalArgumentException ignored) {
                    // Nonstandard dimensions/coordinates cannot be represented by this bounded HUD.
                }
            }
        }
        if (world.getDifficulty() == Difficulty.PEACEFUL
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return;
        Set<Long> visited = new HashSet<>();
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.getServerWorld() != world || player.isSpectator()) continue;
            BlockPos origin = AtmosphereState.cellOrigin(player.getBlockPos());
            // Stagger cells over the minute instead of doing every player's scan in one tick.
            if (Math.floorMod(world.getTime() + Long.hashCode(origin.asLong()), 1200) == 0
                && visited.add(origin.asLong())) attempt(world, origin);
        }
    }
    /** Returns true only on a successful spawn; public for deterministic server tests. */
    public static boolean spawnAt(ServerWorld world, BlockPos origin, BlockPos target, EssenceType type) {
        return spawnAt(world, origin, target, type, world.getServer().getPlayerManager().getPlayerList());
    }
    // Injectable only within the package: the vanilla GameTest mock is not a connected player.
    static boolean spawnAt(ServerWorld world, BlockPos origin, BlockPos target, EssenceType type,
                           java.util.List<ServerPlayerEntity> players) {
        if (world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)
                || !world.isChunkLoaded(target) || !AtmosphereState.cellOrigin(target).equals(AtmosphereState.cellOrigin(origin))) return false;
        var entityType = ModEntities.ELEMENTALS.get(type);
        if (entityType == null || target.getY() < world.getBottomY() || target.getY() >= world.getTopY() - 2) return false;
        if (!world.getEntitiesByClass(ElementalEntity.class, new Box(target).expand(24), e -> e.isAlive()).isEmpty()) return false;
        if (players.stream().noneMatch(p -> p.getServerWorld() == world && !p.isSpectator()
                && p.squaredDistanceTo(target.getX()+.5, target.getY(), target.getZ()+.5) <= 24*24)) return false;
        if (players.stream().anyMatch(p -> p.getServerWorld() == world && !p.isSpectator()
                && p.squaredDistanceTo(target.getX()+.5, target.getY(), target.getZ()+.5) < 8*8)) return false;
        if (!ElementalEntity.canSpawn(entityType, world, SpawnReason.EVENT, target, world.random)) return false;
        ElementalEntity entity = entityType.spawn(world, target, SpawnReason.EVENT);
        if (entity == null) return false;
        AtmosphereState.of(world).spend(world, origin, type, 25);
        return true;
    }
    private static void attempt(ServerWorld world, BlockPos origin) {
        if (!world.isChunkLoaded(origin)) return;
        Map<EssenceType, Double> levels = AtmosphereState.of(world).levels(world, origin);
        if (world.random.nextDouble() >= AtmosphereMath.spawnChance(levels)) return;
        double total = 0;
        for (EssenceType t : AtmosphereMath.PRIMALS) total += Math.max(0, levels.getOrDefault(t, 0.0) - AtmosphereMath.SPAWN_THRESHOLD);
        if (total <= 0) return;
        double draw = world.random.nextDouble() * total; EssenceType selected = null;
        for (EssenceType t : AtmosphereMath.PRIMALS) {
            draw -= Math.max(0, levels.getOrDefault(t, 0.0) - AtmosphereMath.SPAWN_THRESHOLD);
            if (draw < 0) { selected = t; break; }
        }
        if (selected == null) return;
        for (int i = 0; i < 8; i++) {
            BlockPos candidate = origin.add(world.random.nextInt(16), world.random.nextInt(16), world.random.nextInt(16));
            if (spawnAt(world, origin, candidate, selected)) return;
        }
    }
    private AtmosphereEvents() { }
}
