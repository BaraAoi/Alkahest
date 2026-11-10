package net.alkahest.block.entity;

import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
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

    private void sync() {
        if (world == null) return;
        BlockState state = world.getBlockState(pos);
        world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
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
