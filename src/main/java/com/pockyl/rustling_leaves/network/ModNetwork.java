package com.pockyl.rustling_leaves.network;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafManager;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    // Optional on both sides: the client part of the mod also works on servers without it (no items there then).
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(RustlingLeaves.id("main"), () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION), NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(BagCollectPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BagCollectPayload::encode)
                .decoder(BagCollectPayload::decode)
                .consumerMainThread(BagCollectPayload::handle)
                .add();
        CHANNEL.messageBuilder(WindSpellPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WindSpellPayload::encode)
                .decoder(WindSpellPayload::decode)
                .consumerMainThread(ModNetwork::handleWindSpell)
                .add();
    }

    // Client-bound handlers live in client code; the lambda only resolves it when a packet arrives on a client.
    private static void handleWindSpell(WindSpellPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LeafManager.onWindSpell(payload));
    }
}
