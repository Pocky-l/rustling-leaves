# Rustling Leaves

Physically simulated leaves: falling leaves, leaf piles you can wade through, rake and blow away, gusts, whirlwinds and leaf tools.

## Features

- **Falling leaves**: leaves let go of the underside and open sides of tree crowns and come down like real
  leaves - most flutter and glide in pendulum swings (some in wide, lazy arcs), others tumble about their long axis
  and slide off sideways.
- **A real forest floor**: leaves pile up in layers - a patchy carpet under the trees with the ground showing
  through, small drifts against walls and trunks, and once in a while a leaf pile. Thin litter is a layer of loose
  leaves, deep piles get a solid body under them. Piles slump to a natural angle of repose and leaves slide down their
  sides. The ground cover is **saved with your world** (per server in multiplayer), so piles stay where they are.
- **Wade through piles**: the leaves under your feet are pressed down and stay, part of the pile is pushed aside
  with every step and leaves a trail, some leaves skid over the ground and a few fly up after you (more when
  running).
  **Sneaking** over a thin carpet leaves it untouched. **Jump into a pile** and it splashes up around you.
  Mobs, animals, minecarts and arrows stir leaves too, with a soft rustle.
- **Leaves behave like a granular material**: piles settle into round cones, leaves trickle down slopes, fill
  hollows, ditches and cauldrons up to the rim and run over the edge.
- **Leaf tools** (when the mod is also on the server):
  - **Leaf Blower** - hold right click to blow a cone of air: it clears paths through the litter, heaps the leaves
    up where the air dies down, throws loose leaves into the air and nudges items and mobs.
  - **Leaf Bag** - hold right click to suck leaves up (they fly into the bag), sneak + hold right click to pour them
    out in a stream - fill a hole, heap a pile, shower a friend. Holds 1000 leaves; use it on a composter to
    turn every 32 leaves into a layer of compost.
  - **Autumn Bomb** - a throwable bundle of leaves that bursts into a swirling cloud of autumn leaves.
  - **Staff of Winds** - right click to raise a leaf whirlwind where you look, sneak + right click to send a squall
    rolling ahead of you.
- **Rake leaves into piles**: hold right click with a hoe or shovel on leaf litter to sweep the leaves around that
  spot together (it does not till the ground or make a path while there are leaves).
- **Wind you can see**: gust waves sweep across the forest and skitter loose leaves over the ground until they
  pile up against obstacles; **squalls** - strong gust fronts - roll through and strip a band of trees at once;
  **leaf whirlwinds** wander over open ground, suck up the litter and spin it up in a swirling column.
  Rain makes leaves wet and heavy, thunderstorms tear them off the trees.
- **Wind charges** and **explosions** push a pulse of air through the leaves: a ring of wind that rushes outwards and
  dies down, a rising column in the middle (and a swirl for wind charges). Leaves ride it out, get lifted, flutter
  down again; some just hop and fall back where they lay.
- Breaking or decaying leaves releases a burst of leaves; when the block under litter disappears, the litter falls,
  and a block placed on litter ends up with the leaves on top. Leaves float on water, rock on its ripples, drift with the current and go down waterfalls; slowly they soak,
  sink swaying and settle on the bottom. They burn in lava.
- **Colors match the tree** in every biome (also modded trees), with a share of autumn yellows, oranges and reds;
  litter on the ground looks a bit more aged. Oak, birch, spruce and cherry trees drop different shapes.
- **Built for huge numbers of leaves**: lying leaves are stored as stacks per quarter block and only the visible ones
  are drawn from cached GPU meshes - a pile of thousands of leaves costs a few hundred quads; only moving leaves are
  simulated in full, and everything that pushes leaves finds them through a spatial grid. The F3 screen shows the
  current numbers.
- **Works on any server**: the leaves, piles, wind and raking are client-side and work even on vanilla servers.
  Installed on the server too, the mod adds the leaf tools, and everyone sees each other's tools at work.

## Configuration

Everything can be tuned in the in-game config screen: number of leaves, fall rate, radius, time on the ground,
carpet thickness, leaf size, autumn colors, wind, squalls and whirlwinds, footstep and explosion strength,
raking, rustle volume.

## Requirements

[NeoForge](https://neoforged.net) 1.21.1. The leaves work client-side on any server; install the mod on the server too for the leaf tools.

## Credits

Made by **Pocky**. Source code: [GitHub](https://github.com/Pocky-l/rustling-leaves)

<!-- more-mods:start -->
<!-- more-mods:end -->
