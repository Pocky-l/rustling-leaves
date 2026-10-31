package com.pockyl.rustling_leaves.fabric;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.ClientPlatform;

/** {@code RenderPipelines.BLOCK_SNIPPET} and {@code SpriteContents.originalImage} are opened by the access widener. */
public final class FabricClientPlatform implements ClientPlatform {
    @Override
    public RenderPipeline.Builder cutoutBlockPipeline() {
        // RenderPipelines.CUTOUT_BLOCK, which vanilla builds just like this.
        return RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET).withShaderDefine("ALPHA_CUTOUT", 0.5F);
    }

    @Override
    public RenderPipeline.Snippet blockSnippet() {
        return RenderPipelines.BLOCK_SNIPPET;
    }

    @Override
    public GpuTexture externalTexture(@GpuTexture.Usage int usage, String label, TextureFormat format, int width, int height, int glId) {
        return new ExternalGlTexture(usage, label, format, width, height, glId);
    }

    @Override
    public int pixelABGR(TextureAtlasSprite sprite, int x, int y) {
        return ARGB.toABGR(sprite.contents().originalImage.getPixel(x, y));
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
