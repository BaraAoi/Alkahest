package net.alkahest.registry;

import net.alkahest.Alkahest;
import net.alkahest.effect.CrystallizedEffect;
import net.alkahest.effect.InNapalmEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

public final class ModEffects {
    public static final RegistryEntry<StatusEffect> IN_NAPALM = Registry.registerReference(
        Registries.STATUS_EFFECT, Alkahest.id("in_napalm"), new InNapalmEffect());
    public static final RegistryEntry<StatusEffect> CRYSTALLIZED = Registry.registerReference(
        Registries.STATUS_EFFECT, Alkahest.id("crystallized"), new CrystallizedEffect());
    public static void register() { }
    private ModEffects() { }
}
