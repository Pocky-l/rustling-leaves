package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The texture of {@link com.pockyl.rustling_leaves.client.compat.ShaderData}: one row of RGBA32F texels, registered in
 * the texture manager, where Iris looks it up on every bind. Render thread only. Since 26.2 vanilla's textures have a
 * float format, so this is an ordinary GPU texture of the device (OpenGL or Vulkan, no raw GL calls). The texels go
 * through a small copy buffer: the device's direct texture write sends bytes as unsigned bytes on OpenGL, the copy from
 * a buffer passes the floats as floats.
 */
public final class DataTexture extends AbstractTexture {
    private final int width;
    private final GpuBuffer staging;
    private final ByteBuffer bytes;
    private boolean freed;

    private DataTexture(String label, int width) {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        this.width = width;
        texture = device.createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA32_FLOAT, width, 1, 1, 1);
        textureView = device.createTextureView(texture);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        staging = device.createBuffer(() -> label + " upload", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC,
                (long) width * GpuFormat.RGBA32_FLOAT.blockSize());
        bytes = MemoryUtil.memAlloc(width * GpuFormat.RGBA32_FLOAT.blockSize());
        // A new texture holds undefined texels: all zero until the first frame with a pack.
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
        bytes.clear();
        bytes.asFloatBuffer().put(values, 0, width * 4);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(staging.slice(), bytes);
        encoder.copyBufferToTexture(staging.slice(), 0, 0, width, 1, texture, 0, 0, width, 1, 0, 0);
    }

    @Override
    public void close() {
        super.close();
        if (!freed) {
            freed = true;
            staging.close();
            MemoryUtil.memFree(bytes);
        }
    }
}
