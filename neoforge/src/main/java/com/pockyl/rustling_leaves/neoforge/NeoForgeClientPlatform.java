package com.pockyl.rustling_leaves.neoforge;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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
    public int pixelABGR(TextureAtlasSprite sprite, int x, int y) {
        return ARGB.toABGR(sprite.getPixelARGB(0, x, y));
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
