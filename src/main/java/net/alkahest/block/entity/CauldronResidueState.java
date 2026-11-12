package net.alkahest.block.entity;

import net.alkahest.essence.EssenceType;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/** Saved, bounded-rate crystal output for large cauldrons, independent of the block's lifetime. */
public final class CauldronResidueState extends PersistentState {
    private static final Type<CauldronResidueState> TYPE = new Type<>(CauldronResidueState::new, CauldronResidueState::read, null);
    private static final int IMMEDIATE_STACKS = 64, STACKS_PER_TICK = 8, CHECKS_PER_TICK = 16;
    private final ArrayDeque<Batch> batches = new ArrayDeque<>();
    private static final class Batch {
        final BlockPos pos;
        final EnumMap<EssenceType, Integer> remaining = new EnumMap<>(EssenceType.class);
        Batch(BlockPos pos) { this.pos = pos.toImmutable(); }
    }
    public static CauldronResidueState of(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, "alkahest_cauldron_residue");
    }
    /** The caller transfers ownership only after this method has recorded the whole batch. */
    public void enqueue(ServerWorld world, BlockPos pos, Map<EssenceType, Integer> contents) {
        Batch batch = new Batch(pos);
        contents.forEach((type, amount) -> {
            if (amount > 0 && ModItems.CRYSTALS.containsKey(type)) batch.remaining.put(type, amount);
        });
        if (batch.remaining.isEmpty()) return;
        batches.addLast(batch);
        markDirty();
        if (world.isChunkLoaded(pos)) release(world, batch, IMMEDIATE_STACKS);
        if (batch.remaining.isEmpty()) batches.remove(batch);
    }
    /** Work is bounded, unloaded chunks stay unloaded, rejected entity spawns remain queued. */
    public void tick(ServerWorld world) {
        int budget = STACKS_PER_TICK;
        int checks = Math.min(CHECKS_PER_TICK, batches.size());
        for (int i = 0; i < checks && budget > 0; i++) {
            Batch batch = batches.removeFirst();
            if (world.isChunkLoaded(batch.pos)) budget -= release(world, batch, budget);
            if (!batch.remaining.isEmpty()) batches.addLast(batch);
        }
    }
    private int release(ServerWorld world, Batch batch, int limit) {
        int spawned = 0;
        for (EssenceType type : EssenceType.values()) {
            int remaining = batch.remaining.getOrDefault(type, 0);
            var crystal = ModItems.CRYSTALS.get(type);
            if (crystal == null) continue;
            while (remaining > 0 && spawned < limit) {
                int count = Math.min(remaining, 64);
                ItemStack stack = new ItemStack(crystal, count);
                stack.set(ModDataComponents.ESSENCE_STABILITY, CauldronBlockEntity.RESIDUE_STABILITY);
                BlockPos pos = batch.pos;
                ItemEntity entity = new ItemEntity(world, pos.getX()+.5, pos.getY()+1.15, pos.getZ()+.5, stack, 0, .18, 0);
                entity.addCommandTag(CauldronBlockEntity.RETURNED_ITEM_TAG);
                entity.setToDefaultPickupDelay();
                if (!world.spawnEntity(entity)) return spawned;
                remaining -= count; spawned++;
                if (remaining == 0) batch.remaining.remove(type); else batch.remaining.put(type, remaining);
                markDirty();
            }
            if (spawned >= limit) break;
        }
        return spawned;
    }
    public static CauldronResidueState read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        var state = new CauldronResidueState();
        var list = nbt.getList("Batches", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i); var batch = new Batch(BlockPos.fromLong(entry.getLong("Pos")));
            var amounts = entry.getCompound("Amounts");
            for (EssenceType type : EssenceType.values()) {
                int amount = amounts.getInt(type.asString());
                if (amount > 0 && ModItems.CRYSTALS.containsKey(type)) batch.remaining.put(type, amount);
            }
            if (!batch.remaining.isEmpty()) state.batches.addLast(batch);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        var list = new NbtList();
        for (Batch batch : batches) {
            var entry = new NbtCompound(); entry.putLong("Pos", batch.pos.asLong());
            var amounts = new NbtCompound(); batch.remaining.forEach((type, amount) -> amounts.putInt(type.asString(), amount));
            entry.put("Amounts", amounts); list.add(entry);
        }
        nbt.put("Batches", list); return nbt;
    }
}
