package net.alkahest.registry;

import net.alkahest.network.OpenJournalPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class ModPayloads {
    public static void register() { PayloadTypeRegistry.playS2C().register(OpenJournalPayload.ID, OpenJournalPayload.CODEC); }
    private ModPayloads() { }
}
