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

/** Queued distillery with optional Era III Alkahest solvent slot; server owns progress and output. */
public class DistilleryScreenHandler extends ScreenHandler {
    public static final int PLAYER_INV_START = DistilleryBlockEntity.OUTPUT_END; // 8
    public static final int PLAYER_INV_END = PLAYER_INV_START + 36;              // 44
    public static final int CATALYST_SCREEN_SLOT = PLAYER_INV_END;               // 44
    public static final int CATALYST_X = 128, CATALYST_Y = 31;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public DistilleryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(DistilleryBlockEntity.SIZE), new ArrayPropertyDelegate(3));
    }

    private static boolean canInsertInput(Inventory inv, ItemStack stack) {
        ItemStack catalyst = inv.size() > DistilleryBlockEntity.CATALYST
            ? inv.getStack(DistilleryBlockEntity.CATALYST) : ItemStack.EMPTY;
        return DistilleryBlockEntity.acceptsCatalyst(catalyst)
            ? DistilleryBlockEntity.acceptsWithAlkahest(stack)
            : DistilleryBlockEntity.accepts(stack);
    }

    public DistilleryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.DISTILLERY, syncId);
        checkSize(inventory, DistilleryBlockEntity.SIZE);
        checkDataCount(properties, 3);
        this.inventory = inventory;
        this.properties = properties;
        inventory.onOpen(playerInventory.player);

        // Slot 0: Input (80, 31)
        addSlot(new Slot(inventory, 0, 80, 31) {
            @Override public boolean canInsert(ItemStack stack) { return canInsertInput(inventory, stack); }
            @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                if (inventory instanceof DistilleryBlockEntity distillery) distillery.cancelProgress();
                super.onTakeItem(player, stack);
            }
        });
        // Slots 1..7: Outputs (26 + (i - 1) * 18, 96)
        for (int i = 1; i < DistilleryBlockEntity.OUTPUT_END; i++) {
            addSlot(new Slot(inventory, i, 26 + (i - 1) * 18, 96) {
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                    if (inventory instanceof DistilleryBlockEntity distillery) distillery.discoverOutput(player, stack);
                    super.onTakeItem(player, stack);
                }
            });
        }
        // Slots 8..43: Player inventory and hotbar (preserving exact indices 8 and 35)
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 132 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 190));

        // Slot 44: Optional Alkahest solvent vial (inventory index 9)
        addSlot(new Slot(inventory, DistilleryBlockEntity.CATALYST, CATALYST_X, CATALYST_Y) {
            @Override public boolean canInsert(ItemStack stack) { return DistilleryBlockEntity.acceptsCatalyst(stack); }
            @Override public int getMaxItemCount() { return 1; }
        });

        addProperties(properties);
    }

    public int progress() { return properties.get(0); }
    public int maxProgress() { return properties.get(1); }
    public int status() { return properties.get(2); }
    public boolean isAlkahestActive() { return maxProgress() == DistilleryBlockEntity.ALKAHEST_DURATION; }

    @Override public boolean canUse(PlayerEntity player) { return inventory.canPlayerUse(player); }
    @Override public void onClosed(PlayerEntity player) { super.onClosed(player); inventory.onClose(player); }

    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < PLAYER_INV_START || index == CATALYST_SCREEN_SLOT) {
            if (!insertItem(stack, PLAYER_INV_START, PLAYER_INV_END, true)) return ItemStack.EMPTY;
        } else {
            if (DistilleryBlockEntity.acceptsCatalyst(stack)) {
                if (!insertItem(stack, CATALYST_SCREEN_SLOT, CATALYST_SCREEN_SLOT + 1, false)) return ItemStack.EMPTY;
            } else if (!insertItem(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, original);
        return original;
    }
}
