package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** The independent shield's current HP; -1 means no shield ring is equipped. */
public record ShieldPayload(float points, int max, int charge) implements CustomPayload {
    public static final Id<ShieldPayload> ID = new Id<>(Alkahest.id("shield"));
    public static final PacketCodec<RegistryByteBuf, ShieldPayload> CODEC =
        PacketCodec.tuple(PacketCodecs.FLOAT, ShieldPayload::points, PacketCodecs.VAR_INT, ShieldPayload::max, PacketCodecs.VAR_INT, ShieldPayload::charge, ShieldPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
