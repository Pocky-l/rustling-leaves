package com.pockyl.rustling_leaves.platform;

import net.minecraft.server.level.ServerPlayer;

import com.pockyl.rustling_leaves.network.RakePayload;

import java.util.ServiceLoader;

/**
 * What the common code needs from the mod loader, on both sides. Each loader project provides one implementation,
 * listed in its {@code META-INF/services}.
 */
public interface Platform {
    Platform INSTANCE = load(Platform.class);

    boolean isModLoaded(String modId);

    /** Whether a mod is installed in at least this version. */
    boolean isModLoaded(String modId, String minVersion);

    /** Whether the player's client has the rake channel. */
    boolean canSendRake(ServerPlayer player);

    void sendRake(ServerPlayer player, RakePayload payload);

    static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, type.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getName() + " for this mod loader"));
    }
}
