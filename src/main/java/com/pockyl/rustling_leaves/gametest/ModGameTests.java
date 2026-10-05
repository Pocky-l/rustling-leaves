package com.pockyl.rustling_leaves.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafListener;
import com.pockyl.rustling_leaves.sim.LeafPool;
import com.pockyl.rustling_leaves.sim.LeafSettings;
import com.pockyl.rustling_leaves.sim.LeafSimulation;

/**
 * In-game tests, run headless by {@code gradlew runGameTestServer}. The simulation only needs a {@code Level}, so it
 * runs here against real blocks; each test steps it synchronously. Tests use the 7x6x7 {@code box} structure with a
 * stone floor placed at y = 0.
 */
@GameTestHolder(RustlingLeaves.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private static final double EPSILON = 0.05;

    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafSettlesOnFloor(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 400);
        LeafPool pool = sim.pool();
        helper.assertTrue(pool.state[leaf] == LeafPool.RESTING, "leaf did not come to rest");
        helper.assertTrue(Math.abs(relativeY(helper, pool.y[leaf]) - 1.0) < EPSILON, "leaf rests at y " + relativeY(helper, pool.y[leaf]));
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafRestsOnSlab(GameTestHelper helper) {
        floor(helper);
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        fill(helper, 1, 1, 5, 5, 1, slab);
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 400);
        LeafPool pool = sim.pool();
        helper.assertTrue(pool.state[leaf] == LeafPool.RESTING, "leaf did not come to rest");
        helper.assertTrue(Math.abs(relativeY(helper, pool.y[leaf]) - 1.5) < EPSILON, "leaf rests at y " + relativeY(helper, pool.y[leaf]));
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafFloatsOnWater(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 1, 5, 5, 1, Blocks.WATER.defaultBlockState());
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 300);
        LeafPool pool = sim.pool();
        helper.assertTrue(pool.state[leaf] == LeafPool.FLOATING, "leaf is not floating, state " + pool.state[leaf]);
        double y = relativeY(helper, pool.y[leaf]);
        helper.assertTrue(y > 1.8 && y < 2.0, "leaf floats at y " + y);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leafBurnsInLava(GameTestHelper helper) {
        floor(helper);
        fill(helper, 0, 0, 6, 6, 1, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 1, 5, 5, 1, Blocks.LAVA.defaultBlockState());
        int[] burned = new int[1];
        LeafSimulation sim = simulation(64, new LeafListener() {
            @Override
            public void onBurn(double x, double y, double z) {
                burned[0]++;
            }
        });
        spawn(sim, helper, 3.5, 4.5, 3.5);
        run(sim, helper, 300);
        helper.assertTrue(burned[0] == 1 && sim.pool().count() == 0, "leaf did not burn");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void windChargeScattersLeaves(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        int[] leaves = restingCarpet(sim, helper);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        double spreadBefore = spread(sim, leaves, center);
        sim.explode(center.getX() + 0.5, center.getY() + 0.3, center.getZ() + 0.5, 1.2F, true);
        run(sim, helper, 3);
        int airborne = 0;
        for (int leaf : leaves) {
            if (sim.pool().state[leaf] == LeafPool.FALLING && sim.pool().vy[leaf] > 0.0F) {
                airborne++;
            }
        }
        helper.assertTrue(airborne >= leaves.length * 3 / 4, "only " + airborne + " of " + leaves.length + " leaves flew up");
        run(sim, helper, 30);
        double spreadAfter = spread(sim, leaves, center);
        helper.assertTrue(spreadAfter > spreadBefore + 0.5, "leaves did not scatter: " + spreadBefore + " -> " + spreadAfter);
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void walkingKicksUpLeaves(GameTestHelper helper) {
        floor(helper);
        int[] rustles = new int[1];
        LeafSimulation sim = simulation(64, new LeafListener() {
            @Override
            public void onRustle(double x, double y, double z, int count, boolean wet) {
                rustles[0] += count;
            }
        });
        int[] leaves = restingCarpet(sim, helper);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        sim.beginTick(helper.getLevel());
        int kicked = sim.disturb(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.22, 0.0, 0.0, 0.6F, 1.8F, false, 0.0F);
        sim.finishTick(center.getX(), center.getZ());
        helper.assertTrue(kicked >= 3, "walking kicked only " + kicked + " leaves");
        helper.assertTrue(rustles[0] == kicked, "rustle was not reported");
        int moving = 0;
        for (int leaf : leaves) {
            if (sim.pool().state[leaf] == LeafPool.FALLING) {
                moving++;
            }
        }
        helper.assertTrue(moving == kicked, "kicked leaves are not airborne");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void sneakingLeavesLeavesAlone(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        restingCarpet(sim, helper);
        BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
        sim.beginTick(helper.getLevel());
        int kicked = sim.disturb(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.065, 0.0, 0.0, 0.6F, 1.5F, true, 0.0F);
        sim.finishTick(center.getX(), center.getZ());
        helper.assertTrue(kicked == 0, "sneaking kicked " + kicked + " leaves");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void leavesFallWhenSupportIsRemoved(GameTestHelper helper) {
        floor(helper);
        fill(helper, 2, 2, 4, 4, 1, Blocks.OAK_PLANKS.defaultBlockState());
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 3.5, 3.5);
        run(sim, helper, 300);
        helper.assertTrue(Math.abs(relativeY(helper, sim.pool().y[leaf]) - 2.0) < EPSILON, "leaf did not land on the planks");
        fill(helper, 2, 2, 4, 4, 1, Blocks.AIR.defaultBlockState());
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                sim.blockChanged(helper.absolutePos(new BlockPos(x, 1, z)));
            }
        }
        run(sim, helper, 300);
        helper.assertTrue(sim.pool().state[leaf] == LeafPool.RESTING, "leaf did not settle again");
        helper.assertTrue(Math.abs(relativeY(helper, sim.pool().y[leaf]) - 1.0) < EPSILON, "leaf did not fall to the floor");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void placedBlockLiftsLeaf(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(64, LeafListener.NONE);
        int leaf = spawn(sim, helper, 3.5, 1.2, 3.5);
        run(sim, helper, 100);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos at = new BlockPos(Mth.floor(sim.pool().x[leaf]), origin.getY() + 1, Mth.floor(sim.pool().z[leaf]));
        helper.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
        sim.blockChanged(at);
        run(sim, helper, 60);
        helper.assertTrue(Math.abs(relativeY(helper, sim.pool().y[leaf]) - 2.0) < EPSILON, "leaf was not lifted onto the new block");
        helper.succeed();
    }

    @GameTest(template = "box")
    public static void fullPoolRecyclesOldestLeaves(GameTestHelper helper) {
        floor(helper);
        LeafSimulation sim = simulation(16, LeafListener.NONE);
        for (int n = 0; n < 16; n++) {
            spawn(sim, helper, 1.5 + n % 4, 1.2, 1.5 + n / 4);
        }
        helper.assertTrue(spawn(sim, helper, 3.5, 3.0, 3.5) == -1, "a full pool of airborne leaves accepted another one");
        run(sim, helper, 100);
        helper.assertTrue(sim.pool().count() < 16, "resting leaves did not make room under pressure");
        for (int n = 0; n < 8; n++) {
            helper.assertTrue(spawn(sim, helper, 3.5, 3.0, 3.5) >= 0, "no room for a new leaf");
        }
        helper.assertTrue(sim.pool().count() <= 16, "pool overflowed");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------

    private static LeafSimulation simulation(int capacity, LeafListener listener) {
        LeafSettings settings = new LeafSettings();
        settings.windStrength = 0.0F;
        return new LeafSimulation(settings, capacity, listener);
    }

    private static int spawn(LeafSimulation sim, GameTestHelper helper, double x, double y, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return sim.spawn(origin.getX() + x, origin.getY() + y, origin.getZ() + z, 0.0F, 0.0F, 0.0F, 0x6A8F3A, 0, 0.1F);
    }

    private static void run(LeafSimulation sim, GameTestHelper helper, int ticks) {
        BlockPos center = helper.absolutePos(new BlockPos(3, 0, 3));
        for (int t = 0; t < ticks; t++) {
            sim.tick(helper.getLevel(), center.getX(), center.getZ());
        }
    }

    /** A ring of leaves resting on the floor around the center of the box. */
    private static int[] restingCarpet(LeafSimulation sim, GameTestHelper helper) {
        int[] leaves = new int[24];
        for (int n = 0; n < leaves.length; n++) {
            double angle = n * 2.4;
            double radius = 0.2 + 0.07 * n;
            leaves[n] = spawn(sim, helper, 3.5 + Math.cos(angle) * radius, 1.1, 3.5 + Math.sin(angle) * radius);
        }
        run(sim, helper, 60);
        for (int leaf : leaves) {
            helper.assertTrue(sim.pool().state[leaf] == LeafPool.RESTING, "carpet leaf did not settle");
        }
        return leaves;
    }

    private static double spread(LeafSimulation sim, int[] leaves, BlockPos center) {
        double sum = 0.0;
        for (int leaf : leaves) {
            double dx = sim.pool().x[leaf] - (center.getX() + 0.5);
            double dz = sim.pool().z[leaf] - (center.getZ() + 0.5);
            sum += Math.sqrt(dx * dx + dz * dz);
        }
        return sum / leaves.length;
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
