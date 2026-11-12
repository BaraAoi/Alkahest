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
                        AtmosphereMath.formatLevel(levels.get(type)),
                        AtmosphereMath.formatLevel(base.get(type)))
                        .styled(style -> style.withColor(type.color())), false);
                }
                return 1;
            })));
    }
    public static void emit(World world, BlockPos pos, EssenceType type, double amount) {
        if (world instanceof ServerWorld server) AtmosphereState.of(server).add(server, pos, type, amount);
    }
    public static void emit(World world, BlockPos pos, Map<EssenceType, ? extends Number> amounts, double factor) {
        if (world instanceof ServerWorld server) AtmosphereState.of(server).add(server, pos, amounts, factor);
    }
    /** Current cell is refreshed once a second; neighbour prefetch is independently staggered. */
    public static boolean telemetryDue(long time, java.util.UUID playerId, boolean equipped, boolean receiverAvailable) {
        return equipped && receiverAvailable && Math.floorMod(time + playerId.hashCode(), 20) == 0;
    }
    public static int prefetchIndex(long time, java.util.UUID playerId) {
        long phase = time + playerId.hashCode();
        return Math.floorMod(phase, 2) == 0 ? (int) Math.floorMod(Math.floorDiv(phase, 2), 26) : -1;
    }
    /** Includes diagonals and vertical neighbours so corners do not cause a gap. */
    public static BlockPos prefetchPosition(BlockPos position, int index) {
        if (index < 0 || index >= 26) throw new IllegalArgumentException("Invalid prefetch index");
        int n = index < 13 ? index : index + 1; // skip the centre of the 3x3x3 cube
        return AtmosphereState.cellOrigin(position).add((n / 9 - 1) * 16, (n / 3 % 3 - 1) * 16, (n % 3 - 1) * 16);
    }
    public static AtmosphereReading reading(ServerWorld world, BlockPos pos) {
        AtmosphereState state = AtmosphereState.of(world);
        return AtmosphereReading.create(world.getRegistryKey().getValue().toString(), pos,
            state.levels(world, pos), state.baseline(world, pos));
    }
    static boolean canSample(ServerWorld world, BlockPos pos) {
        BlockPos origin = AtmosphereState.cellOrigin(pos);
        return origin.getY() + 15 >= world.getBottomY() && origin.getY() < world.getTopY()
            && origin.getY() >= -2048 && origin.getY() <= 2047 && world.isChunkLoaded(origin);
    }
    private static void sendReading(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        BlockPos origin = AtmosphereState.cellOrigin(pos);
        if (!canSample(world, origin)) return;
        try {
            AtmosphereReading sample = reading(world, origin);
            // Do not label a temporary neutral fallback as a measured baseline.
            if (AtmosphereState.of(world).hasBaseline(origin))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new net.alkahest.network.AtmospherePayload(sample.encode()));
        } catch (IllegalArgumentException ignored) {
            // Nonstandard dimensions/coordinates cannot be represented by this bounded HUD.
        }
    }
    private static void tick(ServerWorld world) {
        AtmosphereState.of(world).tick(world);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.getServerWorld() != world) continue;
            long now = world.getTime();
            int neighbour = prefetchIndex(now, player.getUuid());
            boolean currentDue = Math.floorMod(now + player.getUuid().hashCode(), 20) == 0;
            if (!currentDue && neighbour < 0) continue;
            BlockPos playerPos = player.getBlockPos();
            if (playerPos.getY() < world.getBottomY() || playerPos.getY() >= world.getTopY()
                    || !world.isChunkLoaded(playerPos)) continue;
            boolean equipped = dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player)
                .map(component -> component.isEquipped(net.alkahest.registry.ModItems.ALCHEMISTS_MONOCLE)).orElse(false);
            boolean receiver = net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, net.alkahest.network.AtmospherePayload.ID);
            if (!equipped || !receiver) continue;
            if (telemetryDue(now, player.getUuid(), true, true)) sendReading(world, player, playerPos);
            if (neighbour >= 0) sendReading(world, player, prefetchPosition(playerPos, neighbour));
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
        AtmosphereState.of(world).spend(world, origin, type, 5);
        return true;
    }
    private static void attempt(ServerWorld world, BlockPos origin) {
        if (!world.isChunkLoaded(origin)) return;
        Map<EssenceType, Double> levels = AtmosphereState.of(world).levels(world, origin);
        if (world.random.nextDouble() >= AtmosphereMath.spawnChance(levels)) return;
        // Normalize weights first so even very large finite saved concentrations cannot overflow the sum.
        double largest = 0;
        for (EssenceType t : AtmosphereMath.PRIMALS) largest = Math.max(largest, Math.max(0, levels.getOrDefault(t, 0.0) - AtmosphereMath.SPAWN_THRESHOLD));
        if (largest <= 0) return;
        double total = 0;
        for (EssenceType t : AtmosphereMath.PRIMALS) total += Math.max(0, levels.getOrDefault(t, 0.0) - AtmosphereMath.SPAWN_THRESHOLD) / largest;
        double draw = world.random.nextDouble() * total; EssenceType selected = null;
        for (EssenceType t : AtmosphereMath.PRIMALS) {
            draw -= Math.max(0, levels.getOrDefault(t, 0.0) - AtmosphereMath.SPAWN_THRESHOLD) / largest;
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
