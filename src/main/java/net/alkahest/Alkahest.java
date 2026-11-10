package net.alkahest;

import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModPayloads;
import net.alkahest.registry.ModScreenHandlers;
import net.alkahest.registry.ModWorldGen;
import net.alkahest.research.EarlyResearch;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public final class Alkahest implements ModInitializer {
    public static final String MOD_ID = "alkahest";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModDataComponents.register();
        ModEffects.register();
        ModPayloads.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModEntities.register();
        ModItems.register();
        ModScreenHandlers.register();
        ModWorldGen.register();

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (!(entity instanceof ServerPlayerEntity player) || !EarlyResearch.hasMonocle(player)) return;
            if (killedEntity.getType() == EntityType.BLAZE) {
                EarlyResearch.discoverIgnis(player, world, killedEntity.getBlockPos());
            } else if (killedEntity.getType() == EntityType.GUARDIAN || killedEntity.getType() == EntityType.ELDER_GUARDIAN) {
                EarlyResearch.discoverAqua(player, world, killedEntity.getBlockPos());
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            if (state.isOf(ModBlocks.SALT_ORE)) {
                EarlyResearch.discoverBinding(serverPlayer, world, pos);
                return;
            }
            if (state.isOf(ModBlocks.ALCHEMICAL_MOSS)) {
                EarlyResearch.discoverAlkahest(serverPlayer, world, pos);
                return;
            }
            if (EarlyResearch.hasMonocle(serverPlayer) && (
                    state.isOf(Blocks.PRISMARINE)
                    || state.isOf(Blocks.PRISMARINE_BRICKS)
                    || state.isOf(Blocks.DARK_PRISMARINE)
                    || state.isOf(Blocks.SEA_LANTERN))) {
                EarlyResearch.discoverAqua(serverPlayer, world, pos);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 40 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator()) continue;
                if (player.getWorld().getBiome(player.getBlockPos()).matchesKey(ModWorldGen.ALCHEMICAL_WASTES)) {
                    EarlyResearch.discoverAlkahest(player, player.getWorld(), player.getBlockPos());
                }
            }
        });

        // Vanilla skips block interaction while sneaking with an item; this block always opens its GUI.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!player.isSpectator() && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.DISTILLERY)) {
                if (!world.isClient && world.getBlockEntity(hit.getBlockPos()) instanceof net.alkahest.block.entity.DistilleryBlockEntity d)
                    player.openHandledScreen(d);
                return ActionResult.success(world.isClient);
            }
            if (!player.isSpectator() && player.isSneaking() && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.JAR))
                return net.alkahest.block.custom.JarBlock.fillCrystalStack(world, hit.getBlockPos(), player, hand);
            if (!player.isSpectator() && player.isSneaking() && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.ALCHEMY_CAULDRON))
                return net.alkahest.block.custom.AlchemyCauldronBlock.sneakUseWithItem(world, hit.getBlockPos(), player, hand);
            return ActionResult.PASS;
        });
    }
}
