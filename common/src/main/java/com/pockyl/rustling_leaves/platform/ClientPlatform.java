package com.pockyl.rustling_leaves.platform;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import com.pockyl.rustling_leaves.network.RakePayload;

/** What the client code needs from the mod loader; only loaded on clients. */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Platform.load(ClientPlatform.class);

    /**
     * A builder that starts out as vanilla's cutout block pipeline ({@code RenderPipelines.CUTOUT_BLOCK}): NeoForge's
     * {@code toBuilder}, which Iris follows by copying the pack's programs of the original; on Fabric the same pipeline
     * built from the block snippet.
     */
    RenderPipeline.Builder cutoutBlockPipeline();

    /** {@code RenderPipelines.BLOCK_SNIPPET}, private in vanilla: NeoForge makes it public, Fabric through the access widener. */
    RenderPipeline.Snippet blockSnippet();

    /** A pixel of the first frame of a sprite, in ABGR. */
    int pixelABGR(TextureAtlasSprite sprite, int x, int y);

    /** Whether the server we are connected to has the rake channel. */
    boolean serverHasRake();

    void sendRakeToServer(RakePayload payload);
}
