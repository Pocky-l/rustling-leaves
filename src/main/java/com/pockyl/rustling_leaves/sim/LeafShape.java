package com.pockyl.rustling_leaves.sim;

/**
 * Leaf shapes. Each shape has one or more sprites (indices into the client's sprite list), a relative size and how
 * readily its trees shed: birches and cherries drop leaves and petals all the time, conifers hardly ever.
 */
public enum LeafShape {
    BROAD(0, 3, 1.0F, true, 1.0F),
    ROUND(3, 1, 0.85F, true, 1.5F),
    NEEDLE(4, 1, 0.8F, false, 0.15F),
    PETAL(5, 1, 0.7F, false, 1.6F);

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

    public static LeafShape byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BROAD;
    }
}
