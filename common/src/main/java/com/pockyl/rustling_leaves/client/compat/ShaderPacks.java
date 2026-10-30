package com.pockyl.rustling_leaves.client.compat;


import com.pockyl.rustling_leaves.platform.Platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shader packs of <a href="https://modrinth.com/mod/iris">Iris</a> (or its Forge port Oculus), asked through Iris' public
 * API by reflection, so neither is needed to build or run the mod.
 * <p>
 * The leaves use vanilla shaders, which Iris swaps for the pack's programs; what changes with a pack is the vertex
 * layout of the meshes (see {@code LeafRenderer}).
 */
public final class ShaderPacks {
    private static final MethodHandle IN_USE;
    /** Iris' pipeline manager, its current pipeline and that pipeline's sun path rotation (not public API). */
    private static final MethodHandle PIPELINE;
    private static final MethodHandle SUN_PATH_ROTATION;
    private static boolean sunPathBroken;

    static {
        MethodHandle inUse = null;
        MethodHandle pipeline = null;
        MethodHandle sunPath = null;
        if (Platform.INSTANCE.isModLoaded("iris") || Platform.INSTANCE.isModLoaded("oculus")) {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                inUse = null;
            }
            // Oculus 1.7 and later (package of Iris 1.7), then Oculus 1.6 and earlier.
            for (String root : new String[] {"net.irisshaders.iris", "net.coderbot.iris"}) {
                try {
                    Class<?> manager = Class.forName(root + ".pipeline.PipelineManager");
                    Class<?> rendering = Class.forName(root + ".pipeline.WorldRenderingPipeline");
                    MethodHandle getManager = lookup.findStatic(Class.forName(root + ".Iris"), "getPipelineManager",
                            MethodType.methodType(manager));
                    MethodHandle getPipeline = lookup.findVirtual(manager, "getPipelineNullable", MethodType.methodType(rendering));
                    pipeline = MethodHandles.filterReturnValue(getManager, getPipeline).asType(MethodType.methodType(Object.class));
                    sunPath = lookup.findVirtual(rendering, "getSunPathRotation", MethodType.methodType(float.class))
                            .asType(MethodType.methodType(float.class, Object.class));
                    break;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    pipeline = null;
                }
            }
        }
        IN_USE = inUse;
        PIPELINE = pipeline;
        SUN_PATH_ROTATION = sunPath;
    }

    private ShaderPacks() {
    }

    /**
     * How many degrees the active pack tilts the sun's path towards the south ({@code sunPathRotation}), 0 without a
     * pack or when it cannot be read.
     */
    public static float sunPathRotation() {
        if (PIPELINE == null || sunPathBroken || !inUse()) {
            return 0.0F;
        }
        try {
            Object pipeline = (Object) PIPELINE.invokeExact();
            return pipeline == null ? 0.0F : (float) SUN_PATH_ROTATION.invokeExact(pipeline);
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
