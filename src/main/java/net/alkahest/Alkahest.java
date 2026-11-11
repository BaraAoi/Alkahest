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
        net.alkahest.atmosphere.AtmosphereEvents.register();

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
            net.alkahest.essence.RecipeEssenceService::rebuild);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
            net.alkahest.essence.RecipeEssenceService::clearServer);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                net.alkahest.essence.RecipeEssenceService.rebuild(server);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
                    net.alkahest.essence.RecipeEssenceService.send(player);
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            net.alkahest.research.PracticalResearchStorage.migrate(handler.player);
            net.alkahest.essence.RecipeEssenceService.send(handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isSpectator()) net.alkahest.research.PracticalResearchStorage.scan(player);
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
