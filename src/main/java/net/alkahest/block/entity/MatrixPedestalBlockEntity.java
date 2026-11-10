package net.alkahest.block.entity;

import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Stands around the Matrix and holds one item on top of it, Thaumcraft style. The core pulls
 * these items into a synthesis, so the player arranges the ingredients in the world instead of
 * dropping them into slots.
 */
public class MatrixPedestalBlockEntity extends BlockEntity {
    private final DefaultedList<ItemStack> storage = DefaultedList.ofSize(1, ItemStack.EMPTY);

    public MatrixPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_PEDESTAL, pos, state);
    }

    public ItemStack held() { return storage.get(0); }

    public boolean isEmpty() { return held().isEmpty(); }

    public void setHeld(ItemStack stack) {
        storage.set(0, stack);
        markDirty();
        sync();
    }

    /** Takes one item off the pedestal. */
    public ItemStack takeOne() {
        ItemStack stack = held();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = stack.copyWithCount(1);
        ItemStack remainder = stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
        setHeld(remainder);
        return taken;
    }

    public void describe(net.minecraft.entity.player.PlayerEntity player) {
        if (isEmpty()) {
            player.sendMessage(Text.translatable("message.alkahest.pedestal_empty"), true);
            return;
        }
        player.sendMessage(Text.translatable("message.alkahest.pedestal_status",
            held().getName(), held().getCount()), false);
    }

    /**
     * Pushes the pedestal to every nearby client. Updating the listeners is not enough on its own:
     * taking the last item out changed nothing a listener reacts to, and the ghost of the item kept
     * floating on clients that never received the new contents.
     */
    private void sync() {
        if (!(world instanceof ServerWorld server)) return;
        BlockState state = world.getBlockState(pos);
        server.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        Packet<?> packet = toUpdatePacket();
        if (packet != null) {
            server.getPlayerManager().sendToAround(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                64.0, server.getRegistryKey(), packet);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, storage, registries);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, storage, registries);
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
