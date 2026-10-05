<p align="center">
  <img src="src/main/resources/logo.png" alt="Rustling Leaves" width="160">
</p>

<h1 align="center">Rustling Leaves</h1>

<p align="center">
  Thousands of physically simulated leaves that fall from trees, pile up and scatter when you walk, blast or throw wind charges.
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
- **Leaves pile up** on the ground, on slabs, stairs and paths, and slowly fade away after a few minutes.
  They float and drift on water (rivers carry them along) and burn up in lava.
- **Walk through them**: leaves are kicked up under your feet and fly in the direction you move; sprinting
  throws them higher, landing from a jump scatters them around you, **sneaking leaves them undisturbed**.
  Mobs, animals, minecarts, boats and arrows stir them up too. A soft rustle plays as you wade through piles.
- **Wind charges** blow leaves away in a swirling ring; **explosions** (TNT, creepers, beds, ...) send a shock
  front through the forest floor that throws leaves high into the air.
- **Breaking or decaying leaves** releases a burst of leaves; when the block under a leaf disappears, the leaf falls.
- **Wind**: gusts sweep visibly across the forest, picking up loose leaves; rain makes them wet and heavy,
  thunderstorms tear them off the trees.
- **Colors match the tree** in every biome (also modded trees), with a share of autumn yellows, oranges and reds.
  Oak, birch, spruce and cherry trees drop different shapes: broad leaves, round leaves, needles and petals.
- **Built for thousands of leaves**: leaves lying still cost almost nothing and are drawn from cached GPU meshes,
  only moving leaves are simulated in full; everything that pushes leaves finds them through a spatial grid.
  8000 leaves by default, up to 60000 on a strong PC. The F3 screen shows the current numbers.
- **Client-side only**: works on any server, including vanilla ones; no items, nothing to install on the server.

## Controls

No keys. Just play:

| Action | Effect on leaves |
|---|---|
| Walk / sprint | Kicks up the leaves under your feet |
| Sneak | Walk through leaves quietly without disturbing them |
| Jump and land | Scatters leaves around you |
| Throw a wind charge | Blows leaves away in a swirl |
| Explode TNT | Throws leaves high into the air |
| Break a leaves block | Releases a burst of leaves |

## Crafting

Nothing to craft: the mod adds no items or blocks, it changes how the world looks and feels.

## Configuration

`config/rustling_leaves-client.toml` (also in the in-game config screen: Mods -> Rustling Leaves -> Config):

| Option | Default | Description |
|---|---|---|
| `leaves.maxLeaves` | `8000` | Maximum number of leaves at once (0 disables the mod) |
| `leaves.fallRate` | `1.0` | How often leaves fall from trees (0 = no falling leaves) |
| `leaves.spawnRadius` | `32` | Radius around you in which trees drop leaves |
| `leaves.groundLifetime` | `300` s | How long a leaf lies on the ground before fading |
| `leaves.leafSize` | `1.0` | Size of the leaves |
| `leaves.autumnColors` | `0.35` | Share of leaves in autumn colors |
| `leaves.leavesPerBreak` | `14` | Leaves released by a broken or decayed leaves block |
| `physics.windStrength` | `1.0` | Wind strength (0 = still air) |
| `physics.entityStrength` | `1.0` | How strongly players, mobs and projectiles stir leaves |
| `physics.explosionStrength` | `1.0` | How strongly explosions and wind charges blow leaves |
| `sound.rustleVolume` | `0.6` | Volume of the rustle when walking through leaves |

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
