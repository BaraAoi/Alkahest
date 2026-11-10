package net.alkahest.registry;

import com.mojang.serialization.Codec;
import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModDataComponents {
    public static final ComponentType<JarContentsComponent> JAR_CONTENTS = register("jar_contents", JarContentsComponent.CODEC);
    public static final ComponentType<Integer> ESSENCE_CHARGE = register("essence_charge", Codec.intRange(0, 100));
    public static final ComponentType<Integer> SOUL_CHARGES = register("soul_charges", Codec.intRange(0, 100));
    public static final ComponentType<Boolean> SOULBOUND = register("soulbound", Codec.BOOL);
    public static final ComponentType<EssenceType> PIPE_FILTER = register("pipe_filter", EssenceType.CODEC);
    public static final ComponentType<Float> ESSENCE_STABILITY = register("essence_stability", Codec.floatRange(0.0f, 1.0f));
    public static final ComponentType<Integer> SOLVENT_CHARGES = register("solvent_charges", Codec.intRange(0, 32));
    public static final ComponentType<Integer> AMULET_USES_STEP = register("amulet_uses_step", Codec.intRange(0, 4));

    private static <T> ComponentType<T> register(String name, Codec<T> codec) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Alkahest.id(name),
            ComponentType.<T>builder().codec(codec).packetCodec(PacketCodecs.codec(codec)).build());
    }

    public static void register() { }
    private ModDataComponents() { }
}
