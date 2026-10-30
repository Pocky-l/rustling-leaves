package com.pockyl.rustling_leaves.forge;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.client.model.data.ModelData;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.ClientPlatform;

public final class ForgeClientPlatform implements ClientPlatform {
    @Override
    public RenderType createRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
            boolean sortOnUpload, Object state) {
        return RenderType.create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, (RenderType.CompositeState) state);
    }

    @Override
    public TextureAtlasSprite particleIcon(BakedModel model) {
        return model.getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public int pixelRGBA(TextureAtlasSprite sprite, int x, int y) {
        return sprite.getPixelRGBA(0, x, y);
    }

    @Override
    public boolean serverHasRake() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNetwork.present(connection.getConnection());
    }

    @Override
    public void sendRakeToServer(RakePayload payload) {
        ForgeNetwork.CHANNEL.sendToServer(payload);
    }
}
