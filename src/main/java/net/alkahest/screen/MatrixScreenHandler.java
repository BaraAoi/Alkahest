package net.alkahest.screen;

import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.matrix.MatrixRecipe;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Interface of the Matrix: one base item, three optional parts and the result. */
public class MatrixScreenHandler extends ScreenHandler {
    public static final int PLAYER_INV_START = 2;
    public static final int PLAYER_INV_END = PLAYER_INV_START + 36;
    public static final int BASE_X = 44, BASE_Y = 28;
    public static final int OUTPUT_X = 122, OUTPUT_Y = 28;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public MatrixScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(3));
    }

    public MatrixScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.MATRIX, syncId);
        checkSize(inventory, 2);
        checkDataCount(properties, 3);
        this.inventory = inventory;
        this.properties = properties;
        inventory.onOpen(playerInventory.player);

        addSlot(new Slot(inventory, MatrixCoreBlockEntity.BASE, BASE_X, BASE_Y));
        addSlot(new Slot(inventory, MatrixCoreBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));

        addProperties(properties);
    }

    public int progress() { return properties.get(0); }
    public int maxProgress() { return Math.max(1, properties.get(1)); }
    public int status() { return properties.get(2); }

    /**
     * The matrix pulls its parts off the pedestals, so the client only knows the price of the
     * cheapest recipe for the base item; the server decides whether a synthesis is possible.
     */
    public List<EssenceStack> requirements() {
        MatrixRecipe recipe = MatrixRecipe.cheapestFor(inventory.getStack(MatrixCoreBlockEntity.BASE));
        if (recipe == null) return List.of();
        List<EssenceStack> list = new ArrayList<>();
        for (Map.Entry<net.alkahest.essence.EssenceType, Integer> entry : recipe.cost().entrySet()) {
            list.add(new EssenceStack(entry.getKey(), entry.getValue(), 1f));
        }
        return list;
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
        } else {
            if (!insertItem(stack, MatrixCoreBlockEntity.BASE, MatrixCoreBlockEntity.BASE + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, original);
        return original;
    }
}
