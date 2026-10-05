package com.pockyl.rustling_leaves.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.entity.AutumnBomb;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RustlingLeaves.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AutumnBomb>> AUTUMN_BOMB = ENTITIES.register("autumn_bomb",
            () -> EntityType.Builder.<AutumnBomb>of(AutumnBomb::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(RustlingLeaves.id("autumn_bomb").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
