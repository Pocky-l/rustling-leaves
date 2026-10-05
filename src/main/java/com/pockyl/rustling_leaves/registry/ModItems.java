package com.pockyl.rustling_leaves.registry;

import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.item.AutumnBombItem;
import com.pockyl.rustling_leaves.item.BagContents;
import com.pockyl.rustling_leaves.item.LeafBagItem;
import com.pockyl.rustling_leaves.item.LeafBlowerItem;
import com.pockyl.rustling_leaves.item.WindStaffItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RustlingLeaves.MOD_ID);

    public static final DeferredItem<LeafBlowerItem> LEAF_BLOWER = ITEMS.registerItem("leaf_blower",
            properties -> new LeafBlowerItem(properties.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<LeafBagItem> LEAF_BAG = ITEMS.registerItem("leaf_bag",
            properties -> new LeafBagItem(properties.stacksTo(1).component(ModDataComponents.BAG_CONTENTS.get(), BagContents.EMPTY)));
    public static final DeferredItem<AutumnBombItem> AUTUMN_BOMB = ITEMS.registerItem("autumn_bomb",
            properties -> new AutumnBombItem(properties.stacksTo(16)));
    public static final DeferredItem<WindStaffItem> WIND_STAFF = ITEMS.registerItem("wind_staff",
            properties -> new WindStaffItem(properties.stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
