<p align="center">
  <img src="src/main/resources/logo.png" alt="Rustling Leaves" width="160">
</p>

<h1 align="center">Rustling Leaves</h1>

<p align="center">
  Physically simulated leaves: falling leaves, leaf piles you can wade through, rake and blow away, gusts, whirlwinds and leaf tools.
</p>

<p align="center">
  <img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A">
  <a href="https://neoforged.net"><img alt="NeoForge" src="https://img.shields.io/badge/Loader-NeoForge-F16436"></a>
  <img alt="Client, server optional" src="https://img.shields.io/badge/Side-Client%20%2B%20optional%20server-5B8DEF">
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

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
  and a block placed on litter ends up with the leaves on top. Leaves on water stick flat to the surface and darken as they soak; they drift with the current (and down
  waterfalls), turn along the flow and spin where it swirls, gather into rafts and along the banks, get pushed under
  where water pours onto them, and finally sink swaying to the bottom. They burn in lava.
- **Colors match the tree** in every biome (also modded trees), with a share of autumn yellows, oranges and reds;
  litter on the ground looks a bit more aged. Oak, birch, spruce and cherry trees drop different shapes.
- **Built for huge numbers of leaves**: lying leaves are stored as stacks per quarter block and only the visible ones
  are drawn from cached GPU meshes - a pile of thousands of leaves costs a few hundred quads. Far away the litter
  switches to a simplified layer (one mesh per chunk), so the forest floor stays visible out to the render distance
  without leaves popping in; only moving leaves are
  simulated in full, and everything that pushes leaves finds them through a spatial grid. The F3 screen shows the
  current numbers.
- **Works on any server**: the leaves, piles, wind and raking are client-side and work even on vanilla servers.
  Installed on the server too, the mod adds the leaf tools, and everyone sees each other's tools at work.

## Controls

No keys. Just play:

| Action | Effect on leaves |
|---|---|
| Walk / sprint through leaves | Pushes them aside and leaves a trench; a few fly up |
| Sneak | Walk over a thin carpet without disturbing it |
| Jump into a pile | Leaves splash up around you |
| Hold right click with a hoe or shovel | Rake the leaves around that spot into a pile |
| Hold right click with the Leaf Blower | Blow leaves away |
| Hold right click with the Leaf Bag | Suck leaves up; sneak to pour them out |
| Right click a composter with the Leaf Bag | Compost 32 leaves per layer |
| Throw an Autumn Bomb | Burst of autumn leaves |
| Right click with the Staff of Winds | Whirlwind where you look; sneak for a squall |
| Throw a wind charge | Blows leaves away in a swirl |
| Explode TNT | Clears the ground and throws leaves high into the air |
| Break a leaves block | Releases a burst of leaves |

## Crafting

In creative mode the tools are in the **Pocky Mods** tab (and in Tools & Utilities / Combat).

| Item | Recipe |
|---|---|
| Leaf Blower | `  I` / `IBI` / `RI ` - `I` iron ingot, `B` breeze rod, `R` redstone |
| Leaf Bag | `S S` / `L L` / ` L ` - `S` string, `L` leather |
| Autumn Bomb (x2) | shapeless: 2 leaves (any), gunpowder, paper |
| Staff of Winds | `  W` / ` B ` / `S  ` - `W` wind charge, `B` breeze rod, `S` stick |

## Configuration

`config/rustling_leaves-client.toml` (also in the in-game config screen: Mods -> Rustling Leaves -> Config):

| Option | Default | Description |
|---|---|---|
| `leaves.maxLeaves` | `8000` | Maximum number of moving leaves (0 disables the mod); lying leaves are not counted |
| `leaves.fallRate` | `1.0` | How often leaves fall from trees (0 = no falling leaves) |
| `leaves.spawnRadius` | `32` | Radius around you in which trees drop leaves |
| `leaves.carpetDepth` | `4` | Natural leaf carpet thickness (leaves per quarter block, 0 = none) |
| `leaves.naturalPiles` | `true` | Drifts against walls and trunks and occasional piles in forests |
| `leaves.litterDistance` | `128` | How far lying leaves are shown (capped by the render distance) |
| `leaves.leafSize` | `1.0` | Size of the leaves |
| `leaves.autumnColors` | `0.35` | Share of leaves in autumn colors |
| `leaves.leavesPerBreak` | `14` | Leaves released by a broken or decayed leaves block |
| `physics.windStrength` | `1.0` | Wind strength (0 = still air) |
| `physics.windEvents` | `1.0` | How often squalls and whirlwinds happen (0 = never) |
| `physics.entityStrength` | `1.0` | How strongly players, mobs and projectiles push leaves |
| `physics.explosionStrength` | `1.0` | How strongly explosions and wind charges blow leaves |
| `physics.raking` | `true` | Rake leaves with a hoe or shovel |
| `sound.rustleVolume` | `0.6` | Volume of rustling and whirlwinds |

Leaf litter is saved in `saves/<world>/rustling_leaves/` (singleplayer) or `rustling_leaves/servers/<address>/`
(multiplayer).

## Compatibility

- **Shader packs** ([Iris](https://modrinth.com/mod/iris), Oculus): falling leaves and the litter are drawn with the pack's terrain program, so they
  are lit and shaded like the blocks around them. Switching a pack on or off in game rebuilds the leaf meshes.
- [Sodium](https://modrinth.com/mod/sodium) works.

## Installation

1. Install [NeoForge](https://neoforged.net) for Minecraft 1.21.1.
2. Put this mod into the `mods` folder of your client. For the leaf tools, install it on the server as well.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`.

## Credits

- Author: **Pocky**.
- Leaf textures and logo: original, made for this mod. Sounds: vanilla Minecraft.

<!-- more-mods:start -->
<!-- more-mods:end -->

## License

[MIT](LICENSE)
