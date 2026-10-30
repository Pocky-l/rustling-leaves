package com.pockyl.rustling_leaves.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.Platform;

public final class ForgePlatform implements Platform {
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
    public double blockReach(Player player) {
        return player.getBlockReach();
    }

    @Override
    public boolean canSendRake(ServerPlayer player) {
        return ForgeNetwork.present(player.connection.connection);
    }

    @Override
    public void sendRake(ServerPlayer player, RakePayload payload) {
        ForgeNetwork.send(player, payload);
    }
}
