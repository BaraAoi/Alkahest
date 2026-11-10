package net.alkahest.block.entity;

import net.alkahest.block.custom.DistilleryBellowsBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.math.BlockPos;
import java.util.List;

/**
 * One bellow of the tier-2 distillery column. It stores liquid essence of a single type
 * (like a jar) and the player empties it directly into a held jar.
 */
public class DistilleryBellowsBlockEntity extends BlockEntity {
    public static final int CAPACITY = 64;

    private EssenceType type;
    private int amount;
    private float stability;

    public DistilleryBellowsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLERY_BELLOWS, pos, state);
    }

    public EssenceType storedType() { return type; }
    public int amount() { return amount; }
    public float stability() { return stability; }
    public boolean empty() { return type == null || amount <= 0; }

    /** Visible level 0..3, used by the block state so the bag grows while filling. */
    public int level() {
        if (amount <= 0) return 0;
        if (amount <= 16) return 1;
        if (amount <= 32) return 2;
        return 3;
    }

    /** Accepts only its own type (or the first type poured in); returns how much fit. */
    public int accept(EssenceType incoming, int count, float incomingStability) {
        if (incoming == null || incoming == EssenceType.VACUUM || count <= 0) return 0;
        if (type != null && type != incoming) return 0;
        int accepted = Math.min(count, CAPACITY - amount);
        if (accepted <= 0) return 0;
        stability = amount == 0 ? incomingStability
            : (stability * amount + incomingStability * accepted) / (amount + accepted);
        type = incoming;
        amount += accepted;
        changed();
        return accepted;
    }

    /** Removes up to {@code count} units; essence pipes drain the flask with it. */
    public int drain(int count) {
        int taken = Math.min(count, amount);
        if (taken <= 0) return 0;
        amount -= taken;
        if (amount <= 0) {
            type = null;
            amount = 0;
            stability = 0;
        }
        changed();
        return taken;
    }

    public void pourToJar(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof JarItem)) {
            player.sendMessage(Text.translatable("message.alkahest.bellows_instruction"), true);
            return;
        }
        if (empty()) { player.sendMessage(Text.translatable("message.alkahest.bellows_empty"), true); return; }
        JarContentsComponent jar = JarItem.contents(held);
        if (!JarItem.allowed(jar)) {
            player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true);
            return;
        }
        if (jar.total() > 0 && jar.contents().getFirst().type() != type) {
            player.sendMessage(Text.translatable("message.alkahest.jar_single_type"), true);
            return;
        }
        int free = JarItem.CAPACITY - jar.total();
        if (free <= 0) { player.sendMessage(Text.translatable("message.alkahest.bellows_jar_full"), true); return; }
        int moved = Math.min(free, amount);
        EssenceType movedType = type;
        JarItem.set(held, jar.merge(new JarContentsComponent(List.of(new EssenceStack(movedType, moved, stability))),
            JarItem.CAPACITY));
        amount -= moved;
        if (amount <= 0) { type = null; stability = 0; }
        changed();
        player.sendMessage(Text.translatable("message.alkahest.bellows_poured", movedType.displayName(), moved), true);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.5f, 1.5f);
        }
    }

    public void describe(PlayerEntity player) {
        if (empty()) {
            player.sendMessage(Text.translatable("message.alkahest.bellows_status_empty", CAPACITY), false);
            return;
        }
        player.sendMessage(Text.translatable("message.alkahest.bellows_status",
            storedType().displayName(), amount, CAPACITY, Math.round(stability * 100)), false);
    }

    /** Repairs the visible fill of bellows loaded from an older save. */
    public void refreshLevel() { updateLevel(); }

    private void changed() {
        markDirty();
        updateLevel();
    }

    private void updateLevel() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DistilleryBellowsBlock)) return;
        int level = level();
        if (state.get(DistilleryBellowsBlock.LEVEL) != level) {
            world.setBlockState(pos, state.with(DistilleryBellowsBlock.LEVEL, level), Block.NOTIFY_ALL);
        } else {
            // The fill level did not change but the type or stability may have: resend the data.
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (type != null) nbt.putString("Essence", type.asString());
        nbt.putInt("Amount", amount);
        nbt.putFloat("Stability", stability);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        type = null;
        if (nbt.contains("Essence")) {
            for (EssenceType candidate : EssenceType.values())
                if (candidate.asString().equals(nbt.getString("Essence"))) type = candidate;
        }
        amount = Math.max(0, Math.min(CAPACITY, nbt.getInt("Amount")));
        stability = nbt.getFloat("Stability");
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
