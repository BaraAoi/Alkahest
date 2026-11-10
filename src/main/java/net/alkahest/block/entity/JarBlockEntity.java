package net.alkahest.block.entity;

import net.alkahest.essence.JarContentsComponent;
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
    private final DefaultedList<ItemStack> storage = DefaultedList.ofSize(1, ItemStack.EMPTY);
    public JarBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.JAR, pos, state); }
    private ItemStack holder() {
        if (storage.get(0).isEmpty()) storage.set(0, new ItemStack(ModBlocks.JAR));
        return storage.get(0);
    }
    public JarContentsComponent contents() { return JarItem.contents(holder()); }
    public void setContents(JarContentsComponent value) { JarItem.set(holder(), value); markDirty(); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); Inventories.writeNbt(nbt, storage, registries);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries); Inventories.readNbt(nbt, storage, registries);
    }
    @Override protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder); builder.add(ModDataComponents.JAR_CONTENTS, contents());
    }
    @Override protected void readComponents(ComponentsAccess components) {
        super.readComponents(components); setContents(components.getOrDefault(ModDataComponents.JAR_CONTENTS, JarContentsComponent.EMPTY));
    }
}
