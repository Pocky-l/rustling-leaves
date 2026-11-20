package com.pockyl.rustling_leaves.mixin;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The hashed world seed a server sends when the player joins (the client keeps it in the level's biome manager). It
 * tells worlds apart that a server address serves one after another.
 */
@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
    @Accessor("biomeZoomSeed")
    long rustling_leaves$seed();
}
