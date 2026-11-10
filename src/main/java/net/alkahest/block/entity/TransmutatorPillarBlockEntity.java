package net.alkahest.block.entity;

import net.alkahest.block.custom.TransmutatorPillarBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One pillar of the Transmutator platform. It holds a small essence buffer and, when the core
 * asks for an essence, reaches out to the jars standing nearby: the units travel to the pillar
 * as a stream of coloured particles, without any pipes.
 */
public class TransmutatorPillarBlockEntity extends BlockEntity {
    public static final int CAPACITY = 16;
    private static final int REACH = 6;
    /** Coloured sparks flown along the path for every transfer. */
    private static final int TRAIL = 14;

    private JarContentsComponent buffer = JarContentsComponent.EMPTY;

    public TransmutatorPillarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSMUTATOR_PILLAR, pos, state);
    }

    public JarContentsComponent buffer() { return buffer; }

    public int stored(EssenceType type) {
        for (EssenceStack stack : buffer.contents()) if (stack.type() == type) return stack.amount();
        return 0;
    }

    /**
     * Makes sure the pillar holds at least {@code count} units of the type. Missing units are
     * pulled out of a nearby jar with a visible trail. Returns true when the pillar can supply it.
     */
    public boolean request(World world, EssenceType type, int count) {
        if (stored(type) >= count) return true;
        if (buffer.total() >= CAPACITY) return stored(type) > 0;
        JarBlockEntity jar = findJar(world, type);
        if (jar == null) return stored(type) > 0;
        int room = CAPACITY - buffer.total();
        int available = jar.contents().contents().getFirst().amount();
        int want = Math.min(Math.min(count - stored(type), room), available);
        if (want <= 0) return stored(type) > 0;
        EssenceStack source = jar.contents().contents().getFirst();
        List<EssenceStack> remainder = new ArrayList<>(jar.contents().contents());
        remainder.remove(source);
        if (source.amount() > want) remainder.add(new EssenceStack(source.type(), source.amount() - want, source.stability()));
        jar.setContents(new JarContentsComponent(remainder));
        buffer = buffer.merge(new JarContentsComponent(List.of(new EssenceStack(type, want, source.stability()))), CAPACITY);
        flyTrail(world, jar.getPos(), type);
        markDirty();
        updateLevel();
        return stored(type) > 0;
    }

    /** Right-click report: what this pillar currently holds. */
    public void describe(net.minecraft.entity.player.PlayerEntity player) {
        if (buffer.total() == 0) {
            player.sendMessage(Text.translatable("message.alkahest.pillar_empty", CAPACITY), true);
            return;
        }
        player.sendMessage(Text.translatable("message.alkahest.pillar_status", buffer.total(), CAPACITY), false);
        for (EssenceStack stack : buffer.contents()) {
            player.sendMessage(Text.literal("◆ " + stack.type().displayName() + ": " + stack.amount())
                .styled(style -> style.withColor(stack.type().color())), false);
        }
    }

    /** Removes up to {@code count} units for the core. */
    public int take(EssenceType type, int count) {
        int removed = 0;
        List<EssenceStack> remainder = new ArrayList<>();
        for (EssenceStack stack : buffer.contents()) {
            if (stack.type() != type || removed >= count) { remainder.add(stack); continue; }
            int taken = Math.min(count - removed, stack.amount());
            removed += taken;
            if (stack.amount() > taken) remainder.add(new EssenceStack(type, stack.amount() - taken, stack.stability()));
        }
        if (removed == 0) return 0;
        buffer = new JarContentsComponent(remainder);
        markDirty();
        updateLevel();
        return removed;
    }

    /** Every jar in reach that holds this single type; the core uses it to price an operation. */
    public List<JarBlockEntity> jarsAround(World world, EssenceType type) {
        List<JarBlockEntity> jars = new ArrayList<>();
        for (BlockPos candidate : BlockPos.iterate(pos.add(-REACH, -2, -REACH), pos.add(REACH, 2, REACH))) {
            if (!(world.getBlockEntity(candidate) instanceof JarBlockEntity jar)) continue;
            JarContentsComponent contents = jar.contents();
            if (contents.total() == 0 || contents.contents().size() != 1) continue;
            if (contents.contents().getFirst().type() != type) continue;
            jars.add(jar);
        }
        return jars;
    }

    private JarBlockEntity findJar(World world, EssenceType type) {
        JarBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (JarBlockEntity jar : jarsAround(world, type)) {
            double distance = jar.getPos().getSquaredDistance(pos);
            if (distance < bestDistance) { bestDistance = distance; best = jar; }
        }
        return best;
    }

    /** The Thaumcraft-like part: essence visibly flies through the air from the jar to the pillar. */
    private void flyTrail(World world, BlockPos from, EssenceType type) {
        if (!(world instanceof ServerWorld server)) return;
        int color = type.color();
        DustParticleEffect dust = new DustParticleEffect(
            new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), 0.75f);
        double x0 = from.getX() + 0.5, y0 = from.getY() + 1.05, z0 = from.getZ() + 0.5;
        double x1 = pos.getX() + 0.5, y1 = pos.getY() + 1.2, z1 = pos.getZ() + 0.5;
        for (int i = 0; i < TRAIL; i++) {
            double t = i / (double) TRAIL;
            double arc = Math.sin(t * Math.PI) * 0.45;
            double x = x0 + (x1 - x0) * t;
            double y = y0 + (y1 - y0) * t + arc;
            double z = z0 + (z1 - z0) * t;
            server.spawnParticles(dust, x, y, z, 0, (x1 - x) * 0.35, (y1 - y) * 0.35 + 0.02, (z1 - z) * 0.35, 1.0);
        }
    }

    private void updateLevel() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof TransmutatorPillarBlock)) return;
        boolean charged = buffer.total() > 0;
        if (state.get(TransmutatorPillarBlock.CHARGED) != charged) {
            world.setBlockState(pos, state.with(TransmutatorPillarBlock.CHARGED, charged), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.4f, 1.6f);
        } else {
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.put("Buffer", JarContentsComponent.CODEC.encodeStart(NbtOps.INSTANCE, buffer)
            .result().orElseGet(NbtCompound::new));
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        buffer = JarContentsComponent.CODEC.parse(NbtOps.INSTANCE, nbt.get("Buffer"))
            .result().orElse(JarContentsComponent.EMPTY);
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
