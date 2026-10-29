package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.rustling_leaves.mixin.LeafParticleAccessor;

/** What the leaves of a tree look like; the client answers from block colors and textures. */
public interface TreeLeaves {
    /** The tree color (0xRRGGBB) of a leaves block at a position. */
    int color(BlockState state, Level level, BlockPos pos);

    /** Which leaves a leaves block drops. */
    LeafShape shape(BlockState state);

    /**
     * Whether the block drops vanilla cherry petals by itself, which the client turns into simulated petals one by one
     * ({@link LeafSpawner#dropBelow}); the canopy spawner then only adds the petals the wind shakes off. Every leaves
     * block drops some falling leaf particle since 1.21.5, but only the petals are taken over one for one: the other
     * trees keep the canopy spawner's calm fall, which reaches farther than the particles do.
     */
    default boolean dropsVanillaLeaves(BlockState state) {
        return state.getBlock() instanceof UntintedParticleLeavesBlock leaves
                && ((LeafParticleAccessor) leaves).rustling_leaves$leafParticle().getType() == ParticleTypes.CHERRY_LEAVES;
    }
}
