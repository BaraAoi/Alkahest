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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Self-routing essence line. Connected pipes form a network that looks for any container
 * holding essence and any container with room, then moves the liquid on its own: there are
 * no directions and no filters to configure. Essence is never buffered inside the pipes.
 */
public class AlchemyPipeBlockEntity extends BlockEntity {
    /** How often a network looks for work. */
    public static final int PULSE_TICKS = 2;
    /** How many units one pulse may move; jars hold 100, a bellow holds 64. */
    public static final int UNITS_PER_PULSE = 4;
    private static final int ACTIVE_TICKS = 6;
    private static final int MAX_SEGMENTS = 64;

    private EssenceType flowing;
    private int activeTicks;

    public AlchemyPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PIPE, pos, state);
    }

    /** Essence carried by the last pulse, used for the tint of the animated core. */
    public EssenceType flowing() { return flowing; }

    /** True while the pipe is visibly working. */
    public boolean active() { return activeTicks > 0; }

    public void describe(PlayerEntity player) {
        Network network = world == null ? null : Network.scan(world, pos);
        int pipes = network == null ? 1 : network.pipes.size();
        int containers = network == null ? 0 : network.ports.size();
        player.sendMessage(Text.translatable("message.alkahest.pipe_status", pipes, containers), false);
        if (flowing != null) {
            player.sendMessage(Text.translatable("message.alkahest.pipe_flowing",
                Text.literal(flowing.displayName()).styled(style -> style.withColor(flowing.color()))), false);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (flowing != null) nbt.putString("Flowing", flowing.asString());
        nbt.putInt("ActiveTicks", activeTicks);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        flowing = null;
        for (EssenceType type : EssenceType.values()) {
            if (type != EssenceType.VACUUM && type.asString().equals(nbt.getString("Flowing"))) flowing = type;
        }
        activeTicks = Math.max(0, Math.min(ACTIVE_TICKS, nbt.getInt("ActiveTicks")));
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    public static void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        if (world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe) pipe.age(world, state);
        if (world.getTime() % PULSE_TICKS != 0) return;
        pulse(world, pos);
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

    /** Jars and distillation bellows both act as pipe endpoints. */
    private static boolean isPort(BlockEntity be) {
        return be instanceof JarBlockEntity || be instanceof DistilleryBellowsBlockEntity;
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
        if (be instanceof JarBlockEntity jar) {
            jar.setContents(jar.contents().merge(new JarContentsComponent(List.of(addition)), JarItem.CAPACITY));
        } else if (be instanceof DistilleryBellowsBlockEntity bellows) {
            bellows.accept(addition.type(), addition.amount(), addition.stability());
        }
    }

    private static int portRemove(BlockEntity be, EssenceType type, int count) {
        if (be instanceof DistilleryBellowsBlockEntity bellows) return bellows.drain(count);
        if (!(be instanceof JarBlockEntity jar)) return 0;
        int removed = 0;
        while (removed < count) {
            JarContentsComponent contents = jar.contents();
            EssenceStack item = contents.contents().stream()
                .filter(entry -> entry.type() == type).findFirst().orElse(null);
            if (item == null) break;
            List<EssenceStack> remainder = new ArrayList<>(contents.contents());
            remainder.remove(item);
            if (item.amount() > 1) remainder.add(new EssenceStack(item.type(), item.amount() - 1, item.stability()));
            jar.setContents(new JarContentsComponent(remainder));
            removed++;
        }
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

        private Network(List<BlockPos> pipes, List<BlockEntity> ports) {
            this.pipes = pipes;
            this.ports = ports;
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
            pipes.sort(null);
            return new Network(List.copyOf(pipes), List.copyOf(ports));
        }

        BlockPos leader() { return pipes.getFirst(); }

        /** Moves up to {@link #UNITS_PER_PULSE} units from every container with essence. */
        int pulse(World world) {
            int moved = 0;
            EssenceType last = null;
            Set<BlockEntity> received = new HashSet<>();
            for (BlockEntity source : ports) {
                // Never let essence bounce back into a container that just received it.
                if (received.contains(source)) continue;
                JarContentsComponent contents = portContents(source);
                if (contents.total() == 0 || !JarItem.allowed(contents)) continue;
                for (EssenceStack entry : contents.contents()) {
                    if (entry.type() == EssenceType.VACUUM) continue;
                    int budget = UNITS_PER_PULSE;
                    for (BlockEntity target : ports) {
                        if (budget <= 0) break;
                        if (target == source) continue;
                        int sent = move(source, target, entry.type(), budget);
                        if (sent <= 0) continue;
                        budget -= sent;
                        moved += sent;
                        last = entry.type();
                        received.add(target);
                    }
                }
            }
            if (moved == 0) return 0;
            for (BlockPos pos : pipes) {
                if (world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe) pipe.energize(world, last);
            }
            if (world instanceof ServerWorld server && last != null) {
                DustParticleEffect dust = new DustParticleEffect(colorOf(last), 0.55f);
                for (BlockPos pos : pipes) {
                    if (world.random.nextInt(3) != 0) continue;
                    server.spawnParticles(dust, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, 0.18, 0.18, 0.18, 0.01);
                }
            }
            return moved;
        }
    }
}
