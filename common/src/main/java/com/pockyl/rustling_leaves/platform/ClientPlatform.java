package com.pockyl.rustling_leaves.platform;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;

import com.pockyl.rustling_leaves.network.RakePayload;

/** What the client code needs from the mod loader; only loaded on clients. */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Platform.load(ClientPlatform.class);

    /**
     * {@code RenderType.create}, private in vanilla: Forge makes it public, Fabric through the access widener. {@code state}
     * is a {@code RenderType.CompositeState}, a protected class that only subclasses of RenderType can name.
     */
    RenderType createRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
            boolean sortOnUpload, Object state);

    /** The particle texture of a block model, as the loader resolves it. */
    TextureAtlasSprite particleIcon(BakedModel model);

    /** A pixel of the first frame of a sprite, in ABGR. */
    int pixelRGBA(TextureAtlasSprite sprite, int x, int y);

    /** Whether the server we are connected to has the rake channel. */
    boolean serverHasRake();

    void sendRakeToServer(RakePayload payload);
}
