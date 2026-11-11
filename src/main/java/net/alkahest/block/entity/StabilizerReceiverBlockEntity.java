package net.alkahest.block.entity;

import net.alkahest.block.custom.StabilizerReceiverBlock;
import net.alkahest.effect.MidasTouchEffect;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Early, working Stabilizer slice. Essence is poured straight into the receiver as a liquid,
 * the player's jar stays in hand (emptied, or with the part that did not fit). The activated
 * tower then sorts the tank one essence type at a time into nearby jars at 90% stability
 * (100% for a freshly stabilised batch when Midas is present).
 * The tank is stored in the same NBT slot as 0.1.30, so old saves keep their contents.
 */
public class StabilizerReceiverBlockEntity extends BlockEntity {
    /** Spec: 200 ticks (10 seconds) per essence type in the receiver. */
    public static final int TICKS_PER_TYPE = 200;
    /** Search area for empty/matching jars; keeps the mechanism simple and predictable. */
    public static final int RADIUS = 4;
    public static final int CAPACITY = JarItem.CAPACITY;
    public static final float OUTPUT_STABILITY = 0.90f;
    private static final float LOSS_THRESHOLD = 0.20f;
    private static final float LOSS_CHANCE = 0.15f;

    /** Invisible container item: the single source of truth for the liquid, incl. NBT layout. */
    private final DefaultedList<ItemStack> storage = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private int progress;

    public StabilizerReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STABILIZER_RECEIVER, pos, state);
    }

    private ItemStack tank() {
        if (storage.get(0).isEmpty()) storage.set(0, new ItemStack(ModBlocks.JAR));
        return storage.get(0);
    }

    public JarContentsComponent contents() { return JarItem.contents(tank()); }
    public int progress() { return progress; }

    /** Used by tests and by the break handler; keeps the liquid exactly as it is. */
    public void setContents(JarContentsComponent contents) {
        JarItem.set(tank(), contents);
        progress = 0;
        changed();
    }

    public boolean structureFormed() {
        return world != null
            && world.getBlockState(pos.up()).isOf(ModBlocks.STABILIZER_CORE)
            && world.getBlockState(pos.up(2)).isOf(ModBlocks.STABILIZER_CREST);
    }

    public boolean active() {
        if (!structureFormed()) return false;
        return world.getBlockEntity(pos.up()) instanceof StabilizerCoreBlockEntity core && core.isActivated();
    }

    /** Pour the held jar into the receiver. The jar item itself stays in the player's hand. */
    public void pourJar(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof JarItem)) return;
        JarContentsComponent incoming = JarItem.contents(held);
        if (!JarItem.allowed(incoming) || incoming.total() == 0) {
            message(player, "message.alkahest.stabilizer_needs_essence");
            return;
        }
        JarContentsComponent current = contents();
        if (!JarItem.allowed(current)) {
            message(player, "message.alkahest.stabilizer_invalid");
            return;
        }
        int free = CAPACITY - current.total();
        if (free <= 0) {
            message(player, "message.alkahest.stabilizer_full");
            return;
        }
        List<EssenceStack> accepted = new ArrayList<>();
        List<EssenceStack> remainder = new ArrayList<>();
        int remaining = free;
        for (EssenceStack stack : incoming.contents()) {
            int take = Math.min(remaining, stack.amount());
            if (take > 0) accepted.add(new EssenceStack(stack.type(), take, stack.stability()));
            remaining -= take;
            if (stack.amount() - take > 0)
                remainder.add(new EssenceStack(stack.type(), stack.amount() - take, stack.stability()));
        }
        if (accepted.isEmpty()) { message(player, "message.alkahest.stabilizer_full"); return; }
        JarContentsComponent addition = new JarContentsComponent(accepted);
        setContents(current.merge(addition, CAPACITY));
        // The jar is not consumed: it stays in hand, now empty or holding what did not fit.
        JarItem.set(held, new JarContentsComponent(remainder));
        if (remainder.isEmpty()) message(player, "message.alkahest.stabilizer_poured", addition.total());
        else message(player, "message.alkahest.stabilizer_pour_full", addition.total(), new JarContentsComponent(remainder).total());
    }

    public void describe(PlayerEntity player) {
        JarContentsComponent contents = contents();
        player.sendMessage(Text.translatable("message.alkahest.stabilizer_status",
            Text.translatable(structureFormed() ? "message.alkahest.stabilizer_formed" : "message.alkahest.stabilizer_not_formed"),
            Text.translatable(active() ? "message.alkahest.stabilizer_running" : "message.alkahest.stabilizer_idle")), false);
        player.sendMessage(Text.translatable("message.alkahest.stabilizer_contents",
            contents.total(), CAPACITY, progress * 100 / TICKS_PER_TYPE), false);
        for (EssenceStack stack : contents.contents())
            player.sendMessage(Text.literal("◆ " + stack.type().displayName() + ": " + stack.amount()
                + " · " + Math.round(stack.stability() * 100) + "%").styled(style -> style.withColor(stack.type().color())), false);
    }

    /** Drops whatever is left in the receiver as a filled jar when the block is broken. */
    public void dropContents(World world, BlockPos pos) {
        JarContentsComponent contents = contents();
        storage.set(0, ItemStack.EMPTY);
        progress = 0;
        markDirty();
        if (contents.total() == 0) return;
        ItemStack jar = new ItemStack(ModBlocks.JAR);
        JarItem.set(jar, contents);
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, jar);
    }

    private EssenceType currentType() {
        for (EssenceStack stack : contents().contents()) {
            if (stack.type() != EssenceType.VACUUM && stack.amount() > 0) return stack.type();
        }
        return null;
    }

    /** Nearest jar that is empty or already holds this type and still has room. */
    private JarBlockEntity findTarget(EssenceType type) {
        if (world == null) return null;
        JarBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.iterate(pos.add(-RADIUS, -2, -RADIUS), pos.add(RADIUS, 4, RADIUS))) {
            if (candidate.equals(pos) || !world.isChunkLoaded(candidate)) continue;
            if (!(world.getBlockEntity(candidate) instanceof JarBlockEntity jar)) continue;
            JarContentsComponent contents = jar.contents();
            if (!JarItem.allowed(contents) || !JarItem.singleType(contents)) continue;
            if (contents.total() >= JarItem.CAPACITY) continue;
            if (contents.total() > 0 && contents.contents().getFirst().type() != type) continue;
            double distance = candidate.getSquaredDistance(pos);
            if (distance < bestDistance) { bestDistance = distance; best = jar; }
        }
        return best;
    }

    /** One full cycle: move as much of the current type as fits into its target jar. */
    public boolean extractCurrent() {
        return extractCurrent(world != null && MidasTouchEffect.nearby(world, pos.up()));
    }

    private boolean extractCurrent(boolean midas) {
        EssenceType type = currentType();
        if (type == null) return false;
        JarBlockEntity target = findTarget(type);
        if (target == null) return false;
        JarContentsComponent contents = contents();
        EssenceStack source = null;
        for (EssenceStack stack : contents.contents()) if (stack.type() == type) { source = stack; break; }
        if (source == null) return false;

        int free = JarItem.CAPACITY - target.contents().total();
        int moved = Math.min(free, source.amount());
        if (moved <= 0) return false;
        // Old unstable mixtures may bleed one unit while being sorted.
        int lost = source.stability() < LOSS_THRESHOLD && world.random.nextFloat() < LOSS_CHANCE ? 1 : 0;
        int delivered = Math.max(0, moved - lost);

        List<EssenceStack> remainder = new ArrayList<>();
        for (EssenceStack stack : contents.contents()) {
            if (stack.type() == type) {
                int rest = stack.amount() - moved;
                if (rest > 0) remainder.add(new EssenceStack(stack.type(), rest, stack.stability()));
            } else {
                remainder.add(stack);
            }
        }
        if (delivered > 0) {
            target.setContents(target.contents().merge(
                new JarContentsComponent(List.of(new EssenceStack(type, delivered, MidasTouchEffect.outputStability(OUTPUT_STABILITY, midas)))),
                JarItem.CAPACITY));
        }
        setContents(new JarContentsComponent(remainder));

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ENCHANT,
                target.getPos().getX() + 0.5, target.getPos().getY() + 0.9, target.getPos().getZ() + 0.5,
                10, 0.25, 0.25, 0.25, 0.02);
            world.playSound(null, target.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.6f, 1.4f);
        }
        return delivered > 0;
    }

    public static void tick(World world, BlockPos pos, StabilizerReceiverBlockEntity be) {
        if (world.isClient) return;
        // Distance is measured from the tower core, not the receiver below it.
        boolean midas = be.active() && be.contents().total() > 0 && MidasTouchEffect.nearby(world, pos.up());
        tickWithMidas(world, pos, be, midas, world.getTime());
    }

    /** Shared by the real ticker and deterministic server tests, including effect changes mid-cycle. */
    static void tickWithMidas(World world, BlockPos pos, StabilizerReceiverBlockEntity be, boolean midas, long time) {
        if (world.isClient) return;
        if (!be.active() || be.contents().total() == 0) {
            if (be.progress != 0) { be.progress = 0; be.markDirty(); }
            return;
        }
        int before = be.progress;
        be.progress += MidasTouchEffect.progressStep(midas, time);
        // A boosted step can skip a multiple of 20; do not skip its visual effect.
        if (before / 20 != be.progress / 20 && world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
        }
        if (be.progress < TICKS_PER_TYPE) { be.markDirty(); return; }
        be.progress = 0;
        be.extractCurrent(midas);
        be.markDirty();
    }

    private void message(PlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(key, args), true);
    }

    private void changed() {
        markDirty();
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof StabilizerReceiverBlock)) return;
        boolean occupied = contents().total() > 0;
        if (state.get(StabilizerReceiverBlock.OCCUPIED) != occupied)
            world.setBlockState(pos, state.with(StabilizerReceiverBlock.OCCUPIED, occupied), Block.NOTIFY_ALL);
        else world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, storage, registries);
        nbt.putInt("Progress", progress);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, storage, registries);
        progress = nbt.getInt("Progress");
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
