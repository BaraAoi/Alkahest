package net.alkahest.network;

import net.alkahest.Alkahest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record OpenJournalPayload() implements CustomPayload {
    public static final Id<OpenJournalPayload> ID = new Id<>(Alkahest.id("open_journal"));
    public static final PacketCodec<RegistryByteBuf, OpenJournalPayload> CODEC = PacketCodec.unit(new OpenJournalPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
