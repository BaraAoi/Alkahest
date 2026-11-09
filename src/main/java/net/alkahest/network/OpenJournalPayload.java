package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record OpenJournalPayload(int flags) implements CustomPayload {
    public static final Id<OpenJournalPayload> ID = new Id<>(Alkahest.id("open_journal"));
    public static final PacketCodec<RegistryByteBuf, OpenJournalPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, OpenJournalPayload::flags, OpenJournalPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
