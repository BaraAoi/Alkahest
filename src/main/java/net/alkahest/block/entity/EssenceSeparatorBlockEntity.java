package net.alkahest.block.entity;

import net.alkahest.block.custom.EssenceSeparatorBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Early manual separator: preserves every unit and stability, without performing stabilisation. */
public class EssenceSeparatorBlockEntity extends BlockEntity {
    private final DefaultedList<ItemStack> slots = DefaultedList.ofSize(1, ItemStack.EMPTY);
    public EssenceSeparatorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ESSENCE_SEPARATOR, pos, state); }
    public DefaultedList<ItemStack> stacks() { return slots; }
    public void useJar(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof JarItem)) return;
        ItemStack stored = slots.get(0);
        JarContentsComponent supplied = JarItem.contents(held);
        if (!JarItem.allowed(supplied)) { message(player, "message.alkahest.separator_invalid"); return; }
        if (stored.isEmpty()) {
            if (supplied.total() == 0) { message(player, "message.alkahest.separator_needs_contents"); return; }
            slots.set(0, held.copyWithCount(1));
            // Unlike a crafting ingredient this is placed as a persistent item, even in creative.
            player.setStackInHand(hand, ItemStack.EMPTY);
            changed(); message(player, "message.alkahest.separator_inserted");
            return;
        }
        if (supplied.total() != 0) { message(player, "message.alkahest.separator_use_empty"); return; }
        var contents = JarItem.contents(stored);
        if (!JarItem.allowed(contents)) { message(player, "message.alkahest.separator_invalid"); return; }
        if (contents.total() == 0) { message(player, "message.alkahest.separator_finished"); return; }
        EssenceStack fraction = contents.contents().getFirst();
        List<EssenceStack> remaining = new ArrayList<>(contents.contents());
        remaining.remove(0);
        // Both item stacks already exist: the supplied empty jar becomes the output.
        JarItem.set(held, new JarContentsComponent(List.of(fraction)));
        JarItem.set(stored, new JarContentsComponent(remaining));
        changed();
        player.sendMessage(Text.translatable("message.alkahest.separator_extracted", fraction.type().displayName(), fraction.amount()), true);
    }
    public void collect(PlayerEntity player) {
        if (slots.get(0).isEmpty()) { message(player, "message.alkahest.separator_empty"); return; }
        ItemStack returned = slots.get(0); slots.set(0, ItemStack.EMPTY);
        if (!player.giveItemStack(returned)) player.dropItem(returned, false);
        changed(); message(player, "message.alkahest.separator_returned");
    }
    private void message(PlayerEntity player, String key) { player.sendMessage(Text.translatable(key), true); }
    private void changed() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof EssenceSeparatorBlock && state.get(EssenceSeparatorBlock.OCCUPIED) != !slots.get(0).isEmpty())
                world.setBlockState(pos, state.with(EssenceSeparatorBlock.OCCUPIED, !slots.get(0).isEmpty()), Block.NOTIFY_ALL);
        }
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) { super.writeNbt(nbt, registry); Inventories.writeNbt(nbt, slots, registry); }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) { super.readNbt(nbt, registry); Inventories.readNbt(nbt, slots, registry); }
}
