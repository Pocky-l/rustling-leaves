package com.pockyl.rustling_leaves.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the mixins into other mods ({@link FallingLeavesMixin}, {@link FallingLeavesFabricMixin}, {@link
 * LivingWindMixin}) only when the class they hook into is there. Those mods are optional and do not exist for every
 * Minecraft version or loader; without them the mixins are skipped up front instead of waiting for a class that never loads. Whether the class is there is asked of the
 * class loader of the mods, so this works the same on every loader version. Mixins into the game always apply.
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("rustling_leaves");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName.startsWith("net.minecraft.")) {
            return true;
        }
        String mixin = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (CompatMixinPlugin.class.getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") == null) {
            LOGGER.debug("Skipping {}: {} is not installed", mixin, targetClassName);
            return false;
        }
        LOGGER.info("Applying {} to {}", mixin, targetClassName);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
