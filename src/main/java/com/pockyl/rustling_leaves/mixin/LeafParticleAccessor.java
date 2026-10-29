package com.pockyl.rustling_leaves.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The falling leaf particle of untinted leaves (cherry petals, pale oak leaves, modded leaves of the same kind), which
 * tells petals from leaves; vanilla keeps it protected. Applied on both sides, the game tests ask it on the server.
 */
@Mixin(UntintedParticleLeavesBlock.class)
public interface LeafParticleAccessor {
    @Accessor("leafParticle")
    ParticleOptions rustling_leaves$leafParticle();
}
