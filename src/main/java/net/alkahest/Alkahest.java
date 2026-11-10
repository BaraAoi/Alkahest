package net.alkahest;

import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModWorldGen;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModPayloads;
import net.alkahest.registry.ModEffects;
import net.alkahest.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
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
        ModItems.register();
        ModScreenHandlers.register();
        ModEntities.register();
        ModWorldGen.register();
        // Vanilla skips block interaction while sneaking with an item; this block always opens its GUI.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!player.isSpectator() && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.DISTILLERY)) {
                if (!world.isClient && world.getBlockEntity(hit.getBlockPos()) instanceof net.alkahest.block.entity.DistilleryBlockEntity d)
                    player.openHandledScreen(d);
                return net.minecraft.util.ActionResult.success(world.isClient);
            }
            if (!player.isSpectator() && player.isSneaking() && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.JAR))
                return net.alkahest.block.custom.JarBlock.fillCrystalStack(world, hit.getBlockPos(), player, hand);
            if (!player.isSpectator() && player.isSneaking() && world.getBlockState(hit.getBlockPos()).isOf(ModBlocks.ALCHEMY_CAULDRON))
                return net.alkahest.block.custom.AlchemyCauldronBlock.sneakUseWithItem(world, hit.getBlockPos(), player, hand);
            return net.minecraft.util.ActionResult.PASS;
        });
    }
}
