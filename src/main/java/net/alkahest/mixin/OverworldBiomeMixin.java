package net.alkahest.mixin;

import com.mojang.datafixers.util.Pair;
import net.alkahest.registry.ModWorldGen;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class OverworldBiomeMixin {
    @Shadow
    @Final
    @Mutable
    private MultiNoiseUtil.Entries<RegistryEntry<Biome>> entries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alkahest$injectAlchemicalWastes(
        MultiNoiseBiomeSourceParameterList.Preset preset,
        RegistryEntryLookup<Biome> biomeLookup,
        CallbackInfo ci
    ) {
        if (preset != MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD) {
            return;
        }
        biomeLookup.getOptional(ModWorldGen.ALCHEMICAL_WASTES).ifPresent(wastes -> {
            // Place narrow niche hypercubes ahead of full-coverage vanilla regions so R-tree distance-0 ties pick Wastes inside its niche only.
            var merged = new ArrayList<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>>();
            var temp = MultiNoiseUtil.ParameterRange.of(-0.15f, 0.25f);
            var humidity = MultiNoiseUtil.ParameterRange.of(0.40f, 0.85f);
            var continentalness = MultiNoiseUtil.ParameterRange.of(0.10f, 0.45f);
            var erosion = MultiNoiseUtil.ParameterRange.of(0.20f, 0.55f);
            var posWeird = MultiNoiseUtil.ParameterRange.of(0.65f, 0.88f);
            var negWeird = MultiNoiseUtil.ParameterRange.of(-0.88f, -0.65f);
            for (float depth : new float[]{0.0f, 1.0f}) {
                var depthRange = MultiNoiseUtil.ParameterRange.of(depth);
                merged.add(Pair.of(MultiNoiseUtil.createNoiseHypercube(
                    temp, humidity, continentalness, erosion, depthRange, posWeird, 0.0f), wastes));
                merged.add(Pair.of(MultiNoiseUtil.createNoiseHypercube(
                    temp, humidity, continentalness, erosion, depthRange, negWeird, 0.0f), wastes));
            }
            merged.addAll(this.entries.getEntries());
            this.entries = new MultiNoiseUtil.Entries<>(merged);
        });
    }
}
