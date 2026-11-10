package net.alkahest.block.entity;

import net.alkahest.block.custom.StabilizerReceiverBlock;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Early, working Stabilizer slice: it takes one jar (legacy mixture or single type),
 * sorts its essences one type at a time into nearby jars with free room and rewrites
 * their stability to 90%. Pipes are not required for this first version.
 */
public class StabilizerReceiverBlockEntity extends BlockEntity {
    /** Spec: 200 ticks (10 seconds) per essence type in the input jar. */
    public static final int TICKS_PER_TYPE = 200;
    /** Search area for empty/matching jars; keeps the mechanism simple and predictable. */
    public static final int RADIUS = 4;
    public static final float OUTPUT_STABILITY = 0.90f;
    private static final float LOSS_THRESHOLD = 0.20f;
    private static final float LOSS_CHANCE = 0.15f;

    private final DefaultedList<ItemStack> slots = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private int progress;

    public StabilizerReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STABILIZER_RECEIVER, pos, state);
    }

    public DefaultedList<ItemStack> stacks() { return slots; }
    public int progress() { return progress; }
    public ItemStack stored() { return slots.get(0); }

    public void setStored(ItemStack stack) {
        slots.set(0, stack);
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

    public JarContentsComponent inputContents() {
        return slots.get(0).isEmpty() ? JarContentsComponent.EMPTY : JarItem.contents(slots.get(0));
    }

    public void insertJar(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof JarItem)) return;
        if (!slots.get(0).isEmpty()) { message(player, "message.alkahest.stabilizer_occupied"); return; }
        JarContentsComponent contents = JarItem.contents(held);
        if (!JarItem.allowed(contents) || contents.total() == 0) {
            message(player, "message.alkahest.stabilizer_needs_jar");
            return;
        }
        slots.set(0, held.copyWithCount(1));
        player.setStackInHand(hand, ItemStack.EMPTY);
        progress = 0;
        changed();
        message(player, "message.alkahest.stabilizer_inserted");
    }

    public void takeJar(PlayerEntity player) {
        if (slots.get(0).isEmpty()) { message(player, "message.alkahest.stabilizer_empty"); return; }
        ItemStack returned = slots.get(0);
        slots.set(0, ItemStack.EMPTY);
        progress = 0;
        if (!player.giveItemStack(returned)) player.dropItem(returned, false);
        changed();
        message(player, "message.alkahest.stabilizer_returned");
    }

    private EssenceType currentType() {
        for (EssenceStack stack : inputContents().contents()) {
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
        EssenceType type = currentType();
        if (type == null) return false;
        JarBlockEntity target = findTarget(type);
        if (target == null) return false;
        JarContentsComponent contents = inputContents();
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
                new JarContentsComponent(List.of(new EssenceStack(type, delivered, OUTPUT_STABILITY))),
                JarItem.CAPACITY));
        }
        JarItem.set(slots.get(0), new JarContentsComponent(remainder));
        markDirty();

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
        if (!be.active() || be.inputContents().total() == 0) {
            if (be.progress != 0) { be.progress = 0; be.markDirty(); }
            return;
        }
        be.progress++;
        if (be.progress % 20 == 0 && world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
        }
        if (be.progress < TICKS_PER_TYPE) return;
        be.progress = 0;
        be.extractCurrent();
        be.markDirty();
    }

    private void message(PlayerEntity player, String key) { player.sendMessage(Text.translatable(key), true); }

    private void changed() {
        markDirty();
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof StabilizerReceiverBlock)) return;
        boolean occupied = !slots.get(0).isEmpty();
        if (state.get(StabilizerReceiverBlock.OCCUPIED) != occupied)
            world.setBlockState(pos, state.with(StabilizerReceiverBlock.OCCUPIED, occupied), Block.NOTIFY_ALL);
        else {
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, slots, registries);
        nbt.putInt("Progress", progress);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, slots, registries);
        progress = nbt.getInt("Progress");
    }
}
