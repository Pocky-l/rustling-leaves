package com.pockyl.rustling_leaves.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.Platform;

public final class NeoForgePlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isModLoaded(String modId, String minVersion) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().compareTo(new DefaultArtifactVersion(minVersion)) >= 0)
                .orElse(false);
    }

    @Override
    public boolean canSendRake(ServerPlayer player) {
        return NeoForgeNetwork.canSend(player);
    }

    @Override
    public void sendRake(ServerPlayer player, RakePayload payload) {
        NeoForgeNetwork.send(player, payload);
    }
}
