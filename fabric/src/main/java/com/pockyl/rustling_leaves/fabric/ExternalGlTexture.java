package com.pockyl.rustling_leaves.fabric;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

/**
 * A GPU texture for a GL texture made by hand, like NeoForge's external textures: closing it (or the last view of it)
 * leaves the GL texture alone, its maker deletes it. Vanilla has no such texture; its constructor is open to
 * subclasses.
 */
final class ExternalGlTexture extends GlTexture {
    ExternalGlTexture(@GpuTexture.Usage int usage, String label, TextureFormat format, int width, int height, int glId) {
        super(usage, label, format, width, height, 1, 1, glId);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void addViews() {
    }

    @Override
    public void removeViews() {
    }
}
