package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Server sends only the clicked core; the client computes a temporary, local visual guide. */
public record AssemblyPreviewPayload(long core) implements CustomPayload {
    public static final Id<AssemblyPreviewPayload> ID = new Id<>(Alkahest.id("assembly_preview"));
    public static final PacketCodec<RegistryByteBuf, AssemblyPreviewPayload> CODEC =
        PacketCodec.tuple(PacketCodecs.VAR_LONG, AssemblyPreviewPayload::core, AssemblyPreviewPayload::new);
    public BlockPos pos() { return BlockPos.fromLong(core); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
