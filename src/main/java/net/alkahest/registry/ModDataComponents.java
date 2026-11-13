package net.alkahest.registry;

import com.mojang.serialization.Codec;
import net.alkahest.Alkahest;
import net.alkahest.essence.EssenceType;
import net.alkahest.block.entity.PipeValveMode;
import net.alkahest.block.entity.PipeDirection;
import net.alkahest.essence.JarContentsComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModDataComponents {
    public static final ComponentType<JarContentsComponent> DISTILLERY_CONTENTS = register("distillery_contents", JarContentsComponent.DISTILLERY_CODEC);
    public static final ComponentType<Boolean> DISTILLERY_REDSTONE = register("distillery_redstone", Codec.BOOL);
    public static final ComponentType<Boolean> JAR_PIPE_SOURCE = register("jar_pipe_source", Codec.BOOL);
    public static final ComponentType<JarContentsComponent> JAR_CONTENTS = register("jar_contents", JarContentsComponent.CODEC);
    public static final ComponentType<Integer> ESSENCE_CHARGE = register("essence_charge", Codec.intRange(0, 100));
    public static final ComponentType<Integer> SOUL_CHARGES = register("soul_charges", Codec.intRange(0, 100));
    public static final ComponentType<Boolean> SOULBOUND = register("soulbound", Codec.BOOL);
    public static final ComponentType<PipeDirection> PIPE_DIRECTION = register("pipe_direction", PipeDirection.CODEC);
    public static final ComponentType<PipeValveMode> PIPE_VALVE = register("pipe_valve", PipeValveMode.CODEC);
    public static final ComponentType<EssenceType> PIPE_FILTER = register("pipe_filter", EssenceType.CODEC);
    public static final ComponentType<Float> ESSENCE_STABILITY = register("essence_stability", Codec.floatRange(0.0f, 1.0f));
    public static final ComponentType<Integer> SOLVENT_CHARGES = register("solvent_charges", Codec.intRange(0, 32));
    public static final ComponentType<Float> SHIELD_POINTS = register("shield_points", Codec.floatRange(0.0f, 6.0f));
    public static final ComponentType<Integer> SHIELD_DELAY = register("shield_delay", Codec.intRange(0, 240));
    public static final ComponentType<Integer> SHIELD_REGEN_TICKS = register("shield_regen_ticks", Codec.intRange(0, 9));
    public static final ComponentType<Integer> RING_ACTIVE_TICKS = register("ring_active_ticks", Codec.intRange(0, 2399));
    public static final ComponentType<Integer> AMULET_USES_STEP = register("amulet_uses_step", Codec.intRange(0, 4));

    private static <T> ComponentType<T> register(String name, Codec<T> codec) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Alkahest.id(name),
            ComponentType.<T>builder().codec(codec).packetCodec(PacketCodecs.codec(codec)).build());
    }

    public static void register() { }
    private ModDataComponents() { }
}
