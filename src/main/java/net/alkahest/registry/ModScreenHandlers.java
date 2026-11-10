package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.screen.DistilleryScreenHandler;
import net.alkahest.screen.Tier2DistilleryScreenHandler;
import net.alkahest.screen.MatrixScreenHandler;
import net.alkahest.screen.TransmutatorScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
    public static final ScreenHandlerType<DistilleryScreenHandler> DISTILLERY = Registry.register(Registries.SCREEN_HANDLER,
        Alkahest.id("distillery"), new ScreenHandlerType<>(DistilleryScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
    public static final ScreenHandlerType<Tier2DistilleryScreenHandler> TIER2_DISTILLERY = Registry.register(Registries.SCREEN_HANDLER,
        Alkahest.id("distillery_tier2"), new ScreenHandlerType<>(Tier2DistilleryScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
    public static final ScreenHandlerType<TransmutatorScreenHandler> TRANSMUTATOR = Registry.register(Registries.SCREEN_HANDLER,
        Alkahest.id("transmutator"), new ScreenHandlerType<>(TransmutatorScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
    public static final ScreenHandlerType<MatrixScreenHandler> MATRIX = Registry.register(Registries.SCREEN_HANDLER,
        Alkahest.id("matrix"), new ScreenHandlerType<>(MatrixScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
    public static void register() { }
    private ModScreenHandlers() { }
}
