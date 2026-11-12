package net.alkahest.block.entity;

import net.alkahest.essence.JarContentsComponent;
import net.alkahest.essence.JarVisuals;
import net.alkahest.block.custom.JarBlock;
import net.minecraft.block.Block;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** The item component is the single source of truth for world and portable storage. */
public class JarBlockEntity extends BlockEntity {
    private boolean pipeSource;
    private final DefaultedList<ItemStack> storage = DefaultedList.ofSize(1, ItemStack.EMPTY);
    public JarBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.JAR, pos, state); }
    private ItemStack holder() {
        if (storage.get(0).isEmpty()) storage.set(0, new ItemStack(ModBlocks.JAR));
        return storage.get(0);
    }
    public JarContentsComponent contents() { return JarItem.contents(holder()); }
    /** Explicit source jars feed receivers, never other jars; old jars default to storage. */
    public boolean pipeSource() { return pipeSource; }
    public void setPipeSource(boolean value) {
        if (pipeSource == value) return;
        pipeSource = value;
        markDirty();
        if (world != null && !world.isClient && world.getBlockState(pos).isOf(ModBlocks.JAR)) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }
    public void setContents(JarContentsComponent value) {
        int before = net.alkahest.essence.EssenceComparator.signal(contents().total(), JarItem.CAPACITY);
        JarItem.set(holder(), value); markDirty();
        if (world != null && !world.isClient && world.getBlockState(pos).isOf(ModBlocks.JAR)) {
            updateLevel();
            if (before != net.alkahest.essence.EssenceComparator.signal(value.total(), JarItem.CAPACITY))
                world.updateComparators(pos, ModBlocks.JAR);
            // Notify even if level did not change: a mixture may have changed color.
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }
    public void updateLevel() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.isOf(ModBlocks.JAR)) {
            int level = JarVisuals.level(contents());
            if (state.get(JarBlock.LEVEL) != level) world.setBlockState(pos, state.with(JarBlock.LEVEL, level), Block.NOTIFY_ALL);
        }
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, storage, registries);
        if (pipeSource) nbt.putBoolean("PipeSource", true);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); Inventories.readNbt(nbt, storage, registries);
        pipeSource = nbt.getBoolean("PipeSource");
        // The color may change without changing the quantized level: rebuild the client mesh.
        if (world != null && world.isClient) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }
    @Override protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder); builder.add(ModDataComponents.JAR_CONTENTS, contents());
        if (pipeSource) builder.add(ModDataComponents.JAR_PIPE_SOURCE, true);
    }
    @Override protected void readComponents(ComponentsAccess components) {
        super.readComponents(components); setContents(components.getOrDefault(ModDataComponents.JAR_CONTENTS, JarContentsComponent.EMPTY));
        setPipeSource(components.getOrDefault(ModDataComponents.JAR_PIPE_SOURCE, false));
    }
}
