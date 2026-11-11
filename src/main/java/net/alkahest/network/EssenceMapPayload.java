package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** A bounded chunk of a full, server-authoritative item composition snapshot. */
public record EssenceMapPayload(String chunk) implements CustomPayload {
    public static final Id<EssenceMapPayload> ID = new Id<>(Alkahest.id("essence_map"));
    public static final PacketCodec<RegistryByteBuf, EssenceMapPayload> CODEC =
        PacketCodec.tuple(PacketCodecs.STRING, EssenceMapPayload::chunk, EssenceMapPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
