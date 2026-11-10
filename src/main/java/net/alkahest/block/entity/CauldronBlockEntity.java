package net.alkahest.block.entity;

import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.recipe.CauldronEssenceRecipes;
import net.alkahest.research.EarlyResearch;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.minecraft.block.Block;
import net.alkahest.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;

public class CauldronBlockEntity extends BlockEntity {
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(6, ItemStack.EMPTY);
    private static final String RETURNED_ITEM_TAG = "alkahest_cauldron_returned";
    public static final int MAX_PER_TYPE = 256;
    public static final float RESIDUE_STABILITY = 0.05f;
    private final EnumMap<EssenceType, Integer> essences = new EnumMap<>(EssenceType.class);
    public Map<EssenceType, Integer> essences() { return Map.copyOf(essences); }
    private boolean water;
    private boolean dirtyWater;
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
        if (water && !dirtyWater) { message(player, "message.alkahest.cauldron_full"); return; }
        boolean cleaning = water && dirtyWater;
        dirtyWater = false;
        setWater(true); evaporation = 0;
        if (!player.isCreative()) player.setStackInHand(hand, new ItemStack(Items.BUCKET));
        markDirty(); message(player, cleaning ? "message.alkahest.cauldron_cleaned" : "message.alkahest.cauldron_filled");
    }
    private boolean insertOne(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!water) return false;
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) {
                stacks.set(i, stack.copyWithCount(1));
                evaporation = 0;
                markDirty();
                return true;
            }
        }
        return false;
    }
    public void add(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!water) { message(player, "message.alkahest.need_water"); return; }
        if (insertOne(held)) {
            if (!player.isCreative()) held.decrement(1);
            player.sendMessage(Text.translatable("message.alkahest.cauldron_added", stacks.stream().filter(s -> !s.isEmpty()).count()), true);
        } else message(player, "message.alkahest.cauldron_slots_full");
    }
    /** Adds a single mapped item atomically; the caller owns and consumes the original item. */
    public boolean dissolveOne(ItemStack stack) {
        if (world == null || world.isClient || !water || !heated() || stack.isEmpty()) return false;
        var values = ItemEssenceMap.get(stack);
        var additions = new EnumMap<EssenceType, Integer>(EssenceType.class);
        for (var entry : ModItems.CRYSTALS.entrySet()) if (stack.isOf(entry.getValue())) additions.put(entry.getKey(), 1);
        for (var value : values) {
            if (value.type() == EssenceType.VACUUM || !ModItems.CRYSTALS.containsKey(value.type())) return false;
            additions.merge(value.type(), value.amount(), Integer::sum);
        }
        if (additions.isEmpty()) return false;
        if (additions.entrySet().stream().anyMatch(e -> essences.getOrDefault(e.getKey(), 0) + e.getValue() > MAX_PER_TYPE)) return false;
        additions.forEach((type, amount) -> essences.merge(type, amount, Integer::sum));
        evaporation = 0; syncEssences(); return true;
    }
    private void collectDroppedIngredients(World world, BlockPos pos) {
        if (!water || !heated()) return;
        Box opening = new Box(pos.getX() + 0.1875, pos.getY() + 0.25, pos.getZ() + 0.1875,
                              pos.getX() + 0.8125, pos.getY() + 1.0, pos.getZ() + 0.8125);
        for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, opening,
                e -> e.isAlive() && !e.getCommandTags().contains(RETURNED_ITEM_TAG))) {
            if (entity.getX() < opening.minX || entity.getX() > opening.maxX ||
                entity.getZ() < opening.minZ || entity.getZ() > opening.maxZ ||
                entity.getY() < opening.minY || entity.getY() > opening.maxY) continue;
            ItemStack dropped = entity.getStack();
            if (dropped.isOf(ModItems.CRYSTALLIZATION_POWDER)) {
                if (crystallize() > 0) dropped.decrement(1);
            } else {
                while (dissolveOne(dropped)) dropped.decrement(1);
            }
            if (dropped.isEmpty()) entity.discard(); else entity.setStack(dropped);
        }
    }
    /** Convert all possible residue 1:1 into separately typed 5% crystals; no bank mixtures. */
    public int crystallize() {
        if (world == null || world.isClient) return 0;
        int produced = 0;
        for (EssenceType type : EssenceType.values()) {
            int remaining = essences.getOrDefault(type, 0);
            Item crystal = ModItems.CRYSTALS.get(type);
            if (crystal == null || remaining <= 0) continue;
            while (remaining > 0) {
                int count = Math.min(64, remaining);
                ItemStack result = new ItemStack(crystal, count);
                result.set(ModDataComponents.ESSENCE_STABILITY, RESIDUE_STABILITY);
                ItemEntity output = new ItemEntity(world, pos.getX()+.5, pos.getY()+1.15, pos.getZ()+.5, result, 0, .18, 0);
                output.addCommandTag(RETURNED_ITEM_TAG);
                output.setToDefaultPickupDelay();
                if (!world.spawnEntity(output)) break;
                remaining -= count; produced += count;
                if (remaining == 0) essences.remove(type); else essences.put(type, remaining);
            }
        }
        if (produced > 0) {
            syncEssences();
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                net.minecraft.sound.SoundCategory.BLOCKS, .8f, 1.2f);
        }
        return produced;
    }
    public boolean crystallizeWith(PlayerEntity player, Hand hand) {
        if (!water) { message(player, "message.alkahest.need_water"); return false; }
        if (!heated()) { message(player, "message.alkahest.need_heat"); return false; }
        int amount = crystallize();
        if (amount == 0) { message(player, "message.alkahest.cauldron_no_residue"); return false; }
        if (!player.isCreative()) player.getStackInHand(hand).decrement(1);
        player.sendMessage(Text.translatable("message.alkahest.cauldron_crystallized", amount), true);
        return true;
    }
    public void describeEssences(PlayerEntity player) {
        if (essences.isEmpty()) { message(player, "message.alkahest.cauldron_no_residue"); return; }
        player.sendMessage(Text.translatable("message.alkahest.cauldron_contents"), false);
        for (EssenceType type : EssenceType.values()) {
            int amount = essences.getOrDefault(type, 0);
            if (amount > 0) player.sendMessage(Text.literal("◆ " + type.displayName() + ": " + amount)
                .styled(style -> style.withColor(type.color())), false);
        }
    }
    private void syncEssences() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }
    @Override public net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    public void empty(PlayerEntity player) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                if (!player.giveItemStack(stack)) {
                    ItemEntity returned = player.dropItem(stack, false);
                    // Do not immediately swallow items returned to a player with a full inventory.
                    if (returned != null) returned.addCommandTag(RETURNED_ITEM_TAG);
                }
                stacks.set(i, ItemStack.EMPTY);
            }
        }
        markDirty(); message(player, "message.alkahest.cauldron_emptied");
    }
    public boolean activate(PlayerEntity player) {
        if (!water) { message(player, "message.alkahest.need_water"); return false; }
        if (!heated()) { message(player, "message.alkahest.need_heat"); return false; }
        List<ItemStack> inputs = stacks.stream().filter(s -> !s.isEmpty()).toList();
        if (player instanceof ServerPlayerEntity researcher && world instanceof ServerWorld serverWorld) {
            var attempt = EarlyResearch.identify(EarlyResearch.flags(researcher), inputs);
            if (attempt == EarlyResearch.Attempt.ALREADY_FAILED) {
                message(player, "message.alkahest.research.already_failed");
                return false;
            }
            if (attempt != EarlyResearch.Attempt.NONE) {
                if (!EarlyResearch.record(researcher, attempt, serverWorld, pos)) return false;
                if (attempt == EarlyResearch.Attempt.SAND) {
                    dirtyWater = true;
                    syncWaterState();
                }
                stacks.replaceAll(ignored -> ItemStack.EMPTY);
                evaporation = 0;
                markDirty();
                return true;
            }
        }
        if (inputs.isEmpty()) { describeEssences(player); return false; }
        if (inputs.size() != 1) { message(player, "message.alkahest.cauldron_one_catalyst"); return false; }
        var recipe = CauldronEssenceRecipes.all().stream().filter(r -> r.matches(inputs.getFirst(), essences)).findFirst().orElse(null);
        if (recipe == null) { message(player, "message.alkahest.cauldron_no_essence_recipe"); return false; }
        recipe.cost().forEach((type, amount) -> {
            int rest = essences.get(type) - amount;
            if (rest == 0) essences.remove(type); else essences.put(type, rest);
        });
        ItemStack output = new ItemStack(recipe.result(), recipe.count());
        stacks.replaceAll(ignored -> ItemStack.EMPTY); setWater(false); evaporation = 0; syncEssences();
        if (player instanceof ServerPlayerEntity researcher) {
            if (output.isOf(ModItems.PURIFIED_WATER)) EarlyResearch.discoverReactive(researcher, world, pos);
            if (output.isOf(ModItems.HERBAL_PASTE)) EarlyResearch.discoverRaw(researcher, world, pos);
        }
        if (!player.giveItemStack(output)) player.dropItem(output, false);
        message(player, "message.alkahest.cauldron_success");
        return true;
    }
    private void setWater(boolean value) {
        water = value;
        if (!water) dirtyWater = false;
        syncWaterState();
        markDirty();
    }
    private void syncWaterState() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof AlchemyCauldronBlock &&
            (state.get(AlchemyCauldronBlock.FILLED) != water || state.get(AlchemyCauldronBlock.DIRTY) != (water && dirtyWater))) {
            world.setBlockState(pos, state.with(AlchemyCauldronBlock.FILLED, water)
                .with(AlchemyCauldronBlock.DIRTY, water && dirtyWater), Block.NOTIFY_ALL);
        }
    }
    public static void tick(World world, BlockPos pos, CauldronBlockEntity self) {
        // Old worlds saved the water only in NBT. Restore the visual state after loading.
        self.syncWaterState();
        self.collectDroppedIngredients(world, pos);
        if (self.water && self.heated() && self.essences.isEmpty() && self.stacks.stream().allMatch(ItemStack::isEmpty)) {
            if (++self.evaporation >= 400) {
                self.setWater(false); self.evaporation = 0;
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(ModItems.SALT, 2));
            }
            self.markDirty();
        } else if (self.evaporation != 0) { self.evaporation = 0; self.markDirty(); }
    }
    private void message(PlayerEntity player, String key) { player.sendMessage(Text.translatable(key), true); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, stacks, registries); nbt.putBoolean("Water", water); nbt.putBoolean("DirtyWater", dirtyWater); nbt.putInt("Evaporation", evaporation);
        NbtCompound stored = new NbtCompound(); essences.forEach((type, amount) -> stored.putInt(type.asString(), amount)); nbt.put("Essences", stored);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); stacks.clear(); Inventories.readNbt(nbt, stacks, registries); water = nbt.getBoolean("Water"); dirtyWater = water && nbt.getBoolean("DirtyWater"); evaporation = nbt.getInt("Evaporation");
        essences.clear(); NbtCompound stored = nbt.getCompound("Essences");
        for (EssenceType type : EssenceType.values()) {
            int amount = stored.getInt(type.asString());
            if (amount > 0 && type != EssenceType.VACUUM) essences.put(type, Math.min(amount, MAX_PER_TYPE));
        }
    }
}
