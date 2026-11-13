package net.alkahest.screen;

import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.registry.ModScreenHandlers;
import net.alkahest.transmutation.TransmutationRecipe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** Interface of the Transmutator core: input, amulet and output. */
public class TransmutatorScreenHandler extends ScreenHandler {
    public static final int PLAYER_INV_START = 3;
    public static final int PLAYER_INV_END = PLAYER_INV_START + 36;
    // Shared with the client artwork. Slot indices and storage remain unchanged.
    public static final int WIDTH = 236, HEIGHT = 220;
    public static final int INPUT_X = 26, INPUT_Y = 45;
    public static final int AMULET_X = 26, AMULET_Y = 78;
    public static final int OUTPUT_X = 102, OUTPUT_Y = 45;
    public static final int PLAYER_X = 37, PLAYER_Y = 143, HOTBAR_Y = 201;
    public static final int CHOICE_X = 54, CHOICE_Y = 72, CHOICE_WIDTH = 74, CHOICE_HEIGHT = 9;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public TransmutatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(4));
    }

    public TransmutatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.TRANSMUTATOR, syncId);
        checkSize(inventory, 3);
        checkDataCount(properties, 4);
        this.inventory = inventory;
        this.properties = properties;
        inventory.onOpen(playerInventory.player);

        addSlot(new Slot(inventory, TransmutatorCoreBlockEntity.INPUT, INPUT_X, INPUT_Y) {
            @Override public boolean canInsert(ItemStack stack) { return TransmutationRecipe.find(stack) != null; }
        });
        addSlot(new Slot(inventory, TransmutatorCoreBlockEntity.AMULET, AMULET_X, AMULET_Y) {
            @Override public boolean canInsert(ItemStack stack) { return stack.getItem() instanceof AlkahestAmuletItem; }
        });
        addSlot(new Slot(inventory, TransmutatorCoreBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, PLAYER_X + col * 18, HOTBAR_Y));

        addProperties(properties);
    }

    public int progress() { return properties.get(0); }
    public int maxProgress() { return Math.max(1, properties.get(1)); }
    public int status() { return properties.get(2); }
    public int selectedRecipeIndex() { return properties.get(3); }
    public int recipeCount() { return TransmutationRecipe.choices(inventory.getStack(TransmutatorCoreBlockEntity.INPUT)).size(); }
    public boolean canSelectRecipe() { return recipeCount() > 1; }
    public ItemStack selectedOutput() {
        var recipe = TransmutationRecipe.find(inventory.getStack(TransmutatorCoreBlockEntity.INPUT), selectedRecipeIndex());
        return recipe == null ? ItemStack.EMPTY : recipe.output();
    }

    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != 0 || player.isSpectator() || !canUse(player) || !(inventory instanceof TransmutatorCoreBlockEntity core)) return false;
        return core.cycleRecipe();
    }

    /** The cost of the current input, computed on both sides from the recipe table. */
    public java.util.List<net.alkahest.essence.EssenceStack> requirements() {
        return TransmutatorCoreBlockEntity.requirements(inventory.getStack(TransmutatorCoreBlockEntity.INPUT), selectedRecipeIndex());
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
        } else if (stack.getItem() instanceof AlkahestAmuletItem) {
            if (!insertItem(stack, TransmutatorCoreBlockEntity.AMULET, TransmutatorCoreBlockEntity.AMULET + 1, false)) return ItemStack.EMPTY;
        } else if (TransmutationRecipe.find(stack) != null) {
            if (!insertItem(stack, TransmutatorCoreBlockEntity.INPUT, TransmutatorCoreBlockEntity.INPUT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, original);
        return original;
    }
}
