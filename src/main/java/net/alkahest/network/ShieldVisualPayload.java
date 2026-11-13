package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** A transient impact or cancellation, visible to the wearer and tracking players. */
public record ShieldVisualPayload(int entityId, boolean impact) implements CustomPayload {
    public static final Id<ShieldVisualPayload> ID = new Id<>(Alkahest.id("shield_visual"));
    public static final PacketCodec<RegistryByteBuf, ShieldVisualPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_INT, ShieldVisualPayload::entityId,
        PacketCodecs.BOOL, ShieldVisualPayload::impact, ShieldVisualPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
