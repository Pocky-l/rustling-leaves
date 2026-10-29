package com.pockyl.rustling_leaves.client.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.sim.LeafShape;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/**
 * Tells a shader pack of <a href="https://modrinth.com/mod/iris">Iris</a> that the leaves it draws are leaves. A pack
 * picks its material by the block id ({@code mc_Entity}) Iris writes into every terrain vertex; for quads outside the
 * chunk meshes Iris writes "no block", so packs light falling leaves like plain terrain: no light shining through them
 * against the sun, no foliage shading. Iris fills the id from the block its buffer builder was told about
 * ({@code BlockSensitiveBufferBuilder.beginBlock}, the call its own chunk meshing makes), and the pack's id of every
 * block state is in {@code WorldRenderingSettings.getBlockStateIds()}. Both are reached by reflection, so Iris is not
 * needed to build or run; any failure turns this off for the session.
 */
public final class IrisBlocks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The vanilla leaves standing in for each {@link LeafShape}, in its order: packs map every vanilla tree. */
    private static final BlockState[] LEAVES = {
            Blocks.OAK_LEAVES.defaultBlockState(),
            Blocks.BIRCH_LEAVES.defaultBlockState(),
            Blocks.SPRUCE_LEAVES.defaultBlockState(),
            Blocks.CHERRY_LEAVES.defaultBlockState(),
    };
    private static final MethodHandle BEGIN;
    private static final MethodHandle END;
    private static final MethodHandle BLOCK_IDS;

    private static boolean broken;
    private static Object lastMap;
    private static final int[] IDS = new int[LEAVES.length];

    static {
        MethodHandle begin = null;
        MethodHandle end = null;
        MethodHandle blockIds = null;
        if (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus")) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> sensitive = Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
                begin = lookup.findVirtual(sensitive, "beginBlock", MethodType.methodType(void.class, int.class, byte.class,
                                byte.class, int.class, int.class, int.class))
                        .asType(MethodType.methodType(void.class, BufferBuilder.class, int.class, byte.class, byte.class,
                                int.class, int.class, int.class));
                end = lookup.findVirtual(sensitive, "endBlock", MethodType.methodType(void.class))
                        .asType(MethodType.methodType(void.class, BufferBuilder.class));
                Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                Object instance = settings.getField("INSTANCE").get(null);
                blockIds = lookup.findVirtual(settings, "getBlockStateIds", MethodType.methodType(Object2IntMap.class))
                        .bindTo(instance).asType(MethodType.methodType(Object.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.info("Shader packs will see the leaves as plain terrain: {}", e.toString());
                begin = null;
            }
        }
        BEGIN = begin;
        END = end;
        BLOCK_IDS = blockIds;
        Arrays.fill(IDS, -1);
    }

    private IrisBlocks() {
    }

    /**
     * The active pack's block id of the leaves of every {@link LeafShape} (-1 where the pack has none), or null when
     * leaves cannot be tagged. Looked up again when the pack changes; call once per mesh.
     */
    public static int[] ids() {
        if (BEGIN == null || broken) {
            return null;
        }
        try {
            Object map = (Object) BLOCK_IDS.invokeExact();
            if (map == null) {
                return null;
            }
            if (map != lastMap) {
                lastMap = map;
                @SuppressWarnings("unchecked")
                Object2IntMap<BlockState> byState = (Object2IntMap<BlockState>) map;
                for (int i = 0; i < LEAVES.length; i++) {
                    IDS[i] = byState.getOrDefault(LEAVES[i], -1);
                }
            }
            return IDS;
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    /**
     * The quads written next to {@code builder} belong to a block with the pack's id {@code id} at block position
     * (x, y, z) in the builder's coordinates (Iris derives the vertices' offset to the block centre from it).
     */
    public static void begin(BufferBuilder builder, int id, int x, int y, int z) {
        if (broken) {
            return;
        }
        try {
            BEGIN.invokeExact(builder, id, (byte) 0, (byte) 0, x, y, z);
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Back to "no block" for whatever is written next. */
    public static void end(BufferBuilder builder) {
        if (broken) {
            return;
        }
        try {
            END.invokeExact(builder);
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        if (!broken) {
            broken = true;
            LOGGER.warn("Stopped telling shader packs which block the leaves are", e);
        }
    }
}
