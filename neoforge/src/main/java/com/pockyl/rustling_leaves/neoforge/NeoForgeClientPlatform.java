package com.pockyl.rustling_leaves.neoforge;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;

import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.platform.ClientPlatform;

public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public RenderPipeline.Builder cutoutBlockPipeline() {
        return RenderPipelines.CUTOUT_BLOCK.toBuilder();
    }

    @Override
    public RenderPipeline.Snippet blockSnippet() {
        return RenderPipelines.BLOCK_SNIPPET;
    }

    @Override
    public GpuTexture externalTexture(@GpuTexture.Usage int usage, String label, TextureFormat format, int width, int height, int glId) {
        return ((GlDevice) RenderSystem.getDevice()).createExternalTexture(usage, label, format, width, height, 1, 1, glId);
    }

    /** Despite its name NeoForge's getPixelRGBA hands out ARGB on 1.21.11: it reads vanilla's NativeImage.getPixel. */
    @Override
    public int pixelABGR(TextureAtlasSprite sprite, int x, int y) {
        return ARGB.toABGR(sprite.getPixelRGBA(0, x, y));
    }

    @Override
    public boolean serverHasRake() {
        return NeoForgeNetwork.serverHasRake();
    }

    @Override
    public void sendRakeToServer(RakePayload payload) {
        NeoForgeNetwork.sendToServer(payload);
    }
}
