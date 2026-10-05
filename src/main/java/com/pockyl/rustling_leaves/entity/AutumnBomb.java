package com.pockyl.rustling_leaves.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import com.pockyl.rustling_leaves.LeafEffects;
import com.pockyl.rustling_leaves.registry.ModEntities;
import com.pockyl.rustling_leaves.registry.ModItems;

/** The thrown autumn bomb. Harmless: on impact it tells the clients to burst into leaves. */
public final class AutumnBomb extends ThrowableItemProjectile {
    private static final byte BURST = 3;

    public AutumnBomb(EntityType<? extends AutumnBomb> type, Level level) {
        super(type, level);
    }

    public AutumnBomb(Level level, LivingEntity thrower) {
        super(ModEntities.AUTUMN_BOMB.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.AUTUMN_BOMB.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, BURST);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 0.6F, 1.3F);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1.5F, 0.7F);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == BURST) {
            LeafEffects.get().burst(getX(), getY(), getZ());
        } else {
            super.handleEntityEvent(id);
        }
    }
}
