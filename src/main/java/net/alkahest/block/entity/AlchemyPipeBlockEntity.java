package net.alkahest.block.entity;

import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Self-routing essence line. Connected pipes form a network that looks for any container
 * holding essence and any container with room, then moves the liquid on its own. Optional
 * per-segment filters constrain routes; unconfigured segments accept every ordinary essence.
 * Essence is never buffered inside the pipes.
 */
public class AlchemyPipeBlockEntity extends BlockEntity {
    /** How often a network looks for work. */
    public static final int PULSE_TICKS = 2;
    /** How many units one pulse may move; jars hold 100, a bellow holds 64. */
    public static final int UNITS_PER_PULSE = 4;
    private static final int ACTIVE_TICKS = 6;
    private static final int MAX_SEGMENTS = 64;

    /** Tick-local coordinates only; weak keys avoid retaining stopped/unloaded worlds. */
    private static final Map<World, TickNetworks> TICK_NETWORKS = new WeakHashMap<>();
    private static final class TickNetworks {
        private long tick = Long.MIN_VALUE;
        private final Set<BlockPos> visited = new HashSet<>();
    }

    private EssenceType flowing;
    private EssenceType filter;
    private int activeTicks;

    public AlchemyPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PIPE, pos, state);
    }

    /** Essence carried by the last pulse, used for the tint of the animated core. */
    public EssenceType flowing() { return flowing; }

    /** Null means unrestricted; Vacuum is never carried by ordinary pipes. */
    public EssenceType filter() { return filter; }

    public void setFilter(EssenceType value) {
        if (value == EssenceType.VACUUM) throw new IllegalArgumentException("Ordinary pipes cannot filter Vacuum");
        if (filter == value) return;
        filter = value;
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = getCachedState();
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    /** Sneak-use a wand to cycle the filter, without spending charges. */
    public void cycleFilter(PlayerEntity player) {
        EssenceType[] values = EssenceType.values();
        int next = filter == null ? 0 : filter.ordinal() + 1;
        setFilter(next >= values.length || values[next] == EssenceType.VACUUM ? null : values[next]);
        player.sendMessage(Text.translatable("message.alkahest.pipe_filter_changed", filterText()), true);
    }

    public void selectFilter(EssenceType value, PlayerEntity player) {
        setFilter(filter == value ? null : value);
        player.sendMessage(Text.translatable("message.alkahest.pipe_filter_changed", filterText()), true);
    }

    public Text filterText() {
        return filter == null ? Text.translatable("message.alkahest.pipe_filter_any")
            : Text.literal(filter.displayName()).styled(style -> style.withColor(filter.color()));
    }

    /** True while the pipe is visibly working. */
    public boolean active() { return activeTicks > 0; }

    public void describe(PlayerEntity player) {
        Network network = world == null ? null : Network.scan(world, pos);
        int pipes = network == null ? 1 : network.pipes.size();
        int containers = network == null ? 0 : network.ports.size();
        player.sendMessage(Text.translatable("message.alkahest.pipe_status", pipes, containers), false);
        player.sendMessage(Text.translatable("message.alkahest.pipe_filter", filterText()), false);
        if (active() && flowing != null) {
            player.sendMessage(Text.translatable("message.alkahest.pipe_flowing",
                Text.literal(flowing.displayName()).styled(style -> style.withColor(flowing.color()))), false);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (flowing != null) nbt.putString("Flowing", flowing.asString());
        if (filter != null) nbt.putString("Filter", filter.asString());
        nbt.putInt("ActiveTicks", activeTicks);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        flowing = null;
        filter = null;
        for (EssenceType type : EssenceType.values()) {
            if (type != EssenceType.VACUUM && type.asString().equals(nbt.getString("Flowing"))) flowing = type;
            if (type != EssenceType.VACUUM && type.asString().equals(nbt.getString("Filter"))) filter = type;
        }
        activeTicks = Math.max(0, Math.min(ACTIVE_TICKS, nbt.getInt("ActiveTicks")));
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    public static void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        if (world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe) pipe.age(world, state);
        if (world.getTime() % PULSE_TICKS != 0) return;
        pulseOncePerTick(world, pos);
    }

    /** Scan a loaded physical network once per world tick, even though every segment ticks.
     * If two already-ticked networks are joined mid-tick, defer the joined network to the
     * next pulse instead of allowing either side a second transfer in the same tick.
     */
    public static int pulseOncePerTick(World world, BlockPos pos) {
        if (world.isClient) return 0;
        TickNetworks tick = TICK_NETWORKS.computeIfAbsent(world, ignored -> new TickNetworks());
        if (tick.tick != world.getTime()) {
            tick.tick = world.getTime();
            tick.visited.clear();
        }
        if (tick.visited.contains(pos)) return 0;
        Network network = Network.scan(world, pos);
        if (network == null) {
            tick.visited.add(pos.toImmutable());
            return 0;
        }
        boolean alreadyPulsed = network.pipes.stream().anyMatch(tick.visited::contains);
        tick.visited.addAll(network.pipes);
        return alreadyPulsed ? 0 : network.pulse(world);
    }

    /** Runs one transfer step for the network this pipe belongs to; only its leader acts. */
    public static int pulse(World world, BlockPos pos) {
        Network network = Network.scan(world, pos);
        if (network == null || !network.leader().equals(pos)) return 0;
        return network.pulse(world);
    }

    private void age(World world, BlockState state) {
        if (activeTicks <= 0) return;
        activeTicks--;
        if (activeTicks > 0) return;
        if (state.get(AlchemyPipeBlock.ACTIVE)) {
            world.setBlockState(pos, state.with(AlchemyPipeBlock.ACTIVE, false), Block.NOTIFY_ALL);
        }
        markDirty();
    }

    private void energize(World world, EssenceType type) {
        activeTicks = ACTIVE_TICKS;
        boolean retinted = flowing != type;
        flowing = type;
        markDirty();
        BlockState state = world.getBlockState(pos);
        if (!state.get(AlchemyPipeBlock.ACTIVE)) {
            world.setBlockState(pos, state.with(AlchemyPipeBlock.ACTIVE, true), Block.NOTIFY_ALL);
        }
        if (retinted) world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
    }

    /** Jars and distillation bellows both act as pipe endpoints, but with fixed roles. */
    private static boolean isPort(BlockEntity be) {
        return be instanceof JarBlockEntity || be instanceof DistilleryBellowsBlockEntity
            || be instanceof TransmutatorCoreBlockEntity || be instanceof StabilizerReceiverBlockEntity;
    }

    /** Bellows are the source of a line: they give what the cube distilled into them. */
    private static boolean canGive(BlockEntity be) {
        return be instanceof DistilleryBellowsBlockEntity;
    }

    /** Storage jars receive only; a receiver is a one-way destination, never a pipe source. */
    private static boolean canReceive(BlockEntity be) {
        return be instanceof JarBlockEntity;
    }

    private static EssenceType typeOf(BlockEntity be) {
        JarContentsComponent contents = portContents(be);
        return contents.total() == 0 ? null : contents.contents().getFirst().type();
    }

    /** Bellows are a single-type container, so they are read as a one-entry jar. */
    private static JarContentsComponent portContents(BlockEntity be) {
        if (be instanceof JarBlockEntity jar) return jar.contents();
        if (be instanceof DistilleryBellowsBlockEntity bellows) {
            return bellows.empty() ? JarContentsComponent.EMPTY
                : new JarContentsComponent(List.of(new EssenceStack(bellows.storedType(), bellows.amount(), bellows.stability())));
        }
        return JarContentsComponent.EMPTY;
    }

    /** Room left for {@code type}; zero blocks the transfer. */
    private static int portFree(BlockEntity be, EssenceType type) {
        if (be instanceof StabilizerReceiverBlockEntity receiver) return receiver.pipeFree(type);
        if (be instanceof JarBlockEntity jar) {
            JarContentsComponent contents = jar.contents();
            if (!JarItem.allowed(contents) || !JarItem.singleType(contents)) return 0;
            if (contents.total() > 0 && contents.contents().getFirst().type() != type) return 0;
            return JarItem.CAPACITY - contents.total();
        }
        if (be instanceof DistilleryBellowsBlockEntity bellows) {
            if (bellows.empty()) return DistilleryBellowsBlockEntity.CAPACITY;
            if (bellows.storedType() != type) return 0;
            return DistilleryBellowsBlockEntity.CAPACITY - bellows.amount();
        }
        return 0;
    }

    private static void portAdd(BlockEntity be, EssenceStack addition) {
        if (be instanceof StabilizerReceiverBlockEntity receiver) {
            receiver.acceptFromPipe(addition);
        } else if (be instanceof JarBlockEntity jar) {
            jar.setContents(jar.contents().merge(new JarContentsComponent(List.of(addition)), JarItem.CAPACITY));
        } else if (be instanceof DistilleryBellowsBlockEntity bellows) {
            bellows.accept(addition.type(), addition.amount(), addition.stability());
        }
    }

    private static int portRemove(BlockEntity be, EssenceType type, int count) {
        if (count <= 0) return 0;
        if (be instanceof DistilleryBellowsBlockEntity bellows) return bellows.drain(count);
        if (!(be instanceof JarBlockEntity jar)) return 0;
        JarContentsComponent contents = jar.contents();
        EssenceStack item = contents.contents().stream()
            .filter(entry -> entry.type() == type).findFirst().orElse(null);
        if (item == null) return 0;
        int removed = Math.min(count, item.amount());
        List<EssenceStack> remainder = new ArrayList<>(contents.contents());
        remainder.remove(item);
        if (item.amount() > removed)
            remainder.add(new EssenceStack(item.type(), item.amount() - removed, item.stability()));
        // One update per jar, not one dirty/network notification per essence unit.
        jar.setContents(new JarContentsComponent(remainder));
        return removed;
    }

    private static int move(BlockEntity source, BlockEntity target, EssenceType type, int budget) {
        EssenceStack entry = portContents(source).contents().stream()
            .filter(candidate -> candidate.type() == type).findFirst().orElse(null);
        if (entry == null) return 0;
        int room = Math.min(Math.min(portFree(target, type), entry.amount()), budget);
        if (room <= 0) return 0;
        int removed = portRemove(source, type, room);
        if (removed > 0) portAdd(target, new EssenceStack(type, removed, entry.stability()));
        return removed;
    }

    private static Vector3f colorOf(EssenceType type) {
        if (type == null) return new Vector3f(0.85f, 0.85f, 0.85f);
        int color = type.color();
        return new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f);
    }

    /** One connected group of pipes together with the containers that touch it. */
    private static final class Network {
        private final List<BlockPos> pipes;
        private final List<BlockEntity> ports;
        private final Set<BlockPos> pipeSet;
        // Per-pulse only: no world, topology or filter state is cached across ticks.
        private final Map<RouteKey, PipeRouteTree> routes = new HashMap<>();

        private record RouteKey(BlockPos source, EssenceType type) { }

        private Network(List<BlockPos> pipes, List<BlockEntity> ports) {
            this.pipes = pipes;
            this.ports = ports;
            this.pipeSet = Set.copyOf(pipes);
        }

        static Network scan(World world, BlockPos start) {
            if (!world.isChunkLoaded(start)) return null;
            Set<BlockPos> seen = new HashSet<>();
            Set<BlockPos> expanded = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> pipes = new ArrayList<>();
            List<BlockEntity> ports = new ArrayList<>();
            seen.add(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (!expanded.add(pos)) continue;
                if (!world.isChunkLoaded(pos)) continue;
                if (!(world.getBlockState(pos).getBlock() instanceof AlchemyPipeBlock)) continue;
                pipes.add(pos);
                if (pipes.size() > MAX_SEGMENTS) return null;
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.offset(direction);
                    if (!world.isChunkLoaded(next)) continue;
                    if (world.getBlockState(next).getBlock() instanceof AlchemyPipeBlock) {
                        if (seen.add(next)) queue.add(next);
                        continue;
                    }
                    BlockEntity port = world.getBlockEntity(next);
                    if (isPort(port) && !ports.contains(port)) ports.add(port);
                }
            }
            if (pipes.isEmpty()) return null; // A removed starting segment has no network or leader.
            pipes.sort(null);
            ports.sort((a, b) -> a.getPos().compareTo(b.getPos()));
            return new Network(List.copyOf(pipes), List.copyOf(ports));
        }

        BlockPos leader() { return pipes.getFirst(); }

        /** A shortest allowed path, including both endpoint-adjacent pipes, or none.
         * Search only the loaded network snapshot: never load chunks to perform transfers.
         * An endpoint may touch several segments, so all eligible entry points are searched.
         */
        private List<BlockPos> route(World world, BlockEntity from, BlockEntity to, EssenceType type) {
            if (type == EssenceType.VACUUM) return List.of();
            RouteKey key = new RouteKey(from.getPos(), type);
            PipeRouteTree tree = routes.computeIfAbsent(key,
                ignored -> PipeRouteTree.build(key.source(), pos -> allows(world, pos, key.type())));
            return tree.routeTo(to.getPos());
        }

        private boolean allows(World world, BlockPos pos, EssenceType type) {
            if (!pipeSet.contains(pos) || !world.isChunkLoaded(pos)) return false;
            if (!(world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe)) return false;
            return pipe.filter == null || pipe.filter == type;
        }

        /** Receivers win, then jars already holding the type, then empty jars. */
        private BlockEntity pickTarget(World world, BlockEntity source, EssenceType type, Map<BlockEntity, EssenceType> claimed) {
            for (int priority = 0; priority < 3; priority++) {
                for (BlockEntity port : ports) {
                    if (port == source || portFree(port, type) <= 0) continue;
                    if (priority == 0 && !(port instanceof StabilizerReceiverBlockEntity)) continue;
                    if (priority == 1 && (!canReceive(port) || typeOf(port) != type)) continue;
                    if (priority == 2 && (!canReceive(port) || typeOf(port) != null
                        || (claimed.containsKey(port) && claimed.get(port) != type))) continue;
                    if (!route(world, source, port, type).isEmpty()) return port;
                }
            }
            return null;
        }

        /**
         * Pay an awakened compact transmutator directly from connected jars. Jars may supply
         * this consumer only: never another jar or a bellow. First check the ENTIRE cost, then
         * transfer the exact amounts and complete in the same server tick. No intermediate
         * pipe or machine buffer exists, so a missing type leaves all jars untouched.
         */
        private int payTransmutator(World world, TransmutatorCoreBlockEntity core, Map<BlockPos, EssenceType> used) {
            Map<EssenceType, Integer> cost = core.pipeCost();
            if (cost.isEmpty()) return 0;
            for (var entry : cost.entrySet()) {
                int available = 0;
                for (BlockEntity port : ports) {
                    if (!(port instanceof JarBlockEntity jar)) continue;
                    JarContentsComponent contents = jar.contents();
                    if (!JarItem.allowed(contents) || !JarItem.singleType(contents) || contents.total() == 0) continue;
                    if (contents.contents().getFirst().type() == entry.getKey()
                        && !route(world, jar, core, entry.getKey()).isEmpty()) available += contents.total();
                }
                if (available < entry.getValue()) return 0;
            }
            int paid = 0;
            for (var entry : cost.entrySet()) {
                int remaining = entry.getValue();
                for (BlockEntity port : ports) {
                    if (!(port instanceof JarBlockEntity jar) || remaining <= 0) continue;
                    JarContentsComponent contents = jar.contents();
                    if (!JarItem.allowed(contents) || !JarItem.singleType(contents) || contents.total() == 0
                        || contents.contents().getFirst().type() != entry.getKey()) continue;
                    List<BlockPos> path = route(world, jar, core, entry.getKey());
                    if (path.isEmpty()) continue;
                    int removed = portRemove(jar, entry.getKey(), Math.min(remaining, contents.total()));
                    remaining -= removed;
                    paid += removed;
                    if (removed > 0) for (BlockPos segment : path) used.put(segment, entry.getKey());
                }
            }
            core.finishPipedOperation();
            return paid;
        }

        /**
         * Moves up to {@link #UNITS_PER_PULSE} units from every source into receivers or jars.
         * Bellows only give, receivers and jars only receive, and each jar takes a single essence type, so
         * several flasks feed several jars with different essences instead of shuffling one
         * unit back and forth.
         */
        int pulse(World world) {
            int moved = 0;
            Map<BlockEntity, EssenceType> claimed = new HashMap<>();
            Map<BlockPos, EssenceType> used = new HashMap<>();
            for (BlockEntity source : ports) {
                if (!canGive(source)) continue;
                JarContentsComponent contents = portContents(source);
                if (contents.total() == 0 || !JarItem.allowed(contents)) continue;
                int budget = UNITS_PER_PULSE;
                for (EssenceStack entry : contents.contents()) {
                    if (budget <= 0) break;
                    if (entry.type() == EssenceType.VACUUM) continue;
                    BlockEntity target = pickTarget(world, source, entry.type(), claimed);
                    if (target == null) continue;
                    int sent = move(source, target, entry.type(), budget);
                    if (sent <= 0) continue;
                    for (BlockPos segment : route(world, source, target, entry.type())) used.put(segment, entry.type());
                    claimed.put(target, entry.type());
                    budget -= sent;
                    moved += sent;
                }
            }
            // Distillation still fills jars first. Only a ready Transmutator may draw from
            // jars; idle machines and jars never trigger transfers between storage containers.
            for (BlockEntity port : ports) {
                if (!(port instanceof TransmutatorCoreBlockEntity core)) continue;
                int paid = payTransmutator(world, core, used);
                if (paid > 0) {
                    moved += paid;
                }
            }
            if (moved == 0) return 0;
            for (var entry : used.entrySet()) {
                BlockPos pos = entry.getKey();
                EssenceType type = entry.getValue();
                if (world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe) pipe.energize(world, type);
                if (world instanceof ServerWorld server && world.random.nextInt(3) == 0) {
                    DustParticleEffect dust = new DustParticleEffect(colorOf(type), 0.55f);
                    server.spawnParticles(dust, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, 0.18, 0.18, 0.18, 0.01);
                }
            }
            return moved;
        }
    }
}
