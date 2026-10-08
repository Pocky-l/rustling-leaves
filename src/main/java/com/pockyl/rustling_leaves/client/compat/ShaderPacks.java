package com.pockyl.rustling_leaves.client.compat;

import net.minecraftforge.fml.ModList;

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

    static {
        MethodHandle inUse = null;
        if (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus")) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                inUse = null;
            }
        }
        IN_USE = inUse;
    }

    private ShaderPacks() {
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
