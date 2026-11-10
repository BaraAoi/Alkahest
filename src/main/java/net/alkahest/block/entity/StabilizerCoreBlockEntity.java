package net.alkahest.block.entity;

import net.alkahest.block.custom.StabilizerCoreBlock;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Control block of the 1x3x1 Stabilizer tower.
 * The tower only works after it is activated with an Alkahest amulet or vial.
 */
public class StabilizerCoreBlockEntity extends BlockEntity {
    private boolean activated;

    public StabilizerCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STABILIZER_CORE, pos, state);
    }

    public boolean isActivated() { return activated; }

    public boolean structureFormed() {
        return world != null
            && world.getBlockState(pos.down()).isOf(ModBlocks.STABILIZER_RECEIVER)
            && world.getBlockState(pos.up()).isOf(ModBlocks.STABILIZER_CREST);
    }

    /** Used by tests and by the activation click; always re-syncs the visible state. */
    public void setActivated(boolean value) {
        activated = value && structureFormed();
        markDirty();
        syncState();
    }

    public void toggle(PlayerEntity player) {
        if (world == null || world.isClient) return;
        if (!structureFormed()) {
            player.sendMessage(Text.translatable("message.alkahest.stabilizer_incomplete"), true);
            syncState();
            return;
        }
        activated = !activated;
        markDirty();
        syncState();
        world.playSound(null, pos, activated ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
            SoundCategory.BLOCKS, 0.7f, activated ? 1.2f : 0.9f);
        player.sendMessage(Text.translatable(activated
            ? "message.alkahest.stabilizer_activated" : "message.alkahest.stabilizer_deactivated"), true);
    }

    public void describe(PlayerEntity player) {
        boolean formed = structureFormed();
        player.sendMessage(Text.translatable("message.alkahest.stabilizer_status",
            Text.translatable(formed ? "message.alkahest.stabilizer_formed" : "message.alkahest.stabilizer_not_formed"),
            Text.translatable(activated ? "message.alkahest.stabilizer_running" : "message.alkahest.stabilizer_idle")), false);
        if (formed && world != null && world.getBlockEntity(pos.down()) instanceof StabilizerReceiverBlockEntity receiver) {
            player.sendMessage(Text.translatable("message.alkahest.stabilizer_progress",
                receiver.inputContents().total(),
                receiver.progress() * 100 / StabilizerReceiverBlockEntity.TICKS_PER_TYPE), false);
        }
    }

    /** Recomputes FORMED/ACTIVE; an unfinished tower switches itself off. */
    public void syncState() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof StabilizerCoreBlock)) return;
        boolean formed = structureFormed();
        if (!formed) activated = false;
        BlockState updated = state.with(StabilizerCoreBlock.FORMED, formed).with(StabilizerCoreBlock.ACTIVE, activated);
        if (updated != state) world.setBlockState(pos, updated, net.minecraft.block.Block.NOTIFY_ALL);
    }

    public static void tick(World world, BlockPos pos, StabilizerCoreBlockEntity be) {
        if (world.isClient) return;
        if (world.getTime() % 20 == 0) be.syncState();
        if (be.activated && world instanceof ServerWorld serverWorld && world.getTime() % 5 == 0) {
            serverWorld.spawnParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
        }
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putBoolean("Activated", activated);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        // The world may not be attached yet while NBT is read; the tick loop
        // switches the tower off again if the structure is gone.
        activated = nbt.getBoolean("Activated");
    }
}
