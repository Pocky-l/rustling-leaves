package com.pockyl.rustling_leaves.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSimulation;

/**
 * Draws the leaves in two parts:
 * <ul>
 *   <li>resting leaves are baked into one static GPU buffer per 16x16x16 section, rebuilt only when a leaf lands in or
 *   leaves that section (a few per frame at most) - so tens of thousands of lying leaves cost a handful of draw calls;</li>
 *   <li>moving leaves (falling, floating, fading) are written into one streaming buffer each frame, interpolated
 *   between ticks for smooth motion at any frame rate.</li>
 * </ul>
 * Both use the vanilla cutout terrain shader, so fog, lightmap and day/night match the world.
 */
final class LeafRenderer implements AutoCloseable {
    private static final RenderType LEAVES = RenderType.create(RustlingLeaves.MOD_ID + "_leaves", DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS, 1 << 16, false, false, RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_CUTOUT_MIPPED_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    private static final int REBUILDS_PER_FRAME = 8;

    private final LeafSimulation simulation;
    private final LeafPool pool;
    private final long[] sectionOf;
    private final int[] slotInSection;
    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 18);
    private final VertexBuffer moving = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    private boolean movingEmpty = true;
    private final float[] u0 = new float[LeafKind.SPRITES.length];
    private final float[] v0 = new float[LeafKind.SPRITES.length];
    private final float[] u1 = new float[LeafKind.SPRITES.length];
    private final float[] v1 = new float[LeafKind.SPRITES.length];
    private int relightCursor;

    LeafRenderer(LeafSimulation simulation) {
        this.simulation = simulation;
        this.pool = simulation.pool();
        sectionOf = new long[pool.capacity];
        slotInSection = new int[pool.capacity];
    }

    // ------------------------------------------------------------------------------------------------------------
    // Section bookkeeping (called by the simulation)
    // ------------------------------------------------------------------------------------------------------------

    void onRest(int leaf) {
        long key = SectionPos.asLong(SectionPos.blockToSectionCoord(Mth.floor(pool.x[leaf])),
                SectionPos.blockToSectionCoord(Mth.floor(pool.y[leaf])), SectionPos.blockToSectionCoord(Mth.floor(pool.z[leaf])));
        Section section = sections.get(key);
        if (section == null) {
            section = new Section(key);
            sections.put(key, section);
        }
        sectionOf[leaf] = key;
        slotInSection[leaf] = section.leaves.size();
        section.leaves.add(leaf);
        section.dirty = true;
    }

    void onUnrest(int leaf) {
        Section section = sections.get(sectionOf[leaf]);
        if (section == null) {
            return;
        }
        IntArrayList leaves = section.leaves;
        int slot = slotInSection[leaf];
        int last = leaves.getInt(leaves.size() - 1);
        leaves.set(slot, last);
        slotInSection[last] = slot;
        leaves.removeInt(leaves.size() - 1);
        if (leaves.isEmpty()) {
            section.close();
            sections.remove(section.key);
        } else {
            section.dirty = true;
        }
    }

    /** A block changed: rebuild nearby sections so resting leaves pick up the new light. */
    void onBlockChanged(BlockPos pos) {
        int sx = SectionPos.blockToSectionCoord(pos.getX());
        int sy = SectionPos.blockToSectionCoord(pos.getY());
        int sz = SectionPos.blockToSectionCoord(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Section section = sections.get(SectionPos.asLong(sx + dx, sy + dy, sz + dz));
                    if (section != null) {
                        section.dirty = true;
                    }
                }
            }
        }
    }

    /** Re-lights one section per tick, so light changes far from any block update (sky light) still arrive. */
    void tick() {
        if (sections.isEmpty()) {
            return;
        }
        int target = relightCursor++ % sections.size();
        int n = 0;
        for (Section section : sections.values()) {
            if (n++ == target) {
                section.dirty = true;
                break;
            }
        }
    }

    int sectionCount() {
        return sections.size();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------------------------

    void render(Camera camera, Frustum frustum, Matrix4f modelView, Matrix4f projection, float partialTick) {
        updateSprites();
        Vec3 cam = camera.getPosition();
        rebuildDirtySections();
        buildMoving(cam, camera.getLookVector(), partialTick);

        LEAVES.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            LEAVES.clearRenderState();
            return;
        }
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        Uniform offset = shader.CHUNK_OFFSET;
        for (Section section : sections.values()) {
            if (section.buffer == null || section.empty || !frustum.isVisible(section.bounds)) {
                continue;
            }
            if (offset != null) {
                offset.set((float) (section.originX - cam.x), (float) (section.originY - cam.y), (float) (section.originZ - cam.z));
                offset.upload();
            }
            section.buffer.bind();
            section.buffer.draw();
        }
        if (!movingEmpty) {
            if (offset != null) {
                offset.set(0.0F, 0.0F, 0.0F);
                offset.upload();
            }
            moving.bind();
            moving.draw();
        }
        if (offset != null) {
            offset.set(0.0F, 0.0F, 0.0F);
        }
        VertexBuffer.unbind();
        shader.clear();
        LEAVES.clearRenderState();
    }

    private void updateSprites() {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (int s = 0; s < LeafKind.SPRITES.length; s++) {
            TextureAtlasSprite sprite = atlas.getSprite(LeafKind.SPRITES[s]);
            u0[s] = sprite.getU0();
            v0[s] = sprite.getV0();
            u1[s] = sprite.getU1();
            v1[s] = sprite.getV1();
        }
    }

    private void rebuildDirtySections() {
        int budget = REBUILDS_PER_FRAME;
        for (Section section : sections.values()) {
            if (!section.dirty) {
                continue;
            }
            if (budget-- == 0) {
                break;
            }
            section.dirty = false;
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            IntArrayList leaves = section.leaves;
            for (int n = 0; n < leaves.size(); n++) {
                int i = leaves.getInt(n);
                int light = simulation.lightAt(pool.x[i], pool.y[i] + 0.1, pool.z[i]);
                pool.light[i] = light;
                emit(builder, (float) (pool.x[i] - section.originX), (float) (pool.y[i] - section.originY),
                        (float) (pool.z[i] - section.originZ), pool.yaw[i], pool.pitch[i], pool.roll[i], pool.size[i], pool.color[i],
                        light, pool.sprite[i]);
            }
            MeshData mesh = builder.build();
            if (mesh == null) {
                section.empty = true;
                continue;
            }
            if (section.buffer == null) {
                section.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            section.buffer.bind();
            section.buffer.upload(mesh);
            section.empty = false;
        }
        VertexBuffer.unbind();
    }

    private void buildMoving(Vec3 cam, Vector3f look, float partialTick) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (int i = 0, highWater = pool.highWater(); i < highWater; i++) {
            byte state = pool.state[i];
            if (state == LeafPool.FREE || state == LeafPool.RESTING) {
                continue;
            }
            float x = (float) (Mth.lerp(partialTick, pool.prevX[i], pool.x[i]) - cam.x);
            float y = (float) (Mth.lerp(partialTick, pool.prevY[i], pool.y[i]) - cam.y);
            float z = (float) (Mth.lerp(partialTick, pool.prevZ[i], pool.z[i]) - cam.z);
            if (x * look.x() + y * look.y() + z * look.z() < -0.5F) {
                continue;
            }
            float size = pool.size[i];
            if (state == LeafPool.DYING) {
                size *= Math.max(0.0F, (pool.life[i] - partialTick) / LeafSimulation.DYING_TICKS);
            }
            emit(builder, x, y, z, Mth.lerp(partialTick, pool.prevYaw[i], pool.yaw[i]),
                    Mth.lerp(partialTick, pool.prevPitch[i], pool.pitch[i]), Mth.lerp(partialTick, pool.prevRoll[i], pool.roll[i]),
                    size, pool.color[i], pool.light[i], pool.sprite[i]);
        }
        MeshData mesh = builder.build();
        movingEmpty = mesh == null;
        if (mesh != null) {
            moving.bind();
            moving.upload(mesh);
            VertexBuffer.unbind();
        }
    }

    /**
     * Writes one leaf as a quad rotated by yaw (Y), pitch (X) and roll (Z), shaded by its normal like vanilla block
     * faces (both faces of a leaf look the same).
     */
    private void emit(BufferBuilder builder, float x, float y, float z, float yaw, float pitch, float roll, float half, int rgb,
            int light, int sprite) {
        float sa = Mth.sin(yaw);
        float ca = Mth.cos(yaw);
        float sb = Mth.sin(pitch);
        float cb = Mth.cos(pitch);
        float sc = Mth.sin(roll);
        float cc = Mth.cos(roll);
        float ax = (ca * cc + sa * sb * sc) * half;
        float ay = cb * sc * half;
        float az = (-sa * cc + ca * sb * sc) * half;
        float bx = sa * cb * half;
        float by = -sb * half;
        float bz = ca * cb * half;
        float nx = -ca * sc + sa * sb * cc;
        float ny = cb * cc;
        float nz = sa * sc + ca * sb * cc;
        float shade = nx * nx * 0.6F + ny * ny + nz * nz * 0.8F;
        int r = (int) ((rgb >> 16 & 0xFF) * shade);
        int g = (int) ((rgb >> 8 & 0xFF) * shade);
        int b = (int) ((rgb & 0xFF) * shade);
        int color = 0xFF000000 | r << 16 | g << 8 | b;
        int overlay = OverlayTexture.NO_OVERLAY;
        builder.addVertex(x - ax - bx, y - ay - by, z - az - bz, color, u0[sprite], v0[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x + ax - bx, y + ay - by, z + az - bz, color, u1[sprite], v0[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x + ax + bx, y + ay + by, z + az + bz, color, u1[sprite], v1[sprite], overlay, light, nx, ny, nz);
        builder.addVertex(x - ax + bx, y - ay + by, z - az + bz, color, u0[sprite], v1[sprite], overlay, light, nx, ny, nz);
    }

    @Override
    public void close() {
        for (Section section : sections.values()) {
            section.close();
        }
        sections.clear();
        moving.close();
        bytes.close();
    }

    private static final class Section {
        final long key;
        final int originX;
        final int originY;
        final int originZ;
        final AABB bounds;
        final IntArrayList leaves = new IntArrayList();
        VertexBuffer buffer;
        boolean dirty = true;
        boolean empty = true;

        Section(long key) {
            this.key = key;
            originX = SectionPos.sectionToBlockCoord(SectionPos.x(key));
            originY = SectionPos.sectionToBlockCoord(SectionPos.y(key));
            originZ = SectionPos.sectionToBlockCoord(SectionPos.z(key));
            bounds = new AABB(originX, originY, originZ, originX + 16, originY + 16, originZ + 16).inflate(0.5);
        }

        void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }
    }
}
