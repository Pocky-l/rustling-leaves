package com.pockyl.rustling_leaves.client.compat;

import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.platform.Platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shader packs of <a href="https://modrinth.com/mod/iris">Iris</a>, asked through Iris' public API by reflection, so
 * Iris is not needed to build or run the mod.
 * <p>
 * The leaves are drawn with render pipelines made from vanilla's block pipelines; Iris draws a pipeline with the
 * pack's program it was told about ({@link #assignProgram}). What else changes with a pack is the vertex layout of the
 * meshes (see {@code LeafRenderer}).
 */
public final class ShaderPacks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodHandle ASSIGN_PIPELINE;
    private static final MethodHandle IN_USE;
    private static final MethodHandle SUN_PATH_ROTATION;
    private static boolean sunPathBroken;

    static {
        MethodHandle assign = null;
        MethodHandle inUse = null;
        MethodHandle sunPath = null;
        if (Platform.INSTANCE.isModLoaded("iris")) {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object api = null;
            Class<?> type = null;
            try {
                type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = lookup.findVirtual(type, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(api);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.info("Iris' API is not reachable, shader packs are not recognised: {}", e.toString());
                api = null;
                inUse = null;
            }
            if (api != null) {
                try {
                    assign = lookup.findVirtual(type, "assignPipeline", MethodType.methodType(void.class, RenderPipeline.class,
                                    Class.forName("net.irisshaders.iris.api.v0.IrisProgram")))
                            .bindTo(api).asType(MethodType.methodType(void.class, RenderPipeline.class, Object.class));
                    sunPath = lookup.findVirtual(type, "getSunPathRotation", MethodType.methodType(float.class)).bindTo(api);
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    LOGGER.warn("Iris' API has no programs for other mods' pipelines; shader packs may draw the leaves oddly", e);
                }
            }
        }
        ASSIGN_PIPELINE = assign;
        IN_USE = inUse;
        SUN_PATH_ROTATION = sunPath;
    }

    private ShaderPacks() {
    }

    /**
     * Tells Iris which program of a shader pack draws {@code pipeline} ({@code IrisProgram} constant name). Without it
     * Iris draws an unknown pipeline with its own vanilla shader straight into the pack's buffers. A pipeline that got
     * its program already (Iris copies the program to a pipeline made from a vanilla one with {@code toBuilder}) is
     * left as it is.
     */
    public static void assignProgram(RenderPipeline pipeline, String program) {
        if (ASSIGN_PIPELINE == null) {
            return;
        }
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object constant = Enum.valueOf((Class<Enum>) Class.forName("net.irisshaders.iris.api.v0.IrisProgram"), program);
            ASSIGN_PIPELINE.invokeExact(pipeline, constant);
        } catch (IllegalStateException e) {
            // Already assigned (copied from the vanilla pipeline it was made from).
        } catch (Throwable e) {
            LOGGER.warn("Could not tell Iris the program {} for {}; shader packs may draw the leaves oddly", program,
                    pipeline.getLocation(), e);
        }
    }

    /**
     * How many degrees the active pack tilts the sun's path towards the south ({@code sunPathRotation}), 0 without a
     * pack or when it cannot be read.
     */
    public static float sunPathRotation() {
        if (SUN_PATH_ROTATION == null || sunPathBroken || !inUse()) {
            return 0.0F;
        }
        try {
            return (float) SUN_PATH_ROTATION.invokeExact();
        } catch (Throwable e) {
            sunPathBroken = true;
            return 0.0F;
        }
    }

    /** Whether a shader pack is active right now (packs can be switched in game). */
    public static boolean inUse() {
        if (IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IN_USE.invokeExact();
        } catch (Throwable e) {
            return false;
        }
    }
}
