package com.pockyl.rustling_leaves.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.item.AutumnBombItem;
import com.pockyl.rustling_leaves.item.LeafBagItem;
import com.pockyl.rustling_leaves.item.LeafBlowerItem;
import com.pockyl.rustling_leaves.item.WindStaffItem;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RustlingLeaves.MOD_ID);

    public static final RegistryObject<LeafBlowerItem> LEAF_BLOWER = ITEMS.register("leaf_blower",
            () -> new LeafBlowerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<LeafBagItem> LEAF_BAG = ITEMS.register("leaf_bag",
            () -> new LeafBagItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<AutumnBombItem> AUTUMN_BOMB = ITEMS.register("autumn_bomb",
            () -> new AutumnBombItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<WindStaffItem> WIND_STAFF = ITEMS.register("wind_staff",
            () -> new WindStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
