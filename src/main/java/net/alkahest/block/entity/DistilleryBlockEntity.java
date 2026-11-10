package net.alkahest.block.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.research.EarlyResearch;
import net.minecraft.server.network.ServerPlayerEntity;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
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
import net.minecraft.world.World;

public class DistilleryBlockEntity extends BlockEntity {
    // One input and one output in this early, GUI-less implementation.
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private int progress;
    public DistilleryBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.DISTILLERY, pos, state); }
    public DefaultedList<ItemStack> stacks() { return stacks; }
    public void insert(PlayerEntity player, Hand hand) {
        if (!stacks.get(0).isEmpty() || !stacks.get(1).isEmpty()) { message(player, "message.alkahest.distillery_busy"); return; }
        ItemStack held = player.getStackInHand(hand);
        var essences = ItemEssenceMap.get(held);
        boolean organic = essences.stream().anyMatch(e -> e.type() == EssenceType.HERBA || e.type() == EssenceType.AQUA || e.type() == EssenceType.MORTUUS);
        if (!organic) { message(player, "message.alkahest.distillery_organic"); return; }
        stacks.set(0, held.copyWithCount(1)); held.decrementUnlessCreative(1, player);
        progress = 0; markDirty(); message(player, "message.alkahest.distillery_started");
    }
    public void collect(PlayerEntity player) {
        if (stacks.get(1).isEmpty()) {
            if (!stacks.get(0).isEmpty()) player.sendMessage(Text.translatable("message.alkahest.distillery_progress", Math.min(100, progress * 100 / 1200)), true);
            else message(player, "message.alkahest.distillery_wait");
            return;
        }
        ItemStack output = stacks.get(1); stacks.set(1, ItemStack.EMPTY);
        if (world != null && !world.isClient && player instanceof ServerPlayerEntity researcher && ModItems.CRYSTALS.containsValue(output.getItem()))
            EarlyResearch.discoverSight(researcher, world, pos);
        if (!player.giveItemStack(output)) player.dropItem(output, false);
        markDirty();
    }
    public static void tick(World world, BlockPos pos, DistilleryBlockEntity self) {
        if (self.stacks.get(0).isEmpty() || !self.stacks.get(1).isEmpty()) return;
        if (++self.progress >= 1200) {
            var essences = ItemEssenceMap.get(self.stacks.get(0));
            ItemStack result;
            if (essences.isEmpty() || world.random.nextFloat() < 0.2f) result = new ItemStack(ModItems.ASH);
            else {
                var essence = essences.get(world.random.nextInt(essences.size()));
                result = new ItemStack(ModItems.CRYSTALS.get(essence.type()), 1 + world.random.nextInt(3));
                result.set(ModDataComponents.ESSENCE_STABILITY, 0.15f + world.random.nextFloat() * 0.15f);
            }
            self.stacks.set(0, ItemStack.EMPTY); self.stacks.set(1, result); self.progress = 0;
        }
        self.markDirty();
    }
    private void message(PlayerEntity player, String key) { player.sendMessage(Text.translatable(key), true); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, stacks, registries); nbt.putInt("Progress", progress);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); Inventories.readNbt(nbt, stacks, registries); progress = nbt.getInt("Progress");
    }
}
