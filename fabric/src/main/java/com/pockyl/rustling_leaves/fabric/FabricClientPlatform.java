package com.pockyl.rustling_leaves.fabric;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.ClientPlatform;

/** {@code RenderType.create} and {@code SpriteContents.originalImage} are opened by the access widener. */
public final class FabricClientPlatform implements ClientPlatform {
    @Override
    public RenderType createRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
            boolean sortOnUpload, Object state) {
        return RenderType.create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, (RenderType.CompositeState) state);
    }

    @Override
    public TextureAtlasSprite particleIcon(BakedModel model) {
        return model.getParticleIcon();
    }

    @Override
    public int pixelRGBA(TextureAtlasSprite sprite, int x, int y) {
        return sprite.contents().originalImage.getPixelRGBA(x, y);
    }

    @Override
    public boolean serverHasRake() {
        return FabricNetwork.serverHasRake();
    }

    @Override
    public void sendRakeToServer(RakePayload payload) {
        FabricNetwork.sendToServer(payload);
    }
}
