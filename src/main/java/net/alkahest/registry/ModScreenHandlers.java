package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.screen.DistilleryScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
    public static final ScreenHandlerType<DistilleryScreenHandler> DISTILLERY = Registry.register(Registries.SCREEN_HANDLER,
        Alkahest.id("distillery"), new ScreenHandlerType<>(DistilleryScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
    public static void register() { }
    private ModScreenHandlers() { }
}
