package com.pockyl.rustling_leaves.client;

import net.minecraft.sounds.SoundEvent;

import com.pockyl.rustling_leaves.RustlingLeaves;

/**
 * The mod's leaf sounds (CC0 field recordings, see tools/prepare_sounds.py). They are not registered, only listed in
 * sounds.json, so the client stays welcome on servers without the mod; the client plays them directly.
 */
final class LeafSounds {
    /** A foot swishing through dry leaves. */
    static final SoundEvent RUSTLE = event("leaves.rustle");
    /** A step into wet leaves. */
    static final SoundEvent WET = event("leaves.wet");
    /** Landing in a pile: a big rush of leaves. */
    static final SoundEvent PLUNGE = event("leaves.plunge");
    /** A rake stroke dragging leaves over the ground. */
    static final SoundEvent RAKE = event("leaves.rake");

    private LeafSounds() {
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(RustlingLeaves.id(path));
    }
}
