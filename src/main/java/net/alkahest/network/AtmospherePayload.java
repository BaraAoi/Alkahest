package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Periodic server-authoritative reading for an equipped monocle. */
public record AtmospherePayload(String data) implements CustomPayload {
    public static final Id<AtmospherePayload> ID = new Id<>(Alkahest.id("atmosphere_reading"));
    public static final PacketCodec<RegistryByteBuf, AtmospherePayload> CODEC =
        PacketCodec.tuple(PacketCodecs.STRING, AtmospherePayload::data, AtmospherePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
