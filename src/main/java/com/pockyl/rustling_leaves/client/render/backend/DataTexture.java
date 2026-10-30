package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * The texture of {@link com.pockyl.rustling_leaves.client.compat.ShaderData}: one row of RGBA32F texels, registered in
 * the texture manager, where Iris looks it up on every bind. Render thread only. On 26.1.2 vanilla's textures have no
 * float format and the game draws with OpenGL only, so this is a raw GL texture wrapped in a GL texture of the game
 * (Iris binds it by its GL id; the format the wrapper claims is a placeholder, nothing samples it through vanilla).
 */
public final class DataTexture extends AbstractTexture {
    private static final int[] UNPACK_PARAMETERS = {GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
            GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_ALIGNMENT};
    private static final int[] UNPACK_DEFAULTS = {0, 0, 0, 4};

    private final int width;
    private final int glId;
    private final FloatBuffer buffer;
    private boolean freed;

    private DataTexture(String label, int width) {
        RenderSystem.assertOnRenderThread();
        this.width = width;
        this.buffer = MemoryUtil.memAllocFloat(width * 4);
        glId = GlStateManager._genTexture();
        GlStateManager._bindTexture(glId);
        // One level only: complete for any sampler a pack binds with it, also a mipmapping one.
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        int[] saved = cleanUnpackState();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, width, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
        restoreUnpackState(saved);
        // GL textures of the game have no public constructor; the wrapper owns the GL texture and deletes it on close.
        texture = new GlTexture(GpuTexture.USAGE_TEXTURE_BINDING, label, TextureFormat.RGBA8, width, 1, 1, 1, glId) {
        };
        textureView = RenderSystem.getDevice().createTextureView(texture);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
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
        buffer.put(values, 0, width * 4).flip();
        GlStateManager._bindTexture(glId);
        int[] saved = cleanUnpackState();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, 1, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
        restoreUnpackState(saved);
    }

    /**
     * Uploads read client memory with the current unpack state, which other renderers leave behind: a pixel buffer
     * still bound turns the pointer into an offset into it, a row length or skip of a large image made the driver
     * read far past this small buffer (a native crash in the driver). Clean state for the upload, the old one put
     * back.
     */
    private static int[] cleanUnpackState() {
        int[] saved = new int[UNPACK_PARAMETERS.length + 1];
        for (int i = 0; i < UNPACK_PARAMETERS.length; i++) {
            saved[i] = GL11.glGetInteger(UNPACK_PARAMETERS[i]);
            GlStateManager._pixelStore(UNPACK_PARAMETERS[i], UNPACK_DEFAULTS[i]);
        }
        saved[UNPACK_PARAMETERS.length] = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (saved[UNPACK_PARAMETERS.length] != 0) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        return saved;
    }

    private static void restoreUnpackState(int[] saved) {
        for (int i = 0; i < UNPACK_PARAMETERS.length; i++) {
            GlStateManager._pixelStore(UNPACK_PARAMETERS[i], saved[i]);
        }
        if (saved[UNPACK_PARAMETERS.length] != 0) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, saved[UNPACK_PARAMETERS.length]);
        }
    }

    @Override
    public void close() {
        // Closing the wrapper (with its view) deletes the GL texture.
        super.close();
        if (!freed) {
            freed = true;
            MemoryUtil.memFree(buffer);
        }
    }
}
