<p align="center">
  <img src="src/main/resources/logo.png" alt="Rustling Leaves" width="160">
</p>

<h1 align="center">Rustling Leaves</h1>

<p align="center">
  Physically simulated leaves: falling leaves, a forest floor of leaf piles you can wade through and rake, gusts and whirlwinds.
</p>

<p align="center">
  <img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A">
  <a href="https://neoforged.net"><img alt="NeoForge" src="https://img.shields.io/badge/Loader-NeoForge-F16436"></a>
  <img alt="Client-side" src="https://img.shields.io/badge/Side-Client-5B8DEF">
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

## Features

- **Falling leaves**: trees around you shed leaves that flutter, sway and glide down like real leaves -
  broadside they drift, edge-on they slip, and each one spins and tilts in its own rhythm.
- **A real forest floor**: leaves pile up in layers - a carpet under the trees, drifts banked against walls and
  trunks, and now and then a proper leaf pile. Piles slump to a natural angle of repose and leaves slide down their
  sides. The ground cover is **saved with your world** (per server in multiplayer), so piles stay where they are.
- **Wade through piles**: your body pushes the leaves aside - most are shoved to the sides and ahead, leaving a
  trench with banks behind you; some skid over the ground, only a few from the top fly up (more when running).
  **Sneaking** over a thin carpet leaves it untouched. **Jump into a pile** and it splashes up around you.
  Mobs, animals, minecarts and arrows stir leaves too, with a soft rustle.
- **Rake leaves into piles**: hold right click with a hoe or shovel on leaf litter to sweep the leaves around that
  spot together (it does not till the ground or make a path while there are leaves).
- **Wind you can see**: gust waves sweep across the forest and skitter loose leaves over the ground until they
  pile up against obstacles; **squalls** - strong gust fronts - roll through and strip a band of trees at once;
  **leaf whirlwinds** wander over open ground, suck up the litter and spin it up in a swirling column.
  Rain makes leaves wet and heavy, thunderstorms tear them off the trees.
- **Wind charges** blow leaves away in a swirling ring; **explosions** send a shock front through the forest floor
  that clears the ground and throws leaves high into the air.
- Breaking or decaying leaves releases a burst of leaves; when the block under litter disappears, the litter falls,
  and a block placed on litter ends up with the leaves on top. Leaves float and drift on water and burn in lava.
- **Colors match the tree** in every biome (also modded trees), with a share of autumn yellows, oranges and reds;
  litter on the ground looks a bit more aged. Oak, birch, spruce and cherry trees drop different shapes.
- **Built for huge numbers of leaves**: lying leaves are stored as stacks per quarter block and only the visible ones
  are drawn from cached GPU meshes - a pile of thousands of leaves costs a few hundred quads; only moving leaves are
  simulated in full, and everything that pushes leaves finds them through a spatial grid. The F3 screen shows the
  current numbers.
- **Client-side only**: works on any server, including vanilla ones; no items, nothing to install on the server.

## Controls

No keys. Just play:

| Action | Effect on leaves |
|---|---|
| Walk / sprint through leaves | Pushes them aside and leaves a trench; a few fly up |
| Sneak | Walk over a thin carpet without disturbing it |
| Jump into a pile | Leaves splash up around you |
| Hold right click with a hoe or shovel | Rake the leaves around that spot into a pile |
| Throw a wind charge | Blows leaves away in a swirl |
| Explode TNT | Clears the ground and throws leaves high into the air |
| Break a leaves block | Releases a burst of leaves |

## Crafting

Nothing to craft: the mod adds no items or blocks, it changes how the world looks and feels.

## Configuration

`config/rustling_leaves-client.toml` (also in the in-game config screen: Mods -> Rustling Leaves -> Config):

| Option | Default | Description |
|---|---|---|
| `leaves.maxLeaves` | `8000` | Maximum number of moving leaves (0 disables the mod); lying leaves are not counted |
| `leaves.fallRate` | `1.0` | How often leaves fall from trees (0 = no falling leaves) |
| `leaves.spawnRadius` | `32` | Radius around you in which trees drop leaves |
| `leaves.carpetDepth` | `6` | Natural leaf carpet thickness (leaves per quarter block, 0 = none) |
| `leaves.naturalPiles` | `true` | Drifts against walls and trunks and occasional piles in forests |
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

## Installation

1. Install [NeoForge](https://neoforged.net) for Minecraft 1.21.1.
2. Put this mod into the `mods` folder of your client. Servers do not need it.

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
