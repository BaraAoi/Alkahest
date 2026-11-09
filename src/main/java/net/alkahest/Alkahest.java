package net.alkahest;

import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModWorldGen;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModPayloads;
import net.alkahest.registry.ModEffects;
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
        ModEntities.register();
        ModWorldGen.register();
    }
}
