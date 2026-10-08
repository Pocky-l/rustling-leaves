package com.pockyl.rustling_leaves.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.item.BagContents;
import com.pockyl.rustling_leaves.item.LeafBagItem;
import com.pockyl.rustling_leaves.registry.ModDataComponents;
import com.pockyl.rustling_leaves.registry.ModItems;
import com.pockyl.rustling_leaves.sim.Armful;
import com.pockyl.rustling_leaves.sim.LeafListener;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.LeafSpawner;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;
import com.pockyl.rustling_leaves.sim.SeasonCurve;
import com.pockyl.rustling_leaves.sim.TreeLeaves;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * In-game tests, run headless by {@code gradlew runGameTestServer}. The simulation only needs a {@code Level}, so it
 * runs here against real blocks; each test steps it synchronously. Tests use the 7x6x7 {@code box} structure with a
 * stone floor placed at y = 0, so the litter lies at relative height 1.
 */
@GameTestHolder(RustlingLeaves.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private static final double EPSILON = 0.05;
    private static final float LAYER = LitterField.LAYER;

    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treesShedAtTheirOwnRate(GameTestHelper helper) {
        LeafSettings settings = new LeafSettings();
        helper.assertTrue(settings.shedRate(LeafShape.ROUND) > settings.shedRate(LeafShape.BROAD), "birches shed more than oaks");
        helper.assertTrue(settings.shedRate(LeafShape.PETAL) > settings.shedRate(LeafShape.BROAD), "cherries shed more than oaks");
        helper.assertTrue(settings.shedRate(LeafShape.NEEDLE) < 0.5F * settings.shedRate(LeafShape.BROAD), "conifers hardly shed");
        settings.treeFallRates = false;
        for (LeafShape shape : LeafShape.values()) {
            helper.assertTrue(settings.shedRate(shape) == 1.0F, "with the option off every tree sheds alike");
        }
        helper.succeed();
    }

    /** Sub-season middles: positions in sub-seasons since the start of early spring. */
    private static final float MID_SPRING = 1.5F;
    private static final float MID_SUMMER = 4.5F;
    private static final float EARLY_AUTUMN = 6.5F;
    private static final float MID_AUTUMN = 7.5F;
    private static final float LATE_AUTUMN = 8.5F;
    private static final float MID_WINTER = 10.5F;

    @GameTest(template = "empty")
    public static void seasonCurveFollowsTheYear(GameTestHelper helper) {
        LeafSettings settings = new LeafSettings();
        SeasonCurve.apply(settings, MID_SUMMER);
        helper.assertTrue(settings.seasonFallRate == 1.0F && settings.seasonPetalRate == 1.0F && settings.seasonAutumnColors == 1.0F,
                "summer changes nothing");
        float summerShare = settings.autumnShare();
        SeasonCurve.apply(settings, EARLY_AUTUMN);
        float early = settings.seasonFallRate;
        float earlyShare = settings.autumnShare();
        SeasonCurve.apply(settings, MID_AUTUMN);
        float mid = settings.seasonFallRate;
        float midShare = settings.autumnShare();
        SeasonCurve.apply(settings, LATE_AUTUMN);
        helper.assertTrue(1.0F < early && early < mid && mid < settings.seasonFallRate, "leaf fall does not grow through autumn");
        helper.assertTrue(settings.seasonFallRate == settings.autumnFallRate, "late autumn fall rate " + settings.seasonFallRate);
        helper.assertTrue(summerShare < earlyShare && earlyShare < midShare && midShare < settings.autumnShare(),
                "autumn colors do not grow through autumn");
        helper.assertTrue(settings.autumnShare() == 1.0F, "late autumn share " + settings.autumnShare());
        helper.assertTrue(settings.seasonPetalRate == 1.0F, "autumn changes cherry petals");
        SeasonCurve.apply(settings, MID_WINTER);
        helper.assertTrue(settings.seasonFallRate == settings.winterFallRate && settings.seasonPetalRate == settings.winterFallRate,
                "winter fall rate " + settings.seasonFallRate);
        SeasonCurve.apply(settings, MID_SPRING);
        helper.assertTrue(settings.seasonFallRate < 1.0F && settings.seasonPetalRate > 1.0F, "spring: few leaves, more petals");
        // No jumps: across every sub-season boundary (and the turn of the year) the values change only a little.
        for (int boundary = 0; boundary < SeasonCurve.SUB_SEASONS; boundary++) {
            SeasonCurve.apply(settings, Math.floorMod(boundary - 1, SeasonCurve.SUB_SEASONS) + 0.999F);
            float fall = settings.seasonFallRate;
            float colors = settings.seasonAutumnColors;
            SeasonCurve.apply(settings, boundary + 0.001F);
            helper.assertTrue(Math.abs(settings.seasonFallRate - fall) < 0.02F && Math.abs(settings.seasonAutumnColors - colors) < 0.02F,
                    "season jumps at sub-season " + boundary);
        }
        // The season multiplies the configured options instead of replacing them.
        settings.autumnColors = 0.0F;
        SeasonCurve.apply(settings, LATE_AUTUMN);
        helper.assertTrue(settings.autumnShare() == 0.0F, "autumn colors appear although they are off");
        SeasonCurve.clear(settings);
        helper.assertTrue(settings.seasonFallRate == 1.0F && settings.seasonPetalRate == 1.0F && settings.seasonAutumnColors == 1.0F,
                "no season is not neutral");
        helper.succeed();
    }

    @GameTest(template = "box", skyAccess = true)
    public static void winterTreesShedFarFewerLeaves(GameTestHelper helper) {
        canopy(helper);
        int summer = shed(helper, MID_SUMMER, 400, null);
        int winter = shed(helper, MID_WINTER, 400, null);
        int autumn = shed(helper, LATE_AUTUMN, 400, null);
        helper.assertTrue(summer > 100, "only " + summer + " leaves fell in summer");
        helper.assertTrue(winter * 5 < summer, winter + " leaves fell in winter, " + summer + " in summer");
        helper.assertTrue(autumn > summer * 2, autumn + " leaves fell in late autumn, " + summer + " in summer");
        helper.succeed();
    }

    @GameTest(template = "box", skyAccess = true)
    public static void lateAutumnLeavesFallInAutumnColors(GameTestHelper helper) {
        canopy(helper);
        int[] summer = new int[2];
        shed(helper, MID_SUMMER, 100, summer);
        int[] autumn = new int[2];
        shed(helper, LATE_AUTUMN, 100, autumn);
        helper.assertTrue(summer[1] > 20 && autumn[1] > 20, "too few falling leaves to compare");
        helper.assertTrue(summer[0] < summer[1] * 0.6F, summer[0] + " of " + summer[1] + " leaves in autumn colors in summer");
        helper.assertTrue(autumn[0] == autumn[1], autumn[0] + " of " + autumn[1] + " leaves in autumn colors in late autumn");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void fallingLeafJoinsTheLitter(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 400);
        helper.assertTrue(sim.pool().count() == 0, "leaf is still moving");
        helper.assertTrue(sim.field().total() == 1, "litter has " + sim.field().total() + " leaves");
        double base = baseOfOnlyCell(sim, helper);
        helper.assertTrue(Math.abs(base - 1.0) < EPSILON, "leaf lies at y " + base);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafRestsOnSlab(GameTestHelper helper) {
        floor(helper);
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        fill(helper, 1, 1, 5, 5, 1, slab);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 400);
        double base = baseOfOnlyCell(sim, helper);
        helper.assertTrue(Math.abs(base - 1.5) < EPSILON, "leaf lies at y " + base);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafFloatsOnWater(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 1, 5, 5, 1, Blocks.WATER.defaultBlockState());
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 300);
        LeafPool pool = sim.pool();
        helper.assertTrue(pool.state[leaf] == LeafPool.FLOATING, "leaf is not floating, state " + pool.state[leaf]);
        double y = relativeY(helper, pool.y[leaf]);
        helper.assertTrue(y > 1.8 && y < 2.0, "leaf floats at y " + y);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void soakedLeafSinksToTheBottom(GameTestHelper helper) {
        floor(helper);
        for (int y = 1; y <= 2; y++) {
            fill(helper, 0, 0, 6, 6, y, Blocks.STONE.defaultBlockState());
            fill(helper, 1, 1, 5, 5, y, Blocks.WATER.defaultBlockState());
        }
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 200);
        helper.assertTrue(sim.pool().state[leaf] == LeafPool.FLOATING, "leaf is not floating first");
        run(sim, helper, 20 * 60 * 3);
        helper.assertTrue(sim.pool().count() == 0 && sim.field().total() == 1, "the leaf did not settle on the bottom");
        assertAllBases(sim, helper, 1.0);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void floatingLeavesDriftTogether(GameTestHelper helper) {
        floor(helper);
        for (int y = 1; y <= 2; y++) {
            fill(helper, 0, 0, 6, 6, y, Blocks.STONE.defaultBlockState());
            fill(helper, 1, 1, 5, 5, y, Blocks.WATER.defaultBlockState());
        }
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int a = spawn(sim, helper, 3.33, 2.88, 3.5);
        int b = spawn(sim, helper, 3.67, 2.88, 3.5);
        run(sim, helper, 300);
        LeafPool pool = sim.pool();
        helper.assertTrue(pool.state[a] == LeafPool.FLOATING && pool.state[b] == LeafPool.FLOATING, "leaves are not floating");
        double after = Math.hypot(pool.x[a] - pool.x[b], pool.z[a] - pool.z[b]);
        // They meet (touching at 0.16 for these sizes) but do not slide over each other.
        helper.assertTrue(after < 0.22, "floating leaves did not gather: now " + after + " apart");
        helper.assertTrue(after > 0.12, "floating leaves overlap: " + after + " apart");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void waterPouringOntoALeafSinksIt(GameTestHelper helper) {
        floor(helper);
        for (int y = 1; y <= 2; y++) {
            fill(helper, 0, 0, 6, 6, y, Blocks.STONE.defaultBlockState());
            fill(helper, 1, 1, 5, 5, y, Blocks.WATER.defaultBlockState());
        }
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 3.2, 3.5);
        run(sim, helper, 40);
        helper.assertTrue(sim.pool().state[leaf] == LeafPool.FLOATING, "leaf is not floating");
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos above = new BlockPos((int) Math.floor(sim.pool().x[leaf]), origin.getY() + 3,
                (int) Math.floor(sim.pool().z[leaf]));
        helper.getLevel().setBlock(above, Blocks.WATER.defaultBlockState(), 2);
        run(sim, helper, 3);
        helper.assertTrue(sim.pool().state[leaf] == LeafPool.SINKING, "the leaf was not pushed under");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void floatingLeavesStayOnTheWaterAndSettle(GameTestHelper helper) {
        floor(helper);
        // A pond with a low, open shore: the ground around it lies below the water surface.
        fill(helper, 2, 2, 4, 4, 1, Blocks.WATER.defaultBlockState());
        LeafSimulation sim = simulation(helper, 128, LeafListener.NONE);
        int[] leaves = new int[40];
        for (int n = 0; n < leaves.length; n++) {
            leaves[n] = spawn(sim, helper, 2.1 + (n % 7) * 0.4, 1.88, 2.1 + (n / 7) * 0.45);
        }
        run(sim, helper, 600);
        LeafPool pool = sim.pool();
        double speed = 0.0;
        for (int leaf : leaves) {
            helper.assertTrue(pool.state[leaf] == LeafPool.FLOATING, "a leaf left the water (state " + pool.state[leaf] + ")");
            speed += Math.hypot(pool.vx[leaf], pool.vz[leaf]);
        }
        helper.assertTrue(sim.field().total() == 0, "leaves climbed onto the shore");
        helper.assertTrue(speed / leaves.length < 0.002, "the leaves did not settle down: mean speed " + speed / leaves.length);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafBurnsInLava(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 1, 5, 5, 1, Blocks.LAVA.defaultBlockState());
        int[] burned = new int[1];
        LeafSimulation sim = simulation(helper, 64, new LeafListener() {
            @Override
            public void onBurn(double x, double y, double z) {
                burned[0]++;
            }
        });
        spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 300);
        helper.assertTrue(burned[0] == 1 && sim.pool().count() == 0 && sim.field().total() == 0, "leaf did not burn");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void pileSlumpsToItsAngleOfRepose(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 512, LeafListener.NONE);
        int[] center = cellAt(helper, 3.5, 3.5);
        double ground = absoluteY(helper, 1.0);
        for (int n = 0; n < 150; n++) {
            sim.field().add(center[0], center[1], ground, 0x6A8F3A, 0, 0L, 0);
        }
        sim.field().queueRelax(center[0], center[1]);
        run(sim, helper, 200);
        helper.assertTrue(sim.field().total() + sim.pool().count() == 150, "leaves were lost: " + sim.field().total());
        helper.assertTrue(sim.field().count(center[0], center[1]) < 150, "the pile did not slump");
        double steepest = 0.0;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                double self = surface(sim, center[0] + dx, center[1] + dz, ground);
                steepest = Math.max(steepest, self - surface(sim, center[0] + dx + 1, center[1] + dz, ground));
                steepest = Math.max(steepest, self - surface(sim, center[0] + dx, center[1] + dz + 1, ground));
                steepest = Math.max(steepest, self - surface(sim, center[0] + dx - 1, center[1] + dz, ground));
            }
        }
        helper.assertTrue(steepest <= LitterField.REPOSE + 2 * LAYER, "pile is too steep: " + steepest);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void walkingThroughAPileThinsIt(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 512, LeafListener.NONE);
        int total = carpet(sim, helper, 2.0, 16);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        int[] cell = cellAt(helper, 3.5, 3.5);
        sim.beginTick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
        int moved = sim.disturb(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.22, 0.0, 0.0, 0.6F, 1.8F, false, 0.0F);
        int under = sim.field().count(cell[0], cell[1]);
        helper.assertTrue(under > 0 && under < 16, "the cell under the feet holds " + under + " of 16 leaves");
        sim.finishTick();
        helper.assertTrue(moved > 20, "only " + moved + " leaves were pushed");
        helper.assertTrue(sim.pool().count() < moved / 3, sim.pool().count() + " of " + moved + " leaves flew - most should be shoved aside");
        helper.assertTrue(sim.pool().count() > 0, "no leaf flew up at all");
        helper.assertTrue(sim.field().total() + sim.pool().count() == total, "leaves were lost");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void sneakingLeavesThinCarpetAlone(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        carpet(sim, helper, 2.0, 3);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        sim.beginTick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
        int moved = sim.disturb(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.065, 0.0, 0.0, 0.6F, 1.5F, true, 0.0F);
        sim.finishTick();
        helper.assertTrue(moved == 0 && sim.pool().count() == 0, "sneaking moved " + moved + " leaves");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void jumpingIntoAPileSplashes(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 512, LeafListener.NONE);
        int total = carpet(sim, helper, 2.0, 20);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        sim.beginTick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
        sim.disturb(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0, -0.5, 0.0, 0.6F, 1.8F, false, 0.5F);
        sim.finishTick();
        int up = 0;
        for (int i = 0; i < sim.pool().highWater(); i++) {
            if (sim.pool().state[i] == LeafPool.FALLING && sim.pool().vy[i] > 0.05F) {
                up++;
            }
        }
        helper.assertTrue(up >= 15, "only " + up + " leaves splashed up");
        run(sim, helper, 400);
        helper.assertTrue(sim.field().total() + sim.pool().count() == total, "leaves were lost");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void windChargeBlowsLitterAway(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 1024, LeafListener.NONE);
        int total = carpet(sim, helper, 2.5, 4);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        int[] cell = cellAt(helper, 3.5, 3.5);
        sim.explode(center.getX() + 0.5, center.getY() + 0.3, center.getZ() + 0.5, 1.2F, true);
        run(sim, helper, 3);
        int airborne = 0;
        for (int i = 0; i < sim.pool().highWater(); i++) {
            if (sim.pool().state[i] == LeafPool.FALLING && sim.pool().vy[i] > 0.0F) {
                airborne++;
            }
        }
        helper.assertTrue(airborne > 40, "only " + airborne + " leaves flew up");
        helper.assertTrue(sim.field().count(cell[0], cell[1]) < 4, "the center was not disturbed");
        run(sim, helper, 600);
        helper.assertTrue(sim.field().total() + sim.pool().count() == total,
                "leaves were lost: " + (sim.field().total() + sim.pool().count()) + " of " + total);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void litterFallsWhenSupportIsRemoved(GameTestHelper helper) {
        floor(helper);
        fill(helper, 2, 2, 4, 4, 1, Blocks.OAK_PLANKS.defaultBlockState());
        LeafSimulation sim = simulation(helper, 256, LeafListener.NONE);
        int[] cell = cellAt(helper, 3.5, 3.5);
        for (int n = 0; n < 5; n++) {
            sim.field().add(cell[0], cell[1], absoluteY(helper, 2.0), 0x6A8F3A, 0, 0L, 0);
        }
        fill(helper, 2, 2, 4, 4, 1, Blocks.AIR.defaultBlockState());
        sim.setLevel(helper.getLevel());
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                sim.blockChanged(helper.absolutePos(new BlockPos(x, 1, z)));
            }
        }
        run(sim, helper, 300);
        helper.assertTrue(sim.field().total() + sim.pool().count() == 5, "leaves were lost");
        assertAllBases(sim, helper, 1.0);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void placedBlockLiftsLitter(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int[] cell = cellAt(helper, 3.5, 3.5);
        for (int n = 0; n < 4; n++) {
            sim.field().add(cell[0], cell[1], absoluteY(helper, 1.0), 0x6A8F3A, 0, 0L, 0);
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
        sim.setLevel(helper.getLevel());
        sim.blockChanged(at);
        helper.assertTrue(Math.abs(baseOfOnlyCell(sim, helper) - 2.0) < EPSILON, "litter was not lifted onto the new block");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void rakingGathersAPile(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 256, LeafListener.NONE);
        int total = carpet(sim, helper, 2.5, 4);
        int[] cell = cellAt(helper, 3.5, 3.5);
        double y = absoluteY(helper, 1.0);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int before = pileAround(sim, cell, 2);
        for (int stroke = 0; stroke < 30; stroke++) {
            sim.rake(origin.getX() + 3.5, y, origin.getZ() + 3.5, 2.0F);
            run(sim, helper, 4);
        }
        run(sim, helper, 200);
        int after = pileAround(sim, cell, 2);
        helper.assertTrue(after > before * 2, "raking did not gather leaves: " + before + " -> " + after);
        helper.assertTrue(sim.field().total() + sim.pool().count() == total, "leaves were lost");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void whirlwindLiftsLeavesUp(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 512, LeafListener.NONE);
        carpet(sim, helper, 2.5, 4);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        sim.wind().addWhirlwind(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.5F, 6.0F, 0.3F, 600);
        double highest = 0.0;
        for (int t = 0; t < 120; t++) {
            run(sim, helper, 1);
            for (int i = 0; i < sim.pool().highWater(); i++) {
                if (sim.pool().state[i] != LeafPool.FREE) {
                    highest = Math.max(highest, sim.pool().y[i] - center.getY());
                }
            }
        }
        helper.assertTrue(highest > 2.5, "whirlwind lifted leaves only to " + highest);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void scoopedLeavesPourBackOut(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 1024, LeafListener.NONE);
        int total = carpet(sim, helper, 2.0, 8);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        double y = absoluteY(helper, 1.0);
        Armful armful = new Armful();
        int taken = 0;
        for (int stroke = 0; stroke < 5; stroke++) {
            taken += sim.scoop(origin.getX() + 3.5, y, origin.getZ() + 3.5, 0.9F, 40, origin.getX() + 3.5, y + 1.2, origin.getZ() + 2.5, armful);
            run(sim, helper, 4);
        }
        helper.assertTrue(taken >= 100 && armful.count() == taken, "scooped " + taken + ", carrying " + armful.count());
        helper.assertTrue(sim.field().total() == total - taken, "litter does not match the scoop");
        run(sim, helper, 20);
        while (armful.count() > 0) {
            sim.pour(origin.getX() + 1.5, y + 1.5, origin.getZ() + 1.5, 0.3F, 0.0F, 0.3F, 6, armful);
            run(sim, helper, 1);
        }
        run(sim, helper, 500);
        helper.assertTrue(sim.field().total() + sim.pool().count() == total, "leaves were lost while pouring");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void pouredLeavesFillABowl(GameTestHelper helper) {
        floor(helper);
        // A one block deep hollow in the middle of a raised floor.
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        helper.setBlock(3, 1, 3, Blocks.AIR.defaultBlockState());
        LeafSimulation sim = simulation(helper, 2048, LeafListener.NONE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Armful armful = filledArmful(sim, helper, 500);
        while (armful.count() > 0) {
            sim.pour(origin.getX() + 3.5, absoluteY(helper, 2.6), origin.getZ() + 3.5, 0.0F, -1.0F, 0.0F, 4, armful);
            run(sim, helper, 1);
        }
        run(sim, helper, 600);
        int[] hole = cellAt(helper, 3.0, 3.0);
        int inside = 0;
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                inside += sim.field().count(hole[0] + dx, hole[1] + dz);
            }
        }
        helper.assertTrue(sim.field().total() + sim.pool().count() == 500, "leaves were lost");
        helper.assertTrue(inside > 300, "the hollow holds only " + inside + " of 500 leaves");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void fullBowlRunsOver(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        helper.setBlock(3, 1, 3, Blocks.AIR.defaultBlockState());
        LeafSimulation sim = simulation(helper, 4096, LeafListener.NONE);
        int[] hole = cellAt(helper, 3.0, 3.0);
        double floorY = absoluteY(helper, 1.0);
        // Fill the hollow to the rim and heap a tall stack in its middle.
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                for (int n = 0; n < 83; n++) {
                    sim.field().add(hole[0] + dx, hole[1] + dz, floorY, 0x6A8F3A, 0, 0L, 0);
                }
            }
        }
        for (int n = 0; n < 70; n++) {
            sim.field().add(hole[0] + 1, hole[1] + 1, floorY, 0x6A8F3A, 0, 0L, 0);
            sim.field().add(hole[0] + 2, hole[1] + 1, floorY, 0x6A8F3A, 0, 0L, 0);
            sim.field().add(hole[0] + 1, hole[1] + 2, floorY, 0x6A8F3A, 0, 0L, 0);
            sim.field().add(hole[0] + 2, hole[1] + 2, floorY, 0x6A8F3A, 0, 0L, 0);
        }
        sim.field().queueRelax(hole[0] + 1, hole[1] + 1);
        run(sim, helper, 300);
        int outside = 0;
        for (int dx = -4; dx < 8; dx++) {
            for (int dz = -4; dz < 8; dz++) {
                if (dx < 0 || dx > 3 || dz < 0 || dz > 3) {
                    outside += sim.field().count(hole[0] + dx, hole[1] + dz);
                }
            }
        }
        helper.assertTrue(outside > 0, "nothing ran over the rim");
        helper.assertTrue(sim.field().total() + sim.pool().count() == 16 * 83 + 280, "leaves were lost");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafBlowerClearsAPath(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 2048, LeafListener.NONE);
        int total = carpet(sim, helper, 2.5, 6);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        double y = absoluteY(helper, 1.0);
        int[] near = cellAt(helper, 2.0, 3.5);
        int[] far = cellAt(helper, 5.75, 3.5);
        int nearBefore = pileAround(sim, near, 2);
        int farBefore = pileAround(sim, far, 2);
        // Aimed down the +X axis, slightly downwards, from the west edge of the carpet.
        float dirX = 0.96F;
        float dirY = -0.28F;
        for (int t = 0; t < 40; t++) {
            sim.beginTick(helper.getLevel(), origin.getX() + 3.5, y, origin.getZ() + 3.5);
            sim.blow(origin.getX() + 0.5, y + 0.8, origin.getZ() + 3.5, dirX, dirY, 0.0F, 7.0F, 0.47F, 0.55F);
            sim.finishTick();
        }
        run(sim, helper, 300);
        int nearAfter = pileAround(sim, near, 2);
        int farAfter = pileAround(sim, far, 2);
        helper.assertTrue(nearAfter < nearBefore / 2, "the blower did not clear the near side: " + nearBefore + " -> " + nearAfter);
        helper.assertTrue(farAfter > farBefore, "nothing was blown ahead: " + farBefore + " -> " + farAfter);
        helper.assertTrue(sim.field().total() + sim.pool().count() == total, "leaves were lost ");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafBagSucksUpLeaves(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 1024, LeafListener.NONE);
        int total = carpet(sim, helper, 2.0, 5);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        double y = absoluteY(helper, 1.0);
        Armful bag = new Armful();
        for (int t = 0; t < 60; t++) {
            sim.beginTick(helper.getLevel(), origin.getX() + 3.5, y, origin.getZ() + 3.5);
            sim.vacuum(origin.getX() + 1.0, y + 0.9, origin.getZ() + 3.5, 0.8F, -0.6F, 0.0F, 24, bag);
            sim.finishTick();
        }
        run(sim, helper, 40);
        helper.assertTrue(bag.count() > 60, "the bag took only " + bag.count() + " leaves");
        helper.assertTrue(sim.field().total() + sim.pool().count() + bag.count() == total, "leaves were lost");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafBagFillsAComposter(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.COMPOSTER);
        ItemStack bag = new ItemStack(ModItems.LEAF_BAG.get());
        bag.set(ModDataComponents.BAG_CONTENTS.get(), BagContents.EMPTY.add(100, 0x6A8F3A, 0));
        int layers = LeafBagItem.compost(helper.getLevel(), helper.absolutePos(pos), bag);
        helper.assertTrue(layers == 3, "composted " + layers + " layers from 100 leaves");
        helper.assertBlockProperty(pos, ComposterBlock.LEVEL, 3);
        helper.assertTrue(LeafBagItem.contents(bag).count() == 100 - 3 * LeafBagItem.COMPOST_COST, "wrong leaves left in the bag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bagContentsMixAndCap(GameTestHelper helper) {
        BagContents contents = BagContents.EMPTY.add(600, 0xFF0000, 1).add(600, 0x0000FF, 2);
        helper.assertTrue(contents.count() == BagContents.CAPACITY, "bag holds " + contents.count());
        helper.assertTrue((contents.color() & 0xFF) > 0 && (contents.color() >> 16 & 0xFF) > 0, "colors did not mix");
        helper.assertTrue(contents.remove(5000).count() == 0, "removing too much went negative");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void litterSurvivesSaving(GameTestHelper helper) throws IOException {
        LitterChunk chunk = new LitterChunk(5, -3);
        chunk.seedCell(17, 12, 64.5F, 0x123456, LeafShape.NEEDLE.ordinal());
        chunk.seedCell(4000, 3, 70.0F, 0x654321, LeafShape.BROAD.ordinal());
        chunk.top[17] = LitterField.encodeTop(0.3F, 0.7F, 1.0F, 0.12F, 4);
        chunk.topColor[17] = 0xABCDEF;
        chunk.markSeeded();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chunk.write(new DataOutputStream(bytes));
        LitterChunk read = LitterChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        helper.assertTrue(read.x == 5 && read.z == -3 && read.seeded() && read.total() == 15, "header differs");
        helper.assertTrue(Arrays.equals(read.count, chunk.count) && Arrays.equals(read.base, chunk.base)
                && Arrays.equals(read.color, chunk.color) && Arrays.equals(read.shape, chunk.shape)
                && Arrays.equals(read.top, chunk.top) && Arrays.equals(read.topColor, chunk.topColor), "cells differ");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------

    /** An armful of {@code leaves} leaves, scooped from a carpet laid and removed again outside the test box. */
    private static Armful filledArmful(LeafSimulation sim, GameTestHelper helper, int leaves) {
        Armful armful = new Armful();
        int[] corner = cellAt(helper, 3.5, 3.5);
        double y = absoluteY(helper, 1.0);
        LitterChunk chunk = sim.field().chunkAtCell(corner[0], corner[1]);
        int index = LitterField.index(corner[0], corner[1]);
        while (armful.count() < leaves) {
            sim.field().add(corner[0], corner[1], y, 0x6A8F3A, 0, 0L, 0);
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            sim.scoop(origin.getX() + 3.5, y, origin.getZ() + 3.5, 0.1F, 1, origin.getX() + 3.5, y + 1.0, origin.getZ() + 3.5, armful);
        }
        helper.assertTrue(chunk.count[index] == 0, "scoop left leaves behind");
        run(sim, helper, 20);
        return armful;
    }

    /** A floor and a flat crown of oak leaves under the ceiling of the box (open to the sky, for the heightmap). */
    private static void canopy(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 5, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
    }

    /**
     * Lets the crown from {@link #canopy} shed leaves for {@code ticks} ticks at a point in the year and returns how
     * many fell. With {@code colors}, it also counts the moving leaves at the end in autumn colors (into [0]) and all of
     * them (into [1]): the tree is pure green, so any red in a leaf is an autumn color.
     */
    private static int shed(GameTestHelper helper, float season, int ticks, int[] colors) {
        LeafSimulation sim = simulation(helper, 4096, LeafListener.NONE);
        sim.settings().spawnRadius = 4;
        SeasonCurve.apply(sim.settings(), season);
        int[] fallen = new int[1];
        LeafSpawner spawner = new LeafSpawner(new TreeLeaves() {
            @Override
            public int color(BlockState state, Level level, BlockPos pos) {
                fallen[0]++;
                return 0x00FF00;
            }

            @Override
            public LeafShape shape(BlockState state) {
                return LeafShape.BROAD;
            }
        });
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int t = 0; t < ticks; t++) {
            sim.tick(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            spawner.tick(helper.getLevel(), sim, center.getX() + 0.5, center.getZ() + 0.5);
        }
        if (colors != null) {
            LeafPool pool = sim.pool();
            for (int i = 0; i < pool.highWater(); i++) {
                if (pool.state[i] != LeafPool.FREE) {
                    colors[0] += (pool.color[i] >> 16 & 0xFF) > 0 ? 1 : 0;
                    colors[1]++;
                }
            }
        }
        return fallen[0];
    }

    /** A simulation without wind and with the litter chunks around the test loaded. */
    private static LeafSimulation simulation(GameTestHelper helper, int capacity, LeafListener listener) {
        LeafSettings settings = new LeafSettings();
        settings.windStrength = 0.0F;
        settings.windEvents = 0.0F;
        LeafSimulation sim = new LeafSimulation(settings, capacity, listener);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int cx = (origin.getX() >> 4) - 2; cx <= (origin.getX() >> 4) + 2; cx++) {
            for (int cz = (origin.getZ() >> 4) - 2; cz <= (origin.getZ() >> 4) + 2; cz++) {
                sim.field().put(new LitterChunk(cx, cz));
            }
        }
        return sim;
    }

    private static int spawn(LeafSimulation sim, GameTestHelper helper, double x, double y, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return sim.spawn(origin.getX() + x, origin.getY() + y, origin.getZ() + z, 0.0F, 0.0F, 0.0F, 0x6A8F3A, 0x6A8F3A, LeafShape.BROAD, 0,
                0.1F, false);
    }

    private static void run(LeafSimulation sim, GameTestHelper helper, int ticks) {
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int t = 0; t < ticks; t++) {
            sim.tick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
        }
    }

    /** Puts {@code leaves} leaves in every cell within {@code radius} blocks of the box center; returns the total. */
    private static int carpet(LeafSimulation sim, GameTestHelper helper, double radius, int leaves) {
        int[] center = cellAt(helper, 3.5, 3.5);
        int reach = (int) Math.ceil(radius * 4);
        double ground = absoluteY(helper, 1.0);
        int total = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if ((dx + 0.5) * (dx + 0.5) + (dz + 0.5) * (dz + 0.5) > reach * reach) {
                    continue;
                }
                for (int n = 0; n < leaves; n++) {
                    sim.field().add(center[0] + dx, center[1] + dz, ground, 0x6A8F3A, 0, 0L, 0);
                    total++;
                }
            }
        }
        return total;
    }

    private static int pileAround(LeafSimulation sim, int[] cell, int reach) {
        int sum = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                sum += sim.field().count(cell[0] + dx, cell[1] + dz);
            }
        }
        return sum;
    }

    private static double surface(LeafSimulation sim, int cellX, int cellZ, double ground) {
        double top = sim.field().top(cellX, cellZ);
        return Double.isNaN(top) ? ground : top;
    }

    /** Base height (relative to the test) of the only non-empty litter cell in the box. */
    private static double baseOfOnlyCell(LeafSimulation sim, GameTestHelper helper) {
        int[] corner = cellAt(helper, -1.0, -1.0);
        double base = Double.NaN;
        for (int dx = 0; dx < 9 * 4; dx++) {
            for (int dz = 0; dz < 9 * 4; dz++) {
                int cx = corner[0] + dx;
                int cz = corner[1] + dz;
                LitterChunk chunk = sim.field().chunkAtCell(cx, cz);
                int i = LitterField.index(cx, cz);
                if (chunk != null && chunk.count[i] > 0) {
                    helper.assertTrue(Double.isNaN(base), "more than one litter cell");
                    base = relativeY(helper, chunk.base[i]);
                }
            }
        }
        helper.assertTrue(!Double.isNaN(base), "no litter cell");
        return base;
    }

    /** Every non-empty litter cell in the box lies at this relative height. */
    private static void assertAllBases(LeafSimulation sim, GameTestHelper helper, double expected) {
        int[] corner = cellAt(helper, -1.0, -1.0);
        for (int dx = 0; dx < 9 * 4; dx++) {
            for (int dz = 0; dz < 9 * 4; dz++) {
                int cx = corner[0] + dx;
                int cz = corner[1] + dz;
                LitterChunk chunk = sim.field().chunkAtCell(cx, cz);
                int i = LitterField.index(cx, cz);
                if (chunk != null && chunk.count[i] > 0) {
                    double base = relativeY(helper, chunk.base[i]);
                    helper.assertTrue(Math.abs(base - expected) < EPSILON, "litter lies at y " + base + ", expected " + expected);
                }
            }
        }
    }

    private static int[] cellAt(GameTestHelper helper, double x, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new int[] {LitterField.cell(origin.getX() + x), LitterField.cell(origin.getZ() + z)};
    }

    private static double absoluteY(GameTestHelper helper, double y) {
        return helper.absolutePos(BlockPos.ZERO).getY() + y;
    }

    private static double relativeY(GameTestHelper helper, double y) {
        return y - helper.absolutePos(BlockPos.ZERO).getY();
    }

    private static void floor(GameTestHelper helper) {
        fill(helper, 0, 0, 6, 6, 0, Blocks.STONE.defaultBlockState());
    }

    private static void fill(GameTestHelper helper, int x0, int z0, int x1, int z1, int y, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                helper.setBlock(x, y, z, state);
            }
        }
    }
}
