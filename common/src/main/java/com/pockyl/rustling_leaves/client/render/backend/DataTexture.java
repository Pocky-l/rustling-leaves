package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The texture of {@link com.pockyl.rustling_leaves.client.compat.ShaderData}: one row of RGBA32F texels, registered in
 * the texture manager, where Iris looks it up on every bind. Render thread only. Since 26.2 vanilla's textures have a
 * float format, so this is an ordinary GPU texture written through the command encoder: no GL calls of its own, it
 * works on the Vulkan backend too (Iris, which binds it by its GL id, only runs on OpenGL).
 */
public final class DataTexture extends AbstractTexture {
    private final int width;
    private final ByteBuffer buffer;
    private boolean freed;

    private DataTexture(String label, int width) {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        this.width = width;
        this.buffer = MemoryUtil.memAlloc(width * 4 * Float.BYTES);
        // One level only: complete for any sampler a pack binds with it, also a mipmapping one.
        texture = device.createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA32_FLOAT, width, 1, 1, 1);
        textureView = device.createTextureView(texture);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        upload(new float[width * 4]);
    }

    /** Creates a texture of {@code width} texels (all zero) and registers it as {@code id}. */
    public static DataTexture register(Identifier id, int width) {
        DataTexture texture = new DataTexture(id.toString(), width);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return texture;
    }

    /** Replaces the texels: {@code width * 4} floats, RGBA per texel. */
    public void upload(float[] values) {
        if (freed) {
            return;
        }
        buffer.clear();
        buffer.asFloatBuffer().put(values, 0, width * 4);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(getTexture(), buffer, 0, 0, 0, 0, width, 1);
    }

    @Override
    public void close() {
        super.close();
        if (!freed) {
            freed = true;
            MemoryUtil.memFree(buffer);
        }
    }
}
