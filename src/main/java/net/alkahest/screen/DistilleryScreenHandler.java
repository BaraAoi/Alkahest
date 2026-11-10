package net.alkahest.screen;

import net.alkahest.block.entity.DistilleryBlockEntity;
import net.alkahest.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Two-slot early distillery; the server owns progress and output. */
public class DistilleryScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate properties;
    public DistilleryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(2));
    }
    public DistilleryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.DISTILLERY, syncId);
        checkSize(inventory, 2); checkDataCount(properties, 2);
        this.inventory = inventory; this.properties = properties;
        inventory.onOpen(playerInventory.player);
        addSlot(new Slot(inventory, 0, 44, 32) {
            @Override public boolean canInsert(ItemStack stack) { return DistilleryBlockEntity.accepts(stack); }
            @Override public int getMaxItemCount() { return 1; }
            @Override public int getMaxItemCount(ItemStack stack) { return 1; }
            @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                if (inventory instanceof DistilleryBlockEntity distillery) distillery.cancelProgress();
                super.onTakeItem(player, stack);
            }
        });
        addSlot(new Slot(inventory, 1, 116, 32) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
            @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                if (inventory instanceof DistilleryBlockEntity distillery) distillery.discoverOutput(player, stack);
                super.onTakeItem(player, stack);
            }
        });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++)
            addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col=0; col<9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        addProperties(properties);
    }
    public int progress() { return properties.get(0); }
    public int maxProgress() { return properties.get(1); }
    @Override public boolean canUse(PlayerEntity player) { return inventory.canPlayerUse(player); }
    @Override public void onClosed(PlayerEntity player) { super.onClosed(player); inventory.onClose(player); }
    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(); ItemStack original = stack.copy();
        if (index < 2) {
            if (!insertItem(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!insertItem(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, original);
        return original;
    }
}
