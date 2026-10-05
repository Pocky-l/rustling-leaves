package com.pockyl.rustling_leaves;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.pockyl.rustling_leaves.item.BagContents;

/**
 * Visual leaf effects of the items. Items and entities are common code, the leaves are client-only: the client mod
 * installs its implementation here, on a dedicated server the calls go to the no-op default.
 */
public interface LeafEffects {
    LeafEffects NONE = new LeafEffects() {
    };

    /** A leaf blower in use: a cone of air from the nozzle. */
    default void blow(LivingEntity user, Vec3 nozzle, Vec3 direction) {
    }

    /** A leaf bag sucking leaves in through its mouth. */
    default void vacuum(LivingEntity user, Vec3 mouth, Vec3 direction) {
    }

    /** A leaf bag pouring {@code count} leaves out. */
    default void pour(LivingEntity user, Vec3 mouth, Vec3 direction, int count, BagContents contents) {
    }

    /** An autumn bomb burst at this spot. */
    default void burst(double x, double y, double z) {
    }

    static LeafEffects get() {
        return Holder.effects;
    }

    static void set(LeafEffects effects) {
        Holder.effects = effects;
    }

    final class Holder {
        private static volatile LeafEffects effects = NONE;

        private Holder() {
        }
    }
}
