package net.alkahest.screen;

import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.essence.EssenceType;
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

/** Compact interface of the tier-2 distillery: one input slot, progress and state. */
public class Tier2DistilleryScreenHandler extends ScreenHandler {
    public static final int PLAYER_INV_START = 1;
    public static final int PLAYER_INV_END = PLAYER_INV_START + 36;
    public static final int INPUT_X = 80, INPUT_Y = 30;
    public static final int PENDING_START = 3, PROPERTY_COUNT = PENDING_START + EssenceType.values().length;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public Tier2DistilleryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(PROPERTY_COUNT));
    }

    public Tier2DistilleryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.TIER2_DISTILLERY, syncId);
        checkSize(inventory, 1);
        checkDataCount(properties, PROPERTY_COUNT);
        this.inventory = inventory;
        this.properties = properties;
        inventory.onOpen(playerInventory.player);

        // Taking the input resets progress through the inventory's removeStack override.
        addSlot(new Slot(inventory, 0, INPUT_X, INPUT_Y) {
            @Override public boolean canInsert(ItemStack stack) { return Tier2DistilleryBlockEntity.accepts(stack, playerInventory.player.getWorld()); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));

        addProperties(properties);
    }

    /** Checked only server-side for the player actually using a running assembled machine. */
    public boolean runningForResearch() {
        return inventory instanceof Tier2DistilleryBlockEntity machine && machine.progress() > 0
            && machine.status() == Tier2DistilleryBlockEntity.STATUS_WORKING && !machine.bellows().isEmpty();
    }

    public int progress() { return properties.get(0); }
    public int maxProgress() { return properties.get(1); }
    public int status() { return properties.get(2); }
    public int pendingAmount(EssenceType type) { return properties.get(PENDING_START + type.ordinal()); }
    public int pendingTotal() {
        int total = 0;
        for (EssenceType type : EssenceType.values()) total += pendingAmount(type);
        return total;
    }

    @Override public boolean canUse(PlayerEntity player) { return inventory.canPlayerUse(player); }
    @Override public void onClosed(PlayerEntity player) { super.onClosed(player); inventory.onClose(player); }

    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < PLAYER_INV_START) {
            if (!insertItem(stack, PLAYER_INV_START, PLAYER_INV_END, true)) return ItemStack.EMPTY;
        } else if (!insertItem(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, original);
        return original;
    }
}
