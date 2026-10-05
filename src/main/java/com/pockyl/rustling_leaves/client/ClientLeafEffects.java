package com.pockyl.rustling_leaves.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.pockyl.rustling_leaves.LeafEffects;
import com.pockyl.rustling_leaves.item.BagContents;

/** Routes the items' effects into the client's leaf simulation. */
final class ClientLeafEffects implements LeafEffects {
    @Override
    public void blow(LivingEntity user, Vec3 nozzle, Vec3 direction) {
        LeafManager.blow(user, nozzle, direction);
    }

    @Override
    public void vacuum(LivingEntity user, Vec3 mouth, Vec3 direction) {
        LeafManager.vacuum(user, mouth, direction);
    }

    @Override
    public void pour(LivingEntity user, Vec3 mouth, Vec3 direction, int count, BagContents contents) {
        LeafManager.pour(user, mouth, direction, count, contents);
    }

    @Override
    public void burst(double x, double y, double z) {
        LeafManager.burst(x, y, z);
    }
}
