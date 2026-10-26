package com.pockyl.rustling_leaves.sim;

/**
 * Leaf shapes. Each shape has one or more sprites (indices into the client's sprite list), a relative size and how
 * readily its trees shed: birches and cherries drop leaves and petals all the time, conifers hardly ever. The sprites
 * are the falling leaves of newer Minecraft versions: one tinted leaf in three shades for every tree (as vanilla does)
 * and the cherry petal in three shades.
 */
public enum LeafShape {
    BROAD(0, 3, 1.0F, true, 1.0F),
    ROUND(0, 3, 0.85F, true, 1.5F),
    NEEDLE(0, 3, 0.8F, false, 0.15F),
    PETAL(3, 3, 0.7F, false, 1.6F);

    public static final int SPRITE_COUNT = 6;
    /** Half-size of an average leaf quad in blocks, before the shape and config multipliers. */
    public static final float BASE_SIZE = 0.1F;

    private static final LeafShape[] VALUES = values();

    public final int firstSprite;
    public final int variants;
    public final float size;
    /** Whether these leaves may turn yellow, orange or brown; needles and petals keep their color. */
    public final boolean autumn;
    /** How often these trees drop leaves, relative to broad-leaved trees. */
    public final float shedRate;

    LeafShape(int firstSprite, int variants, float size, boolean autumn, float shedRate) {
        this.firstSprite = firstSprite;
        this.variants = variants;
        this.size = size;
        this.autumn = autumn;
        this.shedRate = shedRate;
    }

    /** The sprite itself if it belongs to this shape, else one of the shape's own (saved piles from older versions). */
    public int ownSprite(int sprite) {
        return sprite >= firstSprite && sprite < firstSprite + variants ? sprite : firstSprite + sprite % variants;
    }

    public static LeafShape byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BROAD;
    }
}
