package com.pockyl.rustling_leaves.sim;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Which tree a leaf came from, as a small number stored with every moving leaf and litter cell (the "kind"). Kinds 0-3
 * stand for leaves of a {@link LeafShape} whose tree is not known (litter saved before 2.9.0, game tests); every leaves
 * block the client meets gets its own kind from 4 up, keyed by its block id, so the player's settings of that tree reach
 * its leaves in the air and on the ground. Kinds live for the session only: the storage writes the keys.
 * <p>
 * The client sets the resolver that turns a key into a {@link TreeProfile}; profiles are resolved lazily on the game
 * thread and dropped by {@link #refresh} when the settings change. Ids may be taken on the storage thread.
 */
public final class TreeKinds {
    /** Kinds below this are bare shapes. */
    public static final int SHAPES = LeafShape.values().length;

    private static final Object LOCK = new Object();
    private static final Map<String, Integer> IDS = new HashMap<>();
    private static String[] keys = new String[64];
    private static int size = SHAPES;
    private static volatile TreeProfile[] profiles = new TreeProfile[64];
    private static volatile Function<String, TreeProfile> resolver;

    private TreeKinds() {
    }

    /** The kind of a tree key (a block id), registering it on first use. */
    public static int id(String key) {
        synchronized (LOCK) {
            Integer id = IDS.get(key);
            if (id != null) {
                return id;
            }
            if (size == Short.MAX_VALUE) {
                return 0;
            }
            if (size == keys.length) {
                keys = Arrays.copyOf(keys, size * 2);
            }
            keys[size] = key;
            IDS.put(key, size);
            return size++;
        }
    }

    /** The key of a kind, null for the bare shapes. */
    public static String key(int kind) {
        synchronized (LOCK) {
            return kind >= SHAPES && kind < size ? keys[kind] : null;
        }
    }

    /** The settings of the trees of a kind. */
    public static TreeProfile profile(int kind) {
        if (kind < SHAPES) {
            return TreeProfile.defaults(LeafShape.byId(kind));
        }
        TreeProfile[] current = profiles;
        if (kind < current.length) {
            TreeProfile profile = current[kind];
            if (profile != null) {
                return profile;
            }
        }
        return resolve(kind);
    }

    /** The leaf shape of a kind. */
    public static LeafShape shape(int kind) {
        return profile(kind).shape;
    }

    /** Sets how keys become profiles (the client's tree settings) and drops the profiles resolved so far. */
    public static void setResolver(Function<String, TreeProfile> value) {
        resolver = value;
        refresh();
    }

    /** Drops every resolved profile: the tree settings or the textures changed. */
    public static void refresh() {
        synchronized (LOCK) {
            profiles = new TreeProfile[profiles.length];
        }
    }

    private static TreeProfile resolve(int kind) {
        String key = key(kind);
        Function<String, TreeProfile> function = resolver;
        TreeProfile profile = null;
        if (key != null && function != null) {
            profile = function.apply(key);
        }
        if (profile == null) {
            profile = TreeProfile.defaults(LeafShape.BROAD);
        }
        synchronized (LOCK) {
            TreeProfile[] current = profiles;
            if (kind >= current.length) {
                current = Arrays.copyOf(current, Math.max(kind + 1, current.length * 2));
            }
            current[kind] = profile;
            profiles = current;
        }
        return profile;
    }
}
