package com.pockyl.rustling_leaves.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.rustling_leaves.client.LeafManager;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional: the client part of the mod also works on servers without it (no items there then).
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(BagCollectPayload.TYPE, BagCollectPayload.STREAM_CODEC, BagCollectPayload::handle);
        // Client-bound handlers live in client code; the lambda only resolves it when a packet arrives on a client.
        registrar.playToClient(WindSpellPayload.TYPE, WindSpellPayload.STREAM_CODEC, (payload, context) -> LeafManager.onWindSpell(payload));
    }
}
