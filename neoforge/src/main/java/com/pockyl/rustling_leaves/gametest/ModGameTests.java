package com.pockyl.rustling_leaves.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.network.RakePayload;
import com.pockyl.rustling_leaves.sim.Armful;
import com.pockyl.rustling_leaves.sim.LeafListener;
import com.pockyl.rustling_leaves.sim.LeafPalette;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.LeafSimulation;
import com.pockyl.rustling_leaves.sim.LeafSpawner;
import com.pockyl.rustling_leaves.sim.LitterChunk;
import com.pockyl.rustling_leaves.sim.LitterField;
import com.pockyl.rustling_leaves.sim.PileBlocks;
import com.pockyl.rustling_leaves.sim.SeasonCurve;
import com.pockyl.rustling_leaves.sim.TreeLeaves;
import com.pockyl.rustling_leaves.sim.WaterWaves;
import com.pockyl.rustling_leaves.sim.Wind;
import com.pockyl.rustling_leaves.sim.WindSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-game tests, run headless by {@code gradlew runGameTestServer}. The simulation only needs a {@code Level}, so it
 * runs here against real blocks; each test steps it synchronously. Tests use the 7x6x7 {@code box} structure with a
 * stone floor placed at y = 0, so the litter lies at relative height 1.
 */
public final class ModGameTests {
    private static final double EPSILON = 0.05;
    private static final float LAYER = LitterField.LAYER;
    /** Every test steps the simulation by itself within its first tick; the limit only catches a test that never ends. */
    private static final int MAX_TICKS = 100;
    private static final Identifier BOX = RustlingLeaves.id("box");
    private static final Identifier EMPTY = RustlingLeaves.id("empty");
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION,
            RustlingLeaves.MOD_ID);
    private static final List<Test> TESTS = new ArrayList<>();

    /** A test function, the structure it runs in and whether it is open to the sky. */
    private record Test(String name, Consumer<GameTestHelper> function, Identifier structure, boolean skyAccess) {
    }

    /** The body of a test; the save tests may throw while writing and reading litter in memory. */
    @FunctionalInterface
    private interface Body {
        void run(GameTestHelper helper) throws IOException;
    }

    static {
        test("mod_loads", ModGameTests::modLoads, EMPTY);
        test("server_relays_only_plausible_rakes", ModGameTests::serverRelaysOnlyPlausibleRakes, EMPTY);
        test("trees_shed_at_their_own_rate", ModGameTests::treesShedAtTheirOwnRate, EMPTY);
        test("season_curve_follows_the_year", ModGameTests::seasonCurveFollowsTheYear, EMPTY);
        test("leaves_fall_only_in_autumn", ModGameTests::leavesFallOnlyInAutumn, BOX, true);
        test("placed_leaves_do_not_shed_by_default", ModGameTests::placedLeavesDoNotShedByDefault, BOX, true);
        test("vanilla_leaf_particles_become_leaves", ModGameTests::vanillaLeafParticlesBecomeLeaves, BOX, true);
        test("pale_oak_leaves_stay_pale_in_autumn", ModGameTests::paleOakLeavesStayPaleInAutumn, EMPTY);
        test("late_autumn_leaves_fall_in_autumn_colors", ModGameTests::lateAutumnLeavesFallInAutumnColors, BOX, true);
        test("falling_leaf_joins_the_litter", ModGameTests::fallingLeafJoinsTheLitter, BOX);
        test("leaf_rests_on_slab", ModGameTests::leafRestsOnSlab, BOX);
        test("leaf_floats_on_water", ModGameTests::leafFloatsOnWater, BOX);
        test("floating_leaf_rides_the_waves", ModGameTests::floatingLeafRidesTheWaves, BOX);
        test("rain_wind_gives_only_the_heading", ModGameTests::rainWindGivesOnlyTheHeading, BOX);
        test("leaves_blow_with_another_mods_wind", ModGameTests::leavesBlowWithAnotherModsWind, BOX);
        test("soaked_leaf_sinks_to_the_bottom", ModGameTests::soakedLeafSinksToTheBottom, BOX);
        test("floating_leaves_drift_together", ModGameTests::floatingLeavesDriftTogether, BOX);
        test("water_pouring_onto_a_leaf_sinks_it", ModGameTests::waterPouringOntoALeafSinksIt, BOX);
        test("floating_leaves_stay_on_the_water_and_settle", ModGameTests::floatingLeavesStayOnTheWaterAndSettle, BOX);
        test("leaf_burns_in_lava", ModGameTests::leafBurnsInLava, BOX);
        test("pile_surface_stays_level_at_a_slab", ModGameTests::pileSurfaceStaysLevelAtASlab, BOX);
        test("pile_slumps_to_its_angle_of_repose", ModGameTests::pileSlumpsToItsAngleOfRepose, BOX);
        test("walking_through_a_pile_thins_it", ModGameTests::walkingThroughAPileThinsIt, BOX);
        test("sneaking_leaves_thin_carpet_alone", ModGameTests::sneakingLeavesThinCarpetAlone, BOX);
        test("jumping_into_a_pile_splashes", ModGameTests::jumpingIntoAPileSplashes, BOX);
        test("wind_charge_blows_litter_away", ModGameTests::windChargeBlowsLitterAway, BOX);
        test("litter_falls_when_support_is_removed", ModGameTests::litterFallsWhenSupportIsRemoved, BOX);
        test("full_block_smothers_litter", ModGameTests::fullBlockSmothersLitter, BOX);
        test("litter_stays_under_a_placed_slab", ModGameTests::litterStaysUnderAPlacedSlab, BOX);
        test("fence_is_as_high_as_it_is_drawn", ModGameTests::fenceIsAsHighAsItIsDrawn, BOX);
        test("raking_gathers_a_pile", ModGameTests::rakingGathersAPile, BOX);
        test("whirlwind_lifts_leaves_up", ModGameTests::whirlwindLiftsLeavesUp, BOX);
        test("scooped_leaves_pour_back_out", ModGameTests::scoopedLeavesPourBackOut, BOX);
        test("poured_leaves_fill_a_bowl", ModGameTests::pouredLeavesFillABowl, BOX);
        test("full_bowl_runs_over", ModGameTests::fullBowlRunsOver, BOX);
        test("leaf_blower_clears_a_path", ModGameTests::leafBlowerClearsAPath, BOX);
        test("leaf_bag_sucks_up_leaves", ModGameTests::leafBagSucksUpLeaves, BOX);
        test("pile_block_becomes_pinned_litter", ModGameTests::pileBlockBecomesPinnedLitter, BOX);
        test("pile_litter_follows_the_block", ModGameTests::pileLitterFollowsTheBlock, BOX);
        test("growing_pile_fills_with_falling_leaves", ModGameTests::growingPileFillsWithFallingLeaves, BOX);
        test("stacked_pile_blocks_make_one_tall_pile", ModGameTests::stackedPileBlocksMakeOneTallPile, BOX);
        test("pinned_leaves_are_not_saved", ModGameTests::pinnedLeavesAreNotSaved, BOX);
        test("black_pile_cells_are_repaired_on_load", ModGameTests::blackPileCellsAreRepairedOnLoad, EMPTY);
        test("leaf_lands_on_undrawn_pile", ModGameTests::leafLandsOnUndrawnPile, BOX);
        test("vanilla_leaf_litter_becomes_litter", ModGameTests::vanillaLeafLitterBecomesLitter, BOX);
        test("litter_survives_saving", ModGameTests::litterSurvivesSaving, EMPTY);
        test("old_litter_format_still_loads", ModGameTests::oldLitterFormatStillLoads, EMPTY);
        test("litter_rots_away_gradually", ModGameTests::litterRotsAwayGradually, BOX);
        test("litter_catches_up_on_the_time_away", ModGameTests::litterCatchesUpOnTheTimeAway, BOX);
        test("snow_covers_no_leaves", ModGameTests::snowCoversNoLeaves, BOX);
        test("pile_block_leaves_do_not_rot", ModGameTests::pileBlockLeavesDoNotRot, BOX);
        test("autumn_litter_is_gone_before_winter", ModGameTests::autumnLitterIsGoneBeforeWinter, BOX);
    }

    private ModGameTests() {
    }

    private static void test(String name, Body body, Identifier structure) {
        test(name, body, structure, false);
    }

    private static void test(String name, Body body, Identifier structure, boolean skyAccess) {
        TESTS.add(new Test(name, helper -> {
            try {
                body.run(helper);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, structure, skyAccess));
    }

    /** Registers the tests when the game test framework is enabled (dev runs and the game test server). */
    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Test test : TESTS) {
            FUNCTIONS.register(test.name(), test::function);
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(RustlingLeaves.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            Identifier id = RustlingLeaves.id(test.name());
            TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(environment, test.structure(), MAX_TICKS, 0, true,
                    Rotation.NONE, false, 1, 1, test.skyAccess());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    /** The server relays a rake stroke only from a sneaking player with a hoe or shovel who can reach the point. */
    public static void serverRelaysOnlyPlausibleRakes(GameTestHelper helper) {
        Vec3 eye = new Vec3(0.0, 1.62, 0.0);
        Vec3 near = new Vec3(3.0, 0.0, 2.0);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        helper.assertTrue(ModNetwork.canRake(eye, near, 4.5, true, hoe), "a sneaking player with a hoe rakes");
        helper.assertTrue(ModNetwork.canRake(eye, near, 4.5, true, new ItemStack(Items.WOODEN_SHOVEL)), "a shovel rakes");
        helper.assertFalse(ModNetwork.canRake(eye, near, 4.5, false, hoe), "no rake without sneaking");
        helper.assertFalse(ModNetwork.canRake(eye, near, 4.5, true, new ItemStack(Items.STICK)), "no rake with a stick");
        helper.assertFalse(ModNetwork.canRake(eye, new Vec3(20.0, 0.0, 0.0), 4.5, true, hoe), "no rake out of reach");
        helper.assertFalse(ModNetwork.canRake(eye, new Vec3(Double.NaN, 0.0, 0.0), 4.5, true, hoe), "no rake at NaN");
        helper.assertFalse(new RakePayload(Double.NaN, 0.0, 0.0).isFinite(), "NaN payloads are dropped");
        helper.succeed();
    }

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
    private static final float EARLY_WINTER = 9.5F;
    private static final float MID_WINTER = 10.5F;

    public static void seasonCurveFollowsTheYear(GameTestHelper helper) {
        LeafSettings settings = new LeafSettings();
        SeasonCurve.apply(settings, MID_SUMMER);
        helper.assertTrue(settings.seasonFallRate == settings.offSeasonFallRate && settings.seasonFallRate < 0.05F,
                "summer fall rate " + settings.seasonFallRate);
        helper.assertTrue(settings.seasonLitter < 0.25F && settings.seasonDecay == 0.0F, "summer litter " + settings.seasonLitter);
        float summerShare = settings.autumnShare();
        SeasonCurve.apply(settings, EARLY_AUTUMN);
        float early = settings.seasonFallRate;
        float earlyShare = settings.autumnShare();
        float earlyLitter = settings.seasonLitter;
        SeasonCurve.apply(settings, MID_AUTUMN);
        float mid = settings.seasonFallRate;
        float midShare = settings.autumnShare();
        SeasonCurve.apply(settings, LATE_AUTUMN);
        helper.assertTrue(0.5F < early && early < mid && mid < settings.seasonFallRate, "leaf fall does not grow through autumn");
        helper.assertTrue(settings.seasonFallRate == settings.autumnFallRate, "late autumn fall rate " + settings.seasonFallRate);
        helper.assertTrue(summerShare < earlyShare && earlyShare < midShare && midShare < settings.autumnShare(),
                "autumn colors do not grow through autumn");
        helper.assertTrue(settings.autumnShare() == 1.0F, "late autumn share " + settings.autumnShare());
        helper.assertTrue(earlyLitter < settings.seasonLitter && settings.seasonLitter == 1.0F, "late autumn litter " + settings.seasonLitter);
        helper.assertTrue(settings.seasonDecay > 0.0F, "the litter does not rot faster in late autumn");
        float lateDecay = settings.seasonDecay;
        SeasonCurve.apply(settings, EARLY_WINTER);
        helper.assertTrue(0.0F < settings.seasonFallRate && settings.seasonFallRate < early, "early winter fall rate " + settings.seasonFallRate);
        helper.assertTrue(settings.seasonDecay > lateDecay, "the litter does not rot faster in winter");
        SeasonCurve.apply(settings, MID_WINTER);
        helper.assertTrue(settings.seasonFallRate == settings.winterFallRate && settings.seasonPetalRate == settings.winterFallRate,
                "winter fall rate " + settings.seasonFallRate);
        helper.assertTrue(settings.seasonLitter == 0.0F, "new ground gets litter in winter");
        SeasonCurve.apply(settings, MID_SPRING);
        helper.assertTrue(settings.seasonFallRate < 0.05F && settings.seasonPetalRate > 1.0F, "spring: no leaves, more petals");
        // The season's rot follows the length of the sub-seasons: twice as long, half as fast.
        SeasonCurve.apply(settings, LATE_AUTUMN, SeasonCurve.DEFAULT_SUB_SEASON_TICKS * 2);
        helper.assertTrue(Math.abs(settings.seasonDecay * 2.0F - lateDecay) < lateDecay * 0.01F, "rot ignores the sub-season length");
        // No jumps: across every sub-season boundary (and the turn of the year) the values change only a little.
        for (int boundary = 0; boundary < SeasonCurve.SUB_SEASONS; boundary++) {
            SeasonCurve.apply(settings, Math.floorMod(boundary - 1, SeasonCurve.SUB_SEASONS) + 0.999F);
            float fall = settings.seasonFallRate;
            float colors = settings.seasonAutumnColors;
            float litter = settings.seasonLitter;
            SeasonCurve.apply(settings, boundary + 0.001F);
            helper.assertTrue(Math.abs(settings.seasonFallRate - fall) < 0.02F && Math.abs(settings.seasonAutumnColors - colors) < 0.02F
                    && Math.abs(settings.seasonLitter - litter) < 0.02F, "season jumps at sub-season " + boundary);
        }
        // The season multiplies the configured options instead of replacing them.
        settings.autumnColors = 0.0F;
        SeasonCurve.apply(settings, LATE_AUTUMN);
        helper.assertTrue(settings.autumnShare() == 0.0F, "autumn colors appear although they are off");
        settings.seasonalDecay = 0.0F;
        SeasonCurve.apply(settings, MID_WINTER);
        helper.assertTrue(settings.seasonDecay == 0.0F, "the litter rots in winter although seasonal rot is off");
        SeasonCurve.clear(settings);
        helper.assertTrue(settings.seasonFallRate == 1.0F && settings.seasonPetalRate == 1.0F && settings.seasonAutumnColors == 1.0F
                && settings.seasonLitter == 1.0F && settings.seasonDecay == 0.0F, "no season is not neutral");
        helper.succeed();
    }

    public static void leavesFallOnlyInAutumn(GameTestHelper helper) {
        canopy(helper);
        int spring = shed(helper, MID_SPRING, 400, null);
        int summer = shed(helper, MID_SUMMER, 400, null);
        int winter = shed(helper, MID_WINTER, 400, null);
        int autumn = shed(helper, LATE_AUTUMN, 400, null);
        helper.assertTrue(autumn > 200, "only " + autumn + " leaves fell in late autumn");
        helper.assertTrue(summer * 20 < autumn && spring * 20 < autumn, spring + " leaves fell in spring, " + summer + " in summer, "
                + autumn + " in late autumn");
        helper.assertTrue(winter == 0, winter + " leaves fell in winter");
        helper.succeed();
    }

    public static void placedLeavesDoNotShedByDefault(GameTestHelper helper) {
        canopy(helper);
        int placed = shed(helper, Float.NaN, 200, null, false);
        int allowed = shed(helper, Float.NaN, 200, null, true);
        helper.assertTrue(placed == 0, placed + " leaves fell from placed leaves");
        helper.assertTrue(allowed > 20, "only " + allowed + " leaves fell with placed leaves allowed");
        helper.succeed();
    }

    /**
     * Since 1.21.5 every leaves block drops vanilla falling leaves. A cherry petal becomes one simulated petal at its spot
     * (times the fall rate) and the canopy spawner leaves the calm fall of cherries to them, as on 1.21.1; the other
     * trees keep the canopy spawner's calm fall and their vanilla particle is only left out.
     */
    public static void vanillaLeafParticlesBecomeLeaves(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 4096, LeafListener.NONE);
        sim.settings().spawnRadius = 4;
        sim.settings().placedLeaves = true;
        SeasonCurve.clear(sim.settings());
        int[] fallen = new int[1];
        TreeLeaves trees = new TreeLeaves() {
            @Override
            public int color(BlockState state, Level level, BlockPos pos) {
                fallen[0]++;
                return 0x00FF00;
            }

            @Override
            public LeafShape shape(BlockState state) {
                return state.is(Blocks.CHERRY_LEAVES) ? LeafShape.PETAL : LeafShape.BROAD;
            }
        };
        helper.assertTrue(trees.dropsVanillaLeaves(Blocks.CHERRY_LEAVES.defaultBlockState()), "cherry leaves drop no petals");
        helper.assertFalse(trees.dropsVanillaLeaves(Blocks.PALE_OAK_LEAVES.defaultBlockState()), "pale oak leaves drop petals");
        helper.assertFalse(trees.dropsVanillaLeaves(Blocks.OAK_LEAVES.defaultBlockState()), "oak leaves drop petals");
        LeafSpawner spawner = new LeafSpawner(trees);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos crown = helper.absolutePos(new BlockPos(3, 5, 3));
        // Cherries: no calm fall from the canopy spawner, every vanilla petal becomes one of ours.
        fill(helper, 0, 0, 6, 6, 5, Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        for (int t = 0; t < 200; t++) {
            sim.tick(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            spawner.tick(helper.getLevel(), sim, center.getX() + 0.5, center.getZ() + 0.5);
        }
        helper.assertTrue(fallen[0] == 0, fallen[0] + " petals fell in calm air besides the vanilla ones");
        BlockState cherry = helper.getLevel().getBlockState(crown);
        helper.assertTrue(spawner.replaceVanillaLeaf(helper.getLevel(), sim, cherry, crown), "the vanilla petal still shows");
        helper.assertTrue(sim.pool().count() == 1, sim.pool().count() + " petals for one vanilla petal");
        LeafPool pool = sim.pool();
        for (int i = 0; i < pool.highWater(); i++) {
            if (pool.state[i] != LeafPool.FREE) {
                helper.assertTrue(pool.x[i] >= crown.getX() && pool.x[i] <= crown.getX() + 1 && pool.z[i] >= crown.getZ()
                        && pool.z[i] <= crown.getZ() + 1 && Math.abs(pool.y[i] - crown.getY()) < 0.1, "the petal is not under its block");
            }
        }
        sim.settings().fallRate = 2.0F;
        spawner.replaceVanillaLeaf(helper.getLevel(), sim, cherry, crown);
        helper.assertTrue(sim.pool().count() == 3, "fall rate 2 did not drop two petals");
        // Oaks: the vanilla leaf is only left out, the canopy spawner drops the calm fall itself.
        sim.settings().fallRate = 1.0F;
        fill(helper, 0, 0, 6, 6, 5, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        BlockState oak = helper.getLevel().getBlockState(crown);
        helper.assertTrue(spawner.replaceVanillaLeaf(helper.getLevel(), sim, oak, crown), "the vanilla oak leaf still shows");
        helper.assertTrue(sim.pool().count() == 3, "a vanilla oak leaf became a leaf");
        fallen[0] = 0;
        for (int t = 0; t < 200; t++) {
            sim.tick(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            spawner.tick(helper.getLevel(), sim, center.getX() + 0.5, center.getZ() + 0.5);
        }
        helper.assertTrue(fallen[0] > 20, "only " + fallen[0] + " oak leaves fell in calm air");
        // Falling leaves switched off: the vanilla particles are back.
        sim.settings().fallRate = 0.0F;
        helper.assertFalse(spawner.replaceVanillaLeaf(helper.getLevel(), sim, oak, crown), "the vanilla oak leaf is gone with falling leaves off");
        helper.succeed();
    }

    /** Pale oak leaves (grey-green) keep their colour when every other tree turns golden and red. */
    public static void paleOakLeavesStayPaleInAutumn(GameTestHelper helper) {
        int paleOak = 0x858B83;
        int oak = 0x2E750E;
        int turned = 0;
        for (int n = 0; n < 200; n++) {
            long hash = LeafPalette.hash(n, 7, 11);
            int pale = LeafPalette.vary(paleOak, hash, 1.0F, LeafShape.BROAD);
            helper.assertTrue(LeafPalette.saturation(pale) < 0.15F, String.format("a pale oak leaf turned %06X", pale));
            int green = LeafPalette.vary(oak, hash, 1.0F, LeafShape.BROAD);
            turned += (green >> 16 & 0xFF) > (green >> 8 & 0xFF) ? 1 : 0;
        }
        helper.assertTrue(turned > 150, "only " + turned + " of 200 oak leaves turned");
        helper.succeed();
    }

    public static void lateAutumnLeavesFallInAutumnColors(GameTestHelper helper) {
        canopy(helper);
        // Without seasons a part of the leaves falls in autumn colors (in summer hardly a leaf falls to compare with).
        int[] none = new int[2];
        shed(helper, Float.NaN, 100, none);
        int[] autumn = new int[2];
        shed(helper, LATE_AUTUMN, 100, autumn);
        helper.assertTrue(none[1] > 20 && autumn[1] > 20, "too few falling leaves to compare");
        helper.assertTrue(none[0] < none[1] * 0.6F, none[0] + " of " + none[1] + " leaves in autumn colors without seasons");
        helper.assertTrue(autumn[0] == autumn[1], autumn[0] + " of " + autumn[1] + " leaves in autumn colors in late autumn");
        helper.succeed();
    }

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

    public static void floatingLeafRidesTheWaves(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 1, 5, 5, 1, Blocks.WATER.defaultBlockState());
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        // A wave field that rises towards +x, as another mod might draw it: 0.6 blocks over the still water mid-pond.
        double originX = helper.absolutePos(BlockPos.ZERO).getX();
        WaterWaves waves = (x, z) -> (float) (0.6 + 0.3 * (x - originX - 3.5));
        sim.setWaves(waves);
        int leaf = spawn(sim, helper, 3.5, 4.5, 3.5);
        LeafPool pool = sim.pool();
        double lowest = Double.MAX_VALUE;
        for (int t = 0; t < 300; t++) {
            if (pool.state[leaf] == LeafPool.FALLING) {
                lowest = Math.min(lowest, relativeY(helper, pool.y[leaf]));
            }
            run(sim, helper, 1);
        }
        helper.assertTrue(pool.state[leaf] == LeafPool.FLOATING, "leaf is not floating, state " + pool.state[leaf]);
        double y = relativeY(helper, pool.y[leaf]);
        helper.assertTrue(y > 1.8 && y < 2.0, "the simulated leaf left the still water: y " + y);
        double drawn = y + pool.lift[leaf];
        double crest = 1.0 + 8.0 / 9.0 + waves.lift(pool.x[leaf], pool.z[leaf]);
        helper.assertTrue(Math.abs(drawn - crest) < 0.06, "the leaf is drawn at y " + drawn + ", the wave is at " + crest);
        helper.assertTrue(lowest > 2.1, "the leaf fell into the wave down to y " + lowest + " before it landed on it");
        float tilt = Mth.sqrt(pool.roll[leaf] * pool.roll[leaf] + pool.pitch[leaf] * pool.pitch[leaf]);
        helper.assertTrue(tilt > 0.15F, "the leaf does not tilt with the wave: " + tilt);
        sim.setWaves(WaterWaves.NONE);
        run(sim, helper, 20);
        helper.assertTrue(pool.state[leaf] == LeafPool.FLOATING && pool.lift[leaf] == 0.0F,
                "without the waves the leaf is not back on the still water: lift " + pool.lift[leaf]);
        helper.succeed();
    }

    public static void rainWindGivesOnlyTheHeading(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        sim.settings().windStrength = 1.0F;
        run(sim, helper, 1);
        float own = sim.wind().base();
        // The course of Petrichor's and Hoarfrost's wind: a heading (towards +z, south) without a speed.
        sim.setWindSource(out -> {
            out[0] = 0.0F;
            out[1] = 1.0F;
            out[2] = Float.NaN;
            return true;
        });
        run(sim, helper, 1);
        Wind wind = sim.wind();
        helper.assertTrue(Math.abs(wind.directionX()) < 1.0E-4F && Math.abs(wind.directionZ() - 1.0F) < 1.0E-4F,
                "the wind does not blow south: " + wind.directionX() + ", " + wind.directionZ());
        helper.assertTrue(own > 0.0F && Math.abs(wind.base() - own) < 1.0E-6F, "the wind speed changed: " + own + " -> " + wind.base());
        helper.succeed();
    }

    public static void leavesBlowWithAnotherModsWind(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        sim.settings().windStrength = 1.0F;
        // A fresh wind towards -z (north), as another mod might sync it: the heading need not be a unit vector.
        float[] speed = {0.09F};
        sim.setWindSource(out -> {
            out[0] = 0.0F;
            out[1] = -2.0F;
            out[2] = speed[0];
            return true;
        });
        run(sim, helper, 1);
        Wind wind = sim.wind();
        helper.assertTrue(Math.abs(wind.directionX()) < 1.0E-4F && Math.abs(wind.directionZ() + 1.0F) < 1.0E-4F,
                "the wind does not blow north: " + wind.directionX() + ", " + wind.directionZ());
        BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
        float strong = wind.speed(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        speed[0] = 0.009F;
        run(sim, helper, 1);
        float weak = wind.speed(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        helper.assertTrue(strong > 0.03F && weak < strong * 0.2F, "the wind speed does not follow: " + strong + " then " + weak);

        // A single leaf glides sideways as much as the wind carries it where the wind is weak (low test worlds), so the
        // mean drift of a few leaves is checked.
        speed[0] = 0.09F;
        LeafPool pool = sim.pool();
        int[] leaves = new int[8];
        double[] start = new double[leaves.length * 2];
        for (int n = 0; n < leaves.length; n++) {
            leaves[n] = spawn(sim, helper, 3.5, 5.5, 3.5);
            start[n * 2] = pool.x[leaves[n]];
            start[n * 2 + 1] = pool.z[leaves[n]];
        }
        run(sim, helper, 30);
        double driftX = 0.0;
        double driftZ = 0.0;
        for (int n = 0; n < leaves.length; n++) {
            driftX += (pool.x[leaves[n]] - start[n * 2]) / leaves.length;
            driftZ += (pool.z[leaves[n]] - start[n * 2 + 1]) / leaves.length;
        }
        helper.assertTrue(driftZ < -0.5 && Math.abs(driftX) < -driftZ * 0.5, "the leaves drifted " + driftX + ", " + driftZ);

        sim.setWindSource(WindSource.NONE);
        run(sim, helper, 1);
        helper.assertTrue(wind.directionZ() > -0.9F, "the own wind does not come back: " + wind.directionZ());
        helper.succeed();
    }

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

    public static void pileSurfaceStaysLevelAtASlab(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int[] onSlab = cellAt(helper, 3.1, 3.4);
        int[] onFloor = cellAt(helper, 2.9, 3.4);
        double slabTop = absoluteY(helper, 1.5);
        double floorTop = absoluteY(helper, 1.0);
        for (int n = 0; n < 10; n++) {
            sim.field().add(onSlab[0], onSlab[1], slabTop, 0x6A8F3A, 0, 0L, 0);
            sim.field().add(onFloor[0], onFloor[1], floorTop, 0x6A8F3A, 0, 0L, 0);
        }
        double slabPile = slabTop + 10 * LAYER;
        double floorPile = floorTop + 10 * LAYER;
        // The corner between the two cells, seen from each: the surface must not bend over the half-block step.
        double fromSlab = sim.field().cornerHeight(onSlab[0], onSlab[1], slabTop, slabPile);
        double fromFloor = sim.field().cornerHeight(onSlab[0], onSlab[1], floorTop, floorPile);
        helper.assertTrue(fromSlab >= slabTop, "the pile on the slab dips towards the floor: " + (fromSlab - slabTop));
        helper.assertTrue(fromFloor <= floorPile + 1.0E-6, "the pile on the floor climbs the slab: " + (fromFloor - floorPile));
        helper.succeed();
    }

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

    public static void fullBlockSmothersLitter(GameTestHelper helper) {
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
        helper.assertTrue(sim.field().total() == 0, "leaves survived inside a full block: " + sim.field().total());
        helper.succeed();
    }

    public static void litterStaysUnderAPlacedSlab(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int[] cell = cellAt(helper, 3.5, 3.5);
        for (int n = 0; n < 4; n++) {
            sim.field().add(cell[0], cell[1], absoluteY(helper, 1.0), 0x6A8F3A, 0, 0L, 0);
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(at, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        sim.setLevel(helper.getLevel());
        sim.blockChanged(at);
        run(sim, helper, 100);
        helper.assertTrue(sim.field().total() + sim.pool().count() == 4, "leaves were lost under the slab");
        helper.assertTrue(Math.abs(baseOfOnlyCell(sim, helper) - 1.0) < EPSILON, "litter was lifted onto the slab");
        LitterField.Pose pose = new LitterField.Pose();
        sim.field().pose(sim.field().chunkAtCell(cell[0], cell[1]), cell[0], cell[1], 3, pose);
        helper.assertTrue(relativeY(helper, pose.y) < 1.2, "the drawn leaf climbed onto the slab: " + relativeY(helper, pose.y));
        helper.succeed();
    }

    public static void fenceIsAsHighAsItIsDrawn(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.OAK_FENCE.defaultBlockState());
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        sim.setLevel(helper.getLevel());
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        double x = origin.getX() + 3.5;
        double z = origin.getZ() + 3.5;
        double top = sim.solidTop(x, absoluteY(helper, 1.9), z);
        helper.assertTrue(Math.abs(relativeY(helper, top) - 2.0) < EPSILON, "a leaf would rest above the fence post: " + relativeY(helper, top));
        double ground = sim.groundBelow(x, absoluteY(helper, 4.0), z, 4);
        helper.assertTrue(Math.abs(relativeY(helper, ground) - 2.0) < EPSILON, "the ground over the fence is not its top: " + relativeY(helper, ground));
        helper.succeed();
    }

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

    /** Snow layers stand in for Immersive Weathering's leaf piles in tests: a block with 1..8 layers. */
    private static final PileBlocks SNOW_PILES = state -> state.is(Blocks.SNOW) ? state.getValue(SnowLayerBlock.LAYERS) : 0;
    private static final TreeLeaves PILE_LOOK = new TreeLeaves() {
        @Override
        public int color(BlockState state, Level level, BlockPos pos) {
            return 0x8A6A2A;
        }

        @Override
        public LeafShape shape(BlockState state) {
            return LeafShape.BROAD;
        }
    };

    public static void pileBlockBecomesPinnedLitter(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
        LeafSimulation sim = pileSimulation(helper, true);
        int[] middle = cellAt(helper, 3.4, 3.4);
        int[] corner = cellAt(helper, 3.1, 3.1);
        int pinned = pinnedIn(sim, helper, 3, 3);
        helper.assertTrue(sim.field().count(middle[0], middle[1]) > 25, "the middle of the pile holds " + sim.field().count(middle[0], middle[1]));
        helper.assertTrue(sim.field().count(corner[0], corner[1]) < sim.field().count(middle[0], middle[1]), "the pile does not slope to its edge");
        helper.assertTrue(pinned == boxTotal(sim, helper), "pinned " + pinned + " of " + boxTotal(sim, helper) + " leaves");
        assertAllBases(sim, helper, 1.0);
        // Feet, a blower-like blast and a bag move nothing of it: the pile is there as long as the block.
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        for (int t = 0; t < 20; t++) {
            sim.beginTick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
            sim.disturb(center.getX() + 0.2 + t * 0.03, center.getY(), center.getZ() + 0.5, 0.25, 0.0, 0.0, 0.6F, 1.8F, false, 0.0F);
            sim.finishTick();
        }
        sim.explode(center.getX() + 0.5, center.getY() + 0.3, center.getZ() + 0.5, 1.2F, true);
        Armful bag = new Armful();
        sim.beginTick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
        sim.vacuum(center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5, 0.0F, -1.0F, 0.0F, 24, bag);
        sim.finishTick();
        run(sim, helper, 100);
        helper.assertTrue(bag.count() == 0 && sim.pool().count() == 0, "pinned leaves were moved");
        helper.assertTrue(pinnedIn(sim, helper, 3, 3) == pinned && boxTotal(sim, helper) == pinned, "the pile changed");
        // Loose leaves on top still behave as usual.
        int[] cell = cellAt(helper, 3.5, 3.5);
        for (int n = 0; n < 6; n++) {
            sim.field().add(cell[0], cell[1], 0.0, 0x6A8F3A, 0, 0L, 0);
        }
        sim.beginTick(helper.getLevel(), center.getX(), center.getY(), center.getZ());
        sim.disturb(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.3, 0.0, 0.0, 0.6F, 1.8F, false, 0.0F);
        sim.finishTick();
        helper.assertTrue(boxTotal(sim, helper) + sim.pool().count() == pinned + 6, "leaves were lost");
        helper.assertTrue(sim.field().loose(cell[0], cell[1]) < 6, "loose leaves on the pile did not move");
        helper.succeed();
    }

    public static void pileLitterFollowsTheBlock(GameTestHelper helper) {
        floor(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
        LeafSimulation sim = pileSimulation(helper, true);
        int four = pinnedIn(sim, helper, 3, 3);
        BlockPos at = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 6));
        sim.pileChanged(at, false, true);
        int six = pinnedIn(sim, helper, 3, 3);
        helper.assertTrue(six > four, "the pile did not grow: " + four + " -> " + six);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        sim.pileChanged(at, false, true);
        int two = pinnedIn(sim, helper, 3, 3);
        helper.assertTrue(two < four && boxTotal(sim, helper) == two && sim.pool().count() == 0,
                "a rotting pile did not just shrink: " + four + " -> " + two + ", " + boxTotal(sim, helper) + " in the box");
        // A neighboring pile joins: the shared edge rises instead of sloping down to the ground.
        int[] edge = cellAt(helper, 3.9, 3.5);
        int alone = sim.field().count(edge[0], edge[1]);
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        sim.pileChanged(helper.absolutePos(new BlockPos(4, 1, 3)), false, true);
        helper.assertTrue(sim.field().count(edge[0], edge[1]) > alone, "piles next to each other do not merge");
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.AIR.defaultBlockState());
        sim.pileChanged(helper.absolutePos(new BlockPos(4, 1, 3)), false, true);
        // Broken: a few leaves fly up and land as loose litter, the rest goes with the block.
        helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        sim.pileChanged(at, true, true);
        int flying = sim.pool().count();
        helper.assertTrue(flying > 10 && flying <= 16 * 3, flying + " leaves flew up from the broken pile");
        run(sim, helper, 300);
        helper.assertTrue(pinnedIn(sim, helper, 3, 3) == 0 && boxTotal(sim, helper) + sim.pool().count() == flying, "leaves are left pinned or lost");
        helper.succeed();
    }

    public static void growingPileFillsWithFallingLeaves(GameTestHelper helper) {
        floor(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        LeafSimulation sim = pileSimulation(helper, true);
        int before = pinnedIn(sim, helper, 3, 3);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5));
        sim.pileChanged(helper.absolutePos(pos), false, false);
        helper.assertTrue(pinnedIn(sim, helper, 3, 3) == before, "the grown pile got its leaves out of nowhere");
        run(sim, helper, 20);
        helper.assertTrue(sim.pool().count() > 0, "no leaves fall onto the growing pile");
        // Long enough for every round of leaves the pile asks for: a few of each round slide off its edges.
        run(sim, helper, 1000);
        int after = pinnedIn(sim, helper, 3, 3);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        sim.pileChanged(helper.absolutePos(pos), false, true);
        helper.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5));
        sim.pileChanged(helper.absolutePos(pos), false, true);
        int full = pinnedIn(sim, helper, 3, 3);
        helper.assertTrue(after > before + (full - before) * 0.8, "the pile filled up to " + after + " of " + full + " leaves (from " + before + ")");
        helper.succeed();
    }

    public static void stackedPileBlocksMakeOneTallPile(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8));
        helper.setBlock(3, 2, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
        LeafSimulation sim = pileSimulation(helper, true);
        int[] middle = cellAt(helper, 3.4, 3.4);
        int count = sim.field().count(middle[0], middle[1]);
        helper.assertTrue(count > 80, "the middle of a pile 1.5 blocks high holds only " + count + " leaves");
        assertAllBases(sim, helper, 1.0);
        // The top block grows: the same column, now taller.
        helper.setBlock(3, 2, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 6));
        sim.pileChanged(helper.absolutePos(new BlockPos(3, 2, 3)), false, true);
        helper.assertTrue(sim.field().count(middle[0], middle[1]) > count, "the upper pile block did not raise the pile");
        // The top block goes: the pile is one block high again.
        helper.setBlock(3, 2, 3, Blocks.AIR.defaultBlockState());
        sim.pileChanged(helper.absolutePos(new BlockPos(3, 2, 3)), false, true);
        helper.assertTrue(sim.field().count(middle[0], middle[1]) < count, "the pile did not lose its upper block");
        helper.assertTrue(pinnedIn(sim, helper, 3, 3) > 0, "the lower pile block was lost too");
        helper.succeed();
    }

    public static void pinnedLeavesAreNotSaved(GameTestHelper helper) throws IOException {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
        LeafSimulation sim = pileSimulation(helper, true);
        int[] cell = cellAt(helper, 3.5, 3.5);
        for (int n = 0; n < 5; n++) {
            sim.field().add(cell[0], cell[1], 0.0, 0x6A8F3A, 0, 0L, 0);
        }
        LitterChunk chunk = sim.field().chunkAtCell(cell[0], cell[1]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chunk.write(new DataOutputStream(bytes));
        LitterChunk read = LitterChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        helper.assertTrue(read.total() == 5, "saved " + read.total() + " leaves instead of the 5 loose ones");
        // Turning the option off drops the pinned leaves and makes the block solid for falling leaves again.
        sim.setPiles(SNOW_PILES, PILE_LOOK, false);
        helper.assertTrue(boxTotal(sim, helper) == 5 && pinnedIn(sim, helper, 3, 3) == 0, "pinned leaves stayed: " + boxTotal(sim, helper));
        helper.succeed();
    }

    public static void blackPileCellsAreRepairedOnLoad(GameTestHelper helper) throws IOException {
        // Natural piles seeded before 2.3.0 could get color 0 (drawn black) next to the colored carpet around them.
        LitterChunk chunk = new LitterChunk(0, 0);
        chunk.seedCell(LitterChunk.index(10, 10), 4, 64.0F, 0x39701F, LeafShape.ROUND.ordinal());
        chunk.seedCell(LitterChunk.index(12, 11), 9, 64.0F, 0, 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chunk.write(new DataOutputStream(bytes));
        LitterChunk read = LitterChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        int i = LitterChunk.index(12, 11);
        helper.assertTrue(read.count[i] == 9 && read.color[i] == 0x39701F && read.shape[i] == LeafShape.ROUND.ordinal(),
                "black cell kept color " + Integer.toHexString(read.color[i]) + ", shape " + read.shape[i]);
        helper.succeed();
    }

    public static void leafLandsOnUndrawnPile(GameTestHelper helper) {
        floor(helper);
        fill(helper, 2, 2, 4, 4, 1, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
        LeafSimulation sim = pileSimulation(helper, false);
        helper.assertTrue(sim.field().total() == 0, "an undrawn pile became litter");
        spawn(sim, helper, 3.5, 3.0, 3.5);
        run(sim, helper, 200);
        double base = baseOfOnlyCell(sim, helper);
        helper.assertTrue(Math.abs(base - 1.5) < EPSILON, "the leaf landed at " + base + ", not on top of the pile");
        helper.succeed();
    }

    /**
     * Vanilla leaf litter becomes pinned litter on the quarters of the block its segments cover (the first one left of
     * its facing, each further one a quarter turn counter-clockwise, like its model), a thin layer like the block, and
     * follows the block.
     */
    public static void vanillaLeafLitterBecomesLitter(GameTestHelper helper) {
        floor(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, leafLitter(Direction.NORTH, 1));
        LeafSimulation sim = pileSimulation(helper, PileBlocks.LEAF_LITTER, true);
        int pinned = pinnedIn(sim, helper, 3, 3);
        helper.assertTrue(quarter(sim, helper, 0, 0) == pinned && pinned > 0, "one segment: " + pinned + " leaves, "
                + quarter(sim, helper, 0, 0) + " of them in the north-west quarter");
        helper.assertTrue(pinned == boxTotal(sim, helper), "pinned " + pinned + " of " + boxTotal(sim, helper) + " leaves");
        BlockPos at = helper.absolutePos(pos);
        helper.setBlock(pos, leafLitter(Direction.NORTH, 2));
        sim.pileChanged(at, false, true);
        helper.assertTrue(quarter(sim, helper, 0, 0) > 0 && quarter(sim, helper, 0, 1) > 0 && quarter(sim, helper, 1, 0) == 0
                && quarter(sim, helper, 1, 1) == 0, "two segments facing north do not cover the west half");
        helper.setBlock(pos, leafLitter(Direction.EAST, 1));
        sim.pileChanged(at, false, true);
        helper.assertTrue(quarter(sim, helper, 1, 0) == pinnedIn(sim, helper, 3, 3) && quarter(sim, helper, 1, 0) > 0,
                "one segment facing east is not in the north-east quarter");
        helper.setBlock(pos, leafLitter(Direction.EAST, 4));
        sim.pileChanged(at, false, true);
        int full = pinnedIn(sim, helper, 3, 3);
        int thickest = 0;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                int[] cell = cellAt(helper, 3 + a * 0.25 + 0.1, 3 + b * 0.25 + 0.1);
                thickest = Math.max(thickest, sim.field().count(cell[0], cell[1]));
                helper.assertTrue(sim.field().count(cell[0], cell[1]) > 0, "four segments leave a cell empty");
            }
        }
        helper.assertTrue(thickest * LAYER < 0.13, "leaf litter became a pile " + thickest + " leaves high");
        // Broken: a few leaves fly up and land as loose litter, the rest goes with the block.
        helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        sim.pileChanged(at, true, true);
        int flying = sim.pool().count();
        helper.assertTrue(flying > 0 && flying < full, flying + " of " + full + " leaves flew up from the broken leaf litter");
        run(sim, helper, 300);
        helper.assertTrue(pinnedIn(sim, helper, 3, 3) == 0 && boxTotal(sim, helper) + sim.pool().count() == flying,
                "leaves are left pinned or lost");
        helper.succeed();
    }

    private static BlockState leafLitter(Direction facing, int segments) {
        return Blocks.LEAF_LITTER.defaultBlockState().setValue(LeafLitterBlock.FACING, facing).setValue(LeafLitterBlock.AMOUNT, segments);
    }

    /** Pinned leaves in one quarter of the block at (3, 1, 3): {@code qx} 0 west, 1 east; {@code qz} 0 north, 1 south. */
    private static int quarter(LeafSimulation sim, GameTestHelper helper, int qx, int qz) {
        int[] corner = cellAt(helper, 3, 3);
        int sum = 0;
        for (int a = qx * 2; a < qx * 2 + 2; a++) {
            for (int b = qz * 2; b < qz * 2 + 2; b++) {
                LitterChunk chunk = sim.field().chunkAtCell(corner[0] + a, corner[1] + b);
                sum += chunk.pinned[LitterField.index(corner[0] + a, corner[1] + b)];
            }
        }
        return sum;
    }

    /** A simulation that knows {@link #SNOW_PILES}, with the piles in the box already scanned. */
    private static LeafSimulation pileSimulation(GameTestHelper helper, boolean draw) {
        return pileSimulation(helper, SNOW_PILES, draw);
    }

    private static LeafSimulation pileSimulation(GameTestHelper helper, PileBlocks piles, boolean draw) {
        LeafSimulation sim = simulation(helper, 512, LeafListener.NONE);
        sim.setLevel(helper.getLevel());
        sim.setPiles(piles, PILE_LOOK, draw);
        for (LitterChunk chunk : sim.field().chunks()) {
            sim.scanPiles(chunk);
        }
        return sim;
    }

    /** Leaves in the litter of the box (other tests run next to it). */
    private static int boxTotal(LeafSimulation sim, GameTestHelper helper) {
        int[] corner = cellAt(helper, -1.0, -1.0);
        int sum = 0;
        for (int dx = 0; dx < 9 * 4; dx++) {
            for (int dz = 0; dz < 9 * 4; dz++) {
                sum += sim.field().count(corner[0] + dx, corner[1] + dz);
            }
        }
        return sum;
    }

    /** Pinned leaves in the cells of a block of the box. */
    private static int pinnedIn(LeafSimulation sim, GameTestHelper helper, int x, int z) {
        int[] corner = cellAt(helper, x, z);
        int sum = 0;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                LitterChunk chunk = sim.field().chunkAtCell(corner[0] + a, corner[1] + b);
                sum += chunk.pinned[LitterField.index(corner[0] + a, corner[1] + b)];
            }
        }
        return sum;
    }

    public static void litterSurvivesSaving(GameTestHelper helper) throws IOException {
        LitterChunk chunk = new LitterChunk(5, -3);
        chunk.seedCell(17, 12, 64.5F, 0x123456, LeafShape.NEEDLE.ordinal());
        chunk.seedCell(4000, 3, 70.0F, 0x654321, LeafShape.BROAD.ordinal());
        chunk.top[17] = LitterField.encodeTop(0.3F, 0.7F, 1.0F, 0.12F, 4);
        chunk.topColor[17] = 0xABCDEF;
        chunk.markSeeded(123456L);
        chunk.setDecayedAt(234567L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chunk.write(new DataOutputStream(bytes));
        LitterChunk read = LitterChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        helper.assertTrue(read.x == 5 && read.z == -3 && read.seeded() && read.total() == 15, "header differs");
        helper.assertTrue(read.seededAt() == 123456L && read.decayedAt() == 234567L, "times differ");
        helper.assertTrue(Arrays.equals(read.count, chunk.count) && Arrays.equals(read.base, chunk.base)
                && Arrays.equals(read.color, chunk.color) && Arrays.equals(read.shape, chunk.shape)
                && Arrays.equals(read.top, chunk.top) && Arrays.equals(read.topColor, chunk.topColor), "cells differ");
        helper.succeed();
    }

    public static void oldLitterFormatStillLoads(GameTestHelper helper) throws IOException {
        // A chunk as 1.3 saved it: format 1, without the game times.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeInt(2);
        out.writeInt(7);
        out.writeBoolean(true);
        out.writeShort(1);
        out.writeShort(100);
        out.writeShort(6);
        out.writeFloat(64.0F);
        out.writeInt(0x6A8F3A);
        out.writeByte(LeafShape.BROAD.ordinal());
        out.writeLong(0L);
        out.writeInt(0);
        LitterChunk read = LitterChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        helper.assertTrue(read.x == 2 && read.z == 7 && read.seeded() && read.count[100] == 6 && read.total() == 6, "old chunk differs");
        helper.assertTrue(read.decayedAt() == LitterChunk.NEVER && read.seededAt() == LitterChunk.NEVER, "old chunk has game times");
        helper.succeed();
    }

    /** Game ticks in which a natural carpet rots away with the default settings. */
    private static final long LIFETIME = (long) (new LeafSettings().litterLifetime * 24000);

    public static void litterRotsAwayGradually(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int total = carpet(sim, helper, 2.5, 4);
        // A small pile in the middle, low enough not to slump.
        int[] center = cellAt(helper, 3.5, 3.5);
        for (int n = 0; n < 14; n++) {
            sim.field().add(center[0], center[1], absoluteY(helper, 1.0), 0x6A8F3A, 0, 0L, 0);
        }
        long now = helper.getLevel().getGameTime();
        decayAll(sim, now);
        helper.assertTrue(boxTotal(sim, helper) == total + 14, "leaves rotted without time passing");
        // A quarter of the lifetime later every cell of the carpet is thinner, but none is bare: leaf by leaf, not cell by cell.
        decayAll(sim, now + LIFETIME / 4);
        int[] corner = cellAt(helper, 2.0, 2.0);
        for (int dx = 0; dx < 6; dx++) {
            for (int dz = 0; dz < 6; dz++) {
                int count = sim.field().count(corner[0] + dx, corner[1] + dz);
                helper.assertTrue(count >= 2 && count <= 3, "a carpet cell holds " + count + " leaves after a quarter of the lifetime");
            }
        }
        int pile = sim.field().count(center[0], center[1]);
        helper.assertTrue(pile >= 10 && pile < 18, "the pile holds " + pile + " of 18 leaves");
        // Soon after the lifetime the carpet is gone; the pile lasts longer.
        decayAll(sim, now + LIFETIME * 3 / 2);
        helper.assertTrue(boxTotal(sim, helper) == sim.field().count(center[0], center[1]), "the carpet did not rot away");
        helper.assertTrue(sim.field().count(center[0], center[1]) > 0, "the pile rotted as fast as the carpet");
        decayAll(sim, now + LIFETIME * 4);
        helper.assertTrue(boxTotal(sim, helper) == 0, boxTotal(sim, helper) + " leaves are left after four lifetimes");
        helper.succeed();
    }

    public static void litterCatchesUpOnTheTimeAway(GameTestHelper helper) throws IOException {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int total = carpet(sim, helper, 2.0, 4);
        long now = helper.getLevel().getGameTime();
        run(sim, helper, 1);
        List<LitterChunk> chunks = new ArrayList<>(sim.field().chunks());
        int rotted = 0;
        for (LitterChunk chunk : chunks) {
            helper.assertTrue(chunk.decayedAt() == now, "the clock of a loaded chunk did not start");
            // Saved, then loaded again half a lifetime later: the time away is applied once, when the chunk comes back.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            chunk.write(new DataOutputStream(bytes));
            LitterChunk loaded = LitterChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            helper.assertTrue(loaded.decayedAt() == now, "the clock was not saved");
            loaded.setDecayedAt(now - LIFETIME / 2);
            sim.field().put(loaded);
            rotted += sim.decay(loaded, now);
            helper.assertTrue(loaded.decayedAt() == now && sim.decay(loaded, now) == 0, "the time away was applied twice");
        }
        int left = boxTotal(sim, helper);
        helper.assertTrue(rotted > total / 4 && left == total - rotted && left > total / 4,
                rotted + " of " + total + " leaves rotted in half a lifetime, " + left + " are left");
        // Ticks rot loaded chunks only as game time passes (a leaf the air lifts off the litter still counts).
        run(sim, helper, 20);
        helper.assertTrue(boxTotal(sim, helper) + sim.pool().count() == left, "loaded chunks rot again without time passing");
        // A chunk without a game time (saved by an older version) starts its clock instead of rotting.
        LitterChunk old = chunks.get(0);
        old.setDecayedAt(LitterChunk.NEVER);
        helper.assertTrue(sim.decay(old, now) == 0 && old.decayedAt() == now, "a chunk without a time rotted");
        helper.succeed();
    }

    public static void snowCoversNoLeaves(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
        int total = carpet(sim, helper, 2.5, 4);
        long now = helper.getLevel().getGameTime();
        decayAll(sim, now);
        // Snow falls on a block of the carpet: the leaves under it are gone, the rest stays.
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(at, Blocks.SNOW.defaultBlockState());
        sim.setLevel(helper.getLevel());
        sim.blockChanged(at);
        int[] corner = cellAt(helper, 3.0, 3.0);
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                helper.assertTrue(sim.field().count(corner[0] + a, corner[1] + b) == 0, "leaves lie under the snow");
            }
        }
        helper.assertTrue(boxTotal(sim, helper) == total - 16 * 4, "snow took " + (total - boxTotal(sim, helper)) + " leaves");
        // Leaves on snow (in a corner the carpet does not reach) rot within minutes, those on the stone stay.
        helper.setBlock(0, 1, 0, Blocks.SNOW_BLOCK.defaultBlockState());
        int[] onSnow = cellAt(helper, 0.5, 0.5);
        helper.assertTrue(sim.field().count(onSnow[0], onSnow[1]) == 0, "the carpet reaches the corner");
        for (int n = 0; n < 6; n++) {
            sim.field().add(onSnow[0], onSnow[1], absoluteY(helper, 2.0), 0x6A8F3A, 0, 0L, 0);
        }
        int onStone = boxTotal(sim, helper) - 6;
        decayAll(sim, now + 20 * 60 * 3);
        helper.assertTrue(sim.field().count(onSnow[0], onSnow[1]) == 0, "leaves still lie on the snow after three minutes");
        helper.assertTrue(boxTotal(sim, helper) > onStone * 9 / 10, "leaves on stone rotted as fast as on snow: "
                + boxTotal(sim, helper) + " of " + onStone + " are left");
        helper.succeed();
    }

    public static void pileBlockLeavesDoNotRot(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
        LeafSimulation sim = pileSimulation(helper, true);
        int pinned = pinnedIn(sim, helper, 3, 3);
        helper.assertTrue(pinned > 0, "the pile block did not become litter");
        int[] cell = cellAt(helper, 3.5, 3.5);
        LitterChunk chunk = sim.field().chunkAtCell(cell[0], cell[1]);
        int index = LitterField.index(cell[0], cell[1]);
        int under = chunk.count[index];
        for (int n = 0; n < 5; n++) {
            sim.field().add(cell[0], cell[1], absoluteY(helper, 1.0), 0x6A8F3A, 0, 0L, 0);
        }
        long now = helper.getLevel().getGameTime();
        decayAll(sim, now);
        // The pile block stands in for snow here, but a pile block is no snow: loose leaves on it rot at the normal pace.
        decayAll(sim, now + 20 * 60 * 3);
        helper.assertTrue(chunk.count[index] >= under + 4, "the leaves on the pile rotted as if it were snow");
        // Only the loose leaves rot; the pile block's own leaves stay as long as the block does.
        decayAll(sim, now + LIFETIME * 10);
        helper.assertTrue(pinnedIn(sim, helper, 3, 3) == pinned && boxTotal(sim, helper) == pinned,
                "after ten lifetimes " + boxTotal(sim, helper) + " leaves are left, the pile block holds " + pinned);
        helper.succeed();
    }

    public static void autumnLitterIsGoneBeforeWinter(GameTestHelper helper) {
        // Whatever the length of the seasons (here the default and a short one of two days per sub-season).
        for (int duration : new int[] {SeasonCurve.DEFAULT_SUB_SEASON_TICKS, 2 * 24000}) {
            floor(helper);
            LeafSimulation sim = simulation(helper, 64, LeafListener.NONE);
            // Only the season rots the leaves here.
            sim.settings().litterLifetime = 0.0F;
            int total = carpet(sim, helper, 2.0, 4);
            long time = helper.getLevel().getGameTime();
            decayAll(sim, time);
            int lateAutumn = -1;
            int earlyWinter = -1;
            for (float position = MID_AUTUMN; position < MID_WINTER; position += 0.05F) {
                SeasonCurve.apply(sim.settings(), position, duration);
                time += (long) (duration * 0.05F);
                decayAll(sim, time);
                if (lateAutumn < 0 && position >= LATE_AUTUMN) {
                    lateAutumn = boxTotal(sim, helper);
                }
                if (earlyWinter < 0 && position >= EARLY_WINTER) {
                    earlyWinter = boxTotal(sim, helper);
                }
            }
            helper.assertTrue(lateAutumn > total / 4 && lateAutumn < total, "in late autumn " + lateAutumn + " of " + total + " leaves are left");
            helper.assertTrue(earlyWinter < total / 50, earlyWinter + " of " + total + " leaves are left in the middle of early winter");
            helper.assertTrue(boxTotal(sim, helper) == 0, boxTotal(sim, helper) + " leaves are left in mid winter");
            // In summer the season adds nothing: the litter lasts.
            SeasonCurve.apply(sim.settings(), MID_SUMMER, duration);
            helper.assertTrue(sim.settings().decayRate() == 0.0F, "the litter rots in summer although its lifetime is off");
        }
        helper.succeed();
    }

    /** Lets the litter of every loaded chunk rot up to {@code gameTime} (chunks without a time start their clock). */
    private static void decayAll(LeafSimulation sim, long gameTime) {
        for (LitterChunk chunk : sim.field().chunks()) {
            sim.decay(chunk, gameTime);
        }
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
     * Lets the crown from {@link #canopy} shed leaves for {@code ticks} ticks at a point in the year (NaN: no seasons) and returns how
     * many fell. With {@code colors}, it also counts the moving leaves at the end in autumn colors (into [0]) and all of
     * them (into [1]): the tree is pure green, so any red in a leaf is an autumn color.
     */
    private static int shed(GameTestHelper helper, float season, int ticks, int[] colors) {
        return shed(helper, season, ticks, colors, true);
    }

    private static int shed(GameTestHelper helper, float season, int ticks, int[] colors, boolean placedLeaves) {
        LeafSimulation sim = simulation(helper, 4096, LeafListener.NONE);
        sim.settings().spawnRadius = 4;
        // The test crown is placed (persistent), so it does not decay during the test.
        sim.settings().placedLeaves = placedLeaves;
        if (Float.isNaN(season)) {
            SeasonCurve.clear(sim.settings());
        } else {
            SeasonCurve.apply(sim.settings(), season);
        }
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
