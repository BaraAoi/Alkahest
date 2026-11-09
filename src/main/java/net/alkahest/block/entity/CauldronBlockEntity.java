package net.alkahest.block.entity;

import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class CauldronBlockEntity extends BlockEntity {
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(6, ItemStack.EMPTY);
    private boolean water;
    private int evaporation;
    private static final TagKey<Item> FLOWERS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", "flowers"));
    public CauldronBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CAULDRON, pos, state); }
    public DefaultedList<ItemStack> stacks() { return stacks; }
    private boolean heated() {
        BlockState below = world.getBlockState(pos.down());
        return below.isOf(Blocks.FIRE) || below.isOf(Blocks.SOUL_FIRE) || below.isOf(Blocks.LAVA)
            || below.isOf(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(below);
    }
    public void fill(PlayerEntity player, Hand hand) {
        if (water) { message(player, "message.alkahest.cauldron_full"); return; }
        water = true; evaporation = 0;
        if (!player.isCreative()) player.setStackInHand(hand, new ItemStack(Items.BUCKET));
        markDirty(); message(player, "message.alkahest.cauldron_filled");
    }
    public void add(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) {
                stacks.set(i, held.copyWithCount(1)); held.decrementUnlessCreative(1, player);
                evaporation = 0; markDirty(); message(player, "message.alkahest.cauldron_added"); return;
            }
        }
        message(player, "message.alkahest.cauldron_slots_full");
    }
    public void empty(PlayerEntity player) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) { if (!player.giveItemStack(stack)) player.dropItem(stack, false); stacks.set(i, ItemStack.EMPTY); }
        }
        markDirty(); message(player, "message.alkahest.cauldron_emptied");
    }
    public void activate(PlayerEntity player) {
        if (!water) { message(player, "message.alkahest.need_water"); return; }
        if (!heated()) { message(player, "message.alkahest.need_heat"); return; }
        List<ItemStack> inputs = stacks.stream().filter(s -> !s.isEmpty()).toList();
        ItemStack output = recipe(inputs);
        if (output.isEmpty()) { message(player, "message.alkahest.no_recipe"); return; }
        stacks.replaceAll(ignored -> ItemStack.EMPTY); water = false; evaporation = 0; markDirty();
        if (!player.giveItemStack(output)) player.dropItem(output, false);
        message(player, "message.alkahest.cauldron_success");
    }
    private static ItemStack recipe(List<ItemStack> inputs) {
        List<Item> items = new ArrayList<>(); inputs.forEach(s -> items.add(s.getItem()));
        if (items.size() == 2 && items.contains(Items.GLASS_BOTTLE) && (items.contains(Items.COAL) || items.contains(Items.CHARCOAL))) return new ItemStack(ModItems.PURIFIED_WATER);
        if (items.size() == 3 && inputs.stream().filter(s -> s.isIn(FLOWERS)).count() == 2 && inputs.stream().filter(s -> s.contains(DataComponentTypes.FOOD) && !s.isIn(FLOWERS)).count() == 1) return new ItemStack(ModItems.HERBAL_PASTE, 2);
        if (items.size() == 2 && items.contains(Items.BLAZE_POWDER) && items.contains(Items.REDSTONE)) return new ItemStack(ModItems.FIRE_DUST, 2);
        if (items.size() == 2 && items.contains(Items.SPIDER_EYE) && items.contains(Items.CACTUS)) return new ItemStack(ModItems.CAUSTIC_SLIME, 2);
        if (items.size() == 3 && items.contains(ModItems.SALT) && items.contains(ModItems.CRYSTALS.get(net.alkahest.essence.EssenceType.ORDO)) && items.contains(Items.STRING)) return new ItemStack(ModItems.BINDING_SALT, 2);
        if (items.size() == 3 && items.contains(Items.GLASS_PANE) && items.contains(Items.GOLD_INGOT) && items.contains(ModItems.CRYSTALS.get(net.alkahest.essence.EssenceType.ORDO))) return new ItemStack(ModItems.ALCHEMISTS_MONOCLE);
        return ItemStack.EMPTY;
    }
    public static void tick(World world, BlockPos pos, CauldronBlockEntity self) {
        if (self.water && self.heated() && self.stacks.stream().allMatch(ItemStack::isEmpty)) {
            if (++self.evaporation >= 400) {
                self.water = false; self.evaporation = 0;
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(ModItems.SALT, 2));
            }
            self.markDirty();
        } else if (self.evaporation != 0) { self.evaporation = 0; self.markDirty(); }
    }
    private void message(PlayerEntity player, String key) { player.sendMessage(Text.translatable(key), true); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, stacks, registries); nbt.putBoolean("Water", water); nbt.putInt("Evaporation", evaporation);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); Inventories.readNbt(nbt, stacks, registries); water = nbt.getBoolean("Water"); evaporation = nbt.getInt("Evaporation");
    }
}
