package com.pockyl.rustling_leaves.client;

import net.minecraft.resources.Identifier;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.TreeProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leaf textures a player can give a tree: the falling leaves of newer Minecraft versions (leaf, petal, needle), the
 * mod's own leaf shapes in the same pixel style (maple, oak, ginkgo, willow), the mod's original textures, and any
 * sprites of the block atlas named in the tree settings (resource packs). Every texture has its sprites, a shadow
 * silhouette and the pile textures its litter is drawn with.
 * <p>
 * Sprite, shadow and pile texture indices are the renderer's: the first ones are fixed ({@link LeafShape#firstSprite},
 * {@link TreeProfile#LAYERS_NEEDLES}...), the sprites named by the settings are added behind them on every reload.
 */
final class LeafStyles {
    /** A texture: where its sprites, shadow and pile textures sit in the renderer's lists. */
    record Style(String name, LeafShape shape, int firstSprite, int variants, int shadow, int layers) {
    }

    /** Pile texture sets: body, sparse layer, dense layer each. 0 and 1 are swapped for the old ones by the option. */
    static final int LAYERS_OLD = 2;
    private static final String[] OWN = {"maple", "oak", "ginkgo", "willow"};

    private static final List<Identifier> BUILT_IN_SPRITES = new ArrayList<>(Arrays.asList(LeafShapes.SPRITES));
    private static final List<Identifier> SHADOWS = new ArrayList<>(Arrays.asList(LeafShapes.SHADOW_SPRITES));
    private static final List<Identifier[]> LAYERS = new ArrayList<>();
    /** Built-in textures in the order the editor cycles through them. */
    private static final Map<String, Style> BUILT_IN = new LinkedHashMap<>();

    static {
        LAYERS.add(new Identifier[] {LeafShapes.LITTER, LeafShapes.LITTER_SPARSE, LeafShapes.LITTER_LAYER});
        LAYERS.add(new Identifier[] {LeafShapes.NEEDLE_LITTER, LeafShapes.NEEDLE_LITTER_SPARSE, LeafShapes.NEEDLE_LITTER_LAYER});
        LAYERS.add(new Identifier[] {LeafShapes.OLD_LITTER, LeafShapes.OLD_LITTER_SPARSE, LeafShapes.OLD_LITTER_LAYER});
        add(new Style("leaf", LeafShape.BROAD, 0, 3, TreeProfile.SHADOW_LEAF, TreeProfile.LAYERS_LEAVES));
        add(new Style("petal", LeafShape.PETAL, 3, 3, TreeProfile.SHADOW_PETAL, TreeProfile.LAYERS_LEAVES));
        add(new Style("needle", LeafShape.NEEDLE, 12, 3, TreeProfile.SHADOW_NEEDLE, TreeProfile.LAYERS_NEEDLES));
        for (String name : OWN) {
            int first = BUILT_IN_SPRITES.size();
            for (String shade : new String[] {"light", "mid", "dark"}) {
                BUILT_IN_SPRITES.add(RustlingLeaves.id("block/leaf/" + name + "_" + shade));
            }
            SHADOWS.add(RustlingLeaves.id("block/leaf/shadow/" + name));
            LAYERS.add(new Identifier[] {RustlingLeaves.id("block/leaf/" + name + "_litter"),
                    RustlingLeaves.id("block/leaf/" + name + "_litter_sparse"), RustlingLeaves.id("block/leaf/" + name + "_litter_layer")});
            add(new Style(name, LeafShape.BROAD, first, 3, SHADOWS.size() - 1, LAYERS.size() - 1));
        }
        add(new Style("old_leaf", LeafShape.BROAD, 6, 3, TreeProfile.SHADOW_LEAF, LAYERS_OLD));
        add(new Style("old_round", LeafShape.ROUND, 9, 1, TreeProfile.SHADOW_LEAF, LAYERS_OLD));
        add(new Style("old_needle", LeafShape.NEEDLE, 10, 1, TreeProfile.SHADOW_NEEDLE, LAYERS_OLD));
        add(new Style("old_petal", LeafShape.PETAL, 11, 1, TreeProfile.SHADOW_PETAL, LAYERS_OLD));
    }

    /** Sprites named by the tree settings (resource pack textures), behind the built-in ones. */
    private static final List<Identifier> EXTRA = new ArrayList<>();
    private static volatile Identifier[] sprites = BUILT_IN_SPRITES.toArray(new Identifier[0]);

    private LeafStyles() {
    }

    private static void add(Style style) {
        BUILT_IN.put(style.name(), style);
    }

    /** Names of the built-in textures, in editor order. */
    static List<String> names() {
        return new ArrayList<>(BUILT_IN.keySet());
    }

    /** A built-in texture, or null. */
    static Style get(String name) {
        return BUILT_IN.get(name);
    }

    /**
     * The texture of one or more sprites of the block atlas (a resource pack's {@code textures/block/...}), added to the
     * renderer's sprites; null if a name is not a valid id.
     */
    static Style custom(List<String> spriteIds, LeafShape shape) {
        List<Identifier> ids = new ArrayList<>();
        for (String id : spriteIds) {
            Identifier location = Identifier.tryParse(id);
            if (location == null) {
                return null;
            }
            ids.add(location);
        }
        if (ids.isEmpty() || ids.size() > 16) {
            return null;
        }
        int first;
        synchronized (EXTRA) {
            first = BUILT_IN_SPRITES.size() + EXTRA.size();
            // The same sprites again (another tree with this texture) share their slots.
            for (int start = 0; start + ids.size() <= EXTRA.size(); start++) {
                if (EXTRA.subList(start, start + ids.size()).equals(ids)) {
                    first = BUILT_IN_SPRITES.size() + start;
                    return customStyle(first, ids.size(), shape);
                }
            }
            if (first + ids.size() > 255) {
                return null;
            }
            EXTRA.addAll(ids);
            List<Identifier> all = new ArrayList<>(BUILT_IN_SPRITES);
            all.addAll(EXTRA);
            sprites = all.toArray(new Identifier[0]);
        }
        return customStyle(first, ids.size(), shape);
    }

    private static Style customStyle(int first, int variants, LeafShape shape) {
        TreeProfile defaults = TreeProfile.defaults(shape);
        return new Style("custom", shape, first, variants, defaults.shadow, defaults.layers);
    }

    /** Forgets the sprites of the settings (they are read again). */
    static void clearCustom() {
        synchronized (EXTRA) {
            if (!EXTRA.isEmpty()) {
                EXTRA.clear();
                sprites = BUILT_IN_SPRITES.toArray(new Identifier[0]);
                }
        }
    }

    /** Every sprite by index. */
    static Identifier[] sprites() {
        return sprites;
    }

    static Identifier[] shadows() {
        return SHADOWS.toArray(new Identifier[0]);
    }

    /** Pile texture sets: body, sparse, dense of each. */
    static Identifier[][] layers() {
        return LAYERS.toArray(new Identifier[0][]);
    }
}
