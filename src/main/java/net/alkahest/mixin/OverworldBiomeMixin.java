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
import java.util.List;

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
            var temp = MultiNoiseUtil.ParameterRange.of(-0.15f, 0.20f);
            var humidity = MultiNoiseUtil.ParameterRange.of(0.40f, 0.85f);
            var continentalness = MultiNoiseUtil.ParameterRange.of(0.10f, 0.45f);
            var erosion = MultiNoiseUtil.ParameterRange.of(0.20f, 0.55f);
            var surfaceDepth = MultiNoiseUtil.ParameterRange.of(0.0f);
            var weirdNiches = List.of(
                MultiNoiseUtil.ParameterRange.of(0.68f, 0.86f),
                MultiNoiseUtil.ParameterRange.of(-0.86f, -0.68f)
            );

            List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> current =
                new ArrayList<>(this.entries.getEntries());
            for (MultiNoiseUtil.ParameterRange wNiche : weirdNiches) {
                List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> next =
                    new ArrayList<>(current.size() + 8);
                for (var entry : current) {
                    var c = entry.getFirst();
                    if (c.temperature().getDistance(temp) == 0L
                        && c.humidity().getDistance(humidity) == 0L
                        && c.continentalness().getDistance(continentalness) == 0L
                        && c.erosion().getDistance(erosion) == 0L
                        && c.depth().getDistance(surfaceDepth) == 0L
                        && c.weirdness().getDistance(wNiche) == 0L) {
                        long wMin = c.weirdness().min();
                        long wMax = c.weirdness().max();
                        if (wMin < wNiche.min()) {
                            next.add(Pair.of(new MultiNoiseUtil.NoiseHypercube(
                                c.temperature(), c.humidity(), c.continentalness(), c.erosion(), c.depth(),
                                new MultiNoiseUtil.ParameterRange(wMin, wNiche.min() - 1L), c.offset()), entry.getSecond()));
                        }
                        if (wMax > wNiche.max()) {
                            next.add(Pair.of(new MultiNoiseUtil.NoiseHypercube(
                                c.temperature(), c.humidity(), c.continentalness(), c.erosion(), c.depth(),
                                new MultiNoiseUtil.ParameterRange(wNiche.max() + 1L, wMax), c.offset()), entry.getSecond()));
                        }
                    } else {
                        next.add(entry);
                    }
                }
                next.add(Pair.of(MultiNoiseUtil.createNoiseHypercube(
                    temp, humidity, continentalness, erosion, surfaceDepth, wNiche, 0.0f), wastes));
                current = next;
            }
            this.entries = new MultiNoiseUtil.Entries<>(current);
        });
    }
}
