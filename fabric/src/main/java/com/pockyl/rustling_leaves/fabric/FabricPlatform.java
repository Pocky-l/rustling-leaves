package com.pockyl.rustling_leaves.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.Platform;

public final class FabricPlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isModLoaded(String modId, String minVersion) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> {
            try {
                return container.getMetadata().getVersion().compareTo(Version.parse(minVersion)) >= 0;
            } catch (VersionParsingException e) {
                return false;
            }
        }).orElse(false);
    }

    /** Vanilla's reach: this version has no reach attribute. */
    @Override
    public double blockReach(Player player) {
        return player.isCreative() ? 5.0 : 4.5;
    }

    @Override
    public boolean canSendRake(ServerPlayer player) {
        return FabricNetwork.canSend(player);
    }

    @Override
    public void sendRake(ServerPlayer player, RakePayload payload) {
        FabricNetwork.send(player, payload);
    }
}
