package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Non-private shield state for the player and clients tracking that player. */
public record ShieldVisualPayload(int entityId, float points, int max) implements CustomPayload {
    public static final Id<ShieldVisualPayload> ID = new Id<>(Alkahest.id("shield_visual"));
    public static final PacketCodec<RegistryByteBuf, ShieldVisualPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_INT, ShieldVisualPayload::entityId, PacketCodecs.FLOAT, ShieldVisualPayload::points,
        PacketCodecs.VAR_INT, ShieldVisualPayload::max, ShieldVisualPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
