package net.alkahest.registry;

import net.alkahest.network.OpenJournalPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class ModPayloads {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenJournalPayload.ID, OpenJournalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.alkahest.network.ShieldPayload.ID, net.alkahest.network.ShieldPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.alkahest.network.ShieldVisualPayload.ID, net.alkahest.network.ShieldVisualPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.alkahest.network.AssemblyPreviewPayload.ID, net.alkahest.network.AssemblyPreviewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.alkahest.network.EssenceMapPayload.ID, net.alkahest.network.EssenceMapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(net.alkahest.network.AtmospherePayload.ID, net.alkahest.network.AtmospherePayload.CODEC);
    }
    private ModPayloads() { }
}
