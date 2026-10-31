package com.pockyl.rustling_leaves.fabric;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;

import com.pockyl.rustling_leaves.client.LeafManager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The debug screen entry of the leaf counts, registered as NeoForge's RegisterDebugEntriesEvent does: an entry of its
 * own that the default profile shows with the overlay. Called from {@code mixin.DebugScreenEntriesMixin} once
 * vanilla's entries are there.
 */
public final class LeafDebugEntry {
    private LeafDebugEntry() {
    }

    /** Registers the entry and returns the profiles with the entry added to the default one. */
    public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> register(
            Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> profiles) {
        DebugScreenEntries.register(LeafManager.DEBUG_ENTRY, (displayer, level, clientChunk, serverChunk) -> LeafManager.debugText(displayer));
        Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> withEntry = new EnumMap<>(DebugScreenProfile.class);
        profiles.forEach((profile, statuses) -> {
            Map<Identifier, DebugScreenEntryStatus> copy = new HashMap<>(statuses);
            if (profile == DebugScreenProfile.DEFAULT) {
                copy.put(LeafManager.DEBUG_ENTRY, DebugScreenEntryStatus.IN_OVERLAY);
            }
            withEntry.put(profile, Map.copyOf(copy));
        });
        return Map.copyOf(withEntry);
    }
}
