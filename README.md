<p align="center">
  <img src="src/main/resources/logo.png" alt="Rustling Leaves" width="160">
</p>

<h1 align="center">Rustling Leaves</h1>

<p align="center">
  Physically simulated leaves: falling leaves, leaf piles you can wade through, rake into piles, gusts and whirlwinds.
</p>

<p align="center">
  <img alt="Minecraft 1.21.1 | 1.20.1" src="https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.20.1-62B47A">
  <a href="https://neoforged.net"><img alt="NeoForge" src="https://img.shields.io/badge/Loader-NeoForge-F16436"></a>
  <a href="https://files.minecraftforge.net"><img alt="Forge" src="https://img.shields.io/badge/Loader-Forge-DFA86A"></a>
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
- **Leaves rot away**: fallen leaves lie for a long time - a natural carpet about ten in-game days, thick piles much
  longer - and thin out slowly, leaf by leaf; near you the last ones crumble away. Under the trees new leaves keep
  falling and the forest floor fills up again, also where you have not been for a while, and ground you come back to
  has rotted for as long as you were away. Leaves never lie under snow: snow that covers them clears them, and leaves on
  snow rot within minutes.
- **Wade through piles**: the leaves under your feet are pressed down and stay, part of the pile is pushed aside
  with every step and leaves a trail, some leaves skid over the ground and a few fly up after you (more when
  running).
  **Sneaking** over a thin carpet leaves it untouched. **Jump into a pile** and it splashes up around you.
  Mobs, animals, minecarts and arrows stir leaves too, with a soft rustle.
- **Leaves behave like a granular material**: piles settle into round cones, leaves trickle down slopes, fill
  hollows, ditches and cauldrons up to the rim and run over the edge.
- **Rake leaves into piles**: sneak and hold right click with a hoe or shovel on leaf litter to sweep the leaves around that
  spot together; without sneaking the tool tills the ground and makes paths as usual. Other players see the pile
  form too.
- **Wind you can see**: gust waves sweep across the forest and skitter loose leaves over the ground until they
  pile up against obstacles; **squalls** - strong gust fronts - roll through and strip a band of trees at once;
  **leaf whirlwinds** wander over open ground, suck up the litter and spin it up in a swirling column.
  Rain makes leaves wet and heavy, thunderstorms tear them off the trees.
- **Wind charges** and **explosions** push a pulse of air through the leaves: a ring of wind that rushes outwards and
  dies down, a rising column in the middle (and a swirl for wind charges). Leaves ride it out, get lifted, flutter
  down again; some just hop and fall back where they lay.
- Breaking or decaying leaves releases a burst of leaves; when the block under litter disappears, the litter falls,
  leaves stay under a slab, carpet or fence placed over them, and a full block placed on litter smothers it. Leaves on water stick flat to the surface and darken as they soak; they drift with the current (and down
  waterfalls), turn along the flow and spin where it swirls, gather into rafts and along the banks, get pushed under
  where water pours onto them, and finally sink swaying to the bottom. They burn in lava.
- **Colors match the tree** in every biome (also modded trees), with a share of autumn yellows, oranges and reds;
  litter on the ground looks a bit more aged. Oak, birch, spruce and cherry trees drop different shapes.
- **Every tree sheds its own way**: birches drop leaves more often, spruces and other conifers hardly ever lose a
  needle. Cherry trees drop simulated petals instead of the vanilla petal particles, exactly as many as vanilla does.
- **Seasons** (with [Serene Seasons](https://www.curseforge.com/minecraft/mc-mods/serene-seasons)): leaf fall is an autumn event - the first leaves come down in early autumn,
  most of them in mid and late autumn, more and more of them yellow, orange and red until by late autumn nearly every
  leaf is, and the last few brown ones in early winter. In winter, spring and summer the trees keep their leaves; only
  cherry blossoms shed their petals in spring. From late autumn on the leaves on the ground rot faster and are gone
  early in winter, before the snow builds up, however long the seasons are. New ground gets a thick leaf carpet only in
  autumn. The change is gradual, without jumps between sub-seasons. Tropical biomes and dimensions without seasons are
  not affected.
- **Immersive Weathering leaf piles** (with [Immersive Weathering](https://www.curseforge.com/minecraft/mc-mods/immersive-weathering)): its leaf piles are drawn as
  Rustling Leaves litter - soft, rounded piles that merge with the forest floor, in the color of their tree. The pile
  stays as long as its block does: when it grows, leaves fall onto it from the tree above and it fills up with them;
  it rots away with Immersive Weathering, and breaking it throws up a handful of leaves. Leaves that fall onto it stay
  on top and react to wind and footsteps as everywhere else.
- **Built for huge numbers of leaves**: lying leaves are stored as stacks per quarter block and only the visible ones
  are drawn from cached GPU meshes - a pile of thousands of leaves costs a few hundred quads. Far away the litter
  switches to a simplified layer (one mesh per chunk), so the forest floor stays visible out to the render distance
  without leaves popping in; only moving leaves are
  simulated in full, and everything that pushes leaves finds them through a spatial grid. The F3 screen shows the
  current numbers.
- **Client-side**: the mod adds no items or blocks, so it works on any server, with or without the mod,
  even on vanilla servers. On the server it is optional and only passes on where players rake.

## Controls

No keys. Just play:

| Action | Effect on leaves |
|---|---|
| Walk / sprint through leaves | Pushes them aside and leaves a trench; a few fly up |
| Sneak | Walk over a thin carpet without disturbing it |
| Jump into a pile | Leaves splash up around you |
| Sneak + hold right click with a hoe or shovel | Rake the leaves around that spot into a pile |
| Throw a wind charge (1.21.1 only) | Blows leaves away in a swirl |
| Explode TNT | Clears the ground and throws leaves high into the air |
| Break a leaves block | Releases a burst of leaves |

## Configuration

`config/rustling_leaves-client.toml` (on 1.21.1 also in the in-game config screen: Mods -> Rustling Leaves -> Config):

| Option | Default | Description |
|---|---|---|
| `leaves.maxLeaves` | `8000` | Maximum number of moving leaves (0 disables the mod); lying leaves are not counted |
| `leaves.fallRate` | `1.0` | How often leaves fall from trees (0 = no falling leaves) |
| `leaves.treeFallRates` | `true` | Birches shed more, conifers less (off = all trees alike) |
| `leaves.placedLeaves` | `false` | Leaves placed by players (hedges, leaf roofs) shed and get litter like trees |
| `leaves.spawnRadius` | `32` | Radius around you in which trees drop leaves |
| `leaves.carpetDepth` | `4` | Natural leaf carpet thickness (leaves per quarter block, 0 = none) |
| `leaves.naturalPiles` | `true` | Drifts against walls and trunks and occasional piles in forests |
| `leaves.litterLifetime` | `10.0` | In-game days until a carpet of fallen leaves has rotted away (piles last longer, 0 = never) |
| `leaves.litterDistance` | `128` | How far lying leaves are shown (capped by the render distance) |
| `leaves.leafSize` | `1.0` | Size of the leaves |
| `leaves.shadows` | `true` | Pixel shadows under falling leaves, inside piles and around their foot |
| `leaves.oldLeafTextures` | `false` | Draw leaves with the mod's original textures (leaf shapes, needles, petals) instead of the falling leaves of newer Minecraft versions |
| `leaves.autumnColors` | `0.35` | Share of leaves in autumn colors |
| `leaves.immersiveWeatheringPiles` | `true` | Draw Immersive Weathering's leaf piles as leaf litter |
| `leaves.realisticWaves` | `true` | With Realistic Waves: floating leaves ride its waves (off = flat vanilla water level) |
| `leaves.livingWind` | `true` | With Living Wind, Petrichor or Hoarfrost: leaves blow with their wind (off = their own wind) |
| `leaves.fallingLeavesMod` | `false` | With Falling Leaves or Living Wind: keep their falling leaves as well (off = only Rustling Leaves' leaves fall) |
| `leaves.leavesPerBreak` | `14` | Leaves released by a broken or decayed leaves block |
| `physics.windStrength` | `1.0` | Wind strength (0 = still air) |
| `physics.windEvents` | `1.0` | How often squalls and whirlwinds happen (0 = never) |
| `physics.entityStrength` | `1.0` | How strongly players, mobs and projectiles push leaves |
| `physics.explosionStrength` | `1.0` | How strongly explosions and wind charges blow leaves |
| `physics.raking` | `true` | Rake leaves with a hoe or shovel while sneaking |
| `seasons.enabled` | `true` | With Serene Seasons: leaves fall in autumn, the litter rots before winter |
| `seasons.autumnFallRate` | `3.0` | Fall rate multiplier at the autumn peak (mid and late autumn; less in early autumn) |
| `seasons.winterFallRate` | `0.0` | Fall rate multiplier in mid and late winter (0 = no leaves fall) |
| `seasons.springSummerFallRate` | `0.02` | Fall rate multiplier in spring and summer (cherry blossom petals not affected) |
| `seasons.autumnColors` | `3.0` | Multiplier of `leaves.autumnColors` in late autumn (capped at all leaves) |
| `seasons.seasonalDecay` | `1.0` | Extra rot of the litter in late autumn and winter, in carpets per sub-season (0 = none) |
| `sound.rustleVolume` | `0.6` | Volume of rustling |

Leaf litter is saved in `saves/<world>/rustling_leaves/` (singleplayer) or `rustling_leaves/servers/<address>/`
(multiplayer).

## Compatibility

- **Shader packs** ([Iris](https://modrinth.com/mod/iris), Oculus): falling leaves and the litter are drawn with the pack's terrain program, so they
  are lit and shaded like the blocks around them. Switching a pack on or off in game rebuilds the leaf meshes.
- [Serene Seasons](https://www.curseforge.com/minecraft/mc-mods/serene-seasons): leaf fall, autumn colors and the rot of the litter follow the season (see Features); tune or turn
  it off in the `seasons` config section.
- [Immersive Weathering](https://www.curseforge.com/minecraft/mc-mods/immersive-weathering): its leaf piles are drawn as this mod's leaf litter (see Features); turn it off with
  `leaves.immersiveWeatheringPiles` to keep their own look (falling leaves then land on top of them).
- [Realistic Waves](https://www.curseforge.com/minecraft/mc-mods/realistic-waves): leaves floating on the sea ride its swell and surf, rising, sinking and tilting
  with every wave (`leaves.realisticWaves`).
- [Falling Leaves](https://www.curseforge.com/minecraft/mc-mods/falling-leaves-forge): Rustling Leaves takes over its falling leaves, so trees do not shed twice
  (its snow from snowy leaves stays; `leaves.fallingLeavesMod` keeps both).
- [Living Wind](https://www.curseforge.com/minecraft/mc-mods/living-wind): leaves blow with its wind, the way its wind vanes point and as hard as it blows; it leaves the
  falling leaves to this mod (`leaves.livingWind`, `leaves.fallingLeavesMod`).
- [Petrichor: Rain & Storms](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms) and [Hoarfrost: Snow & Blizzards](https://www.curseforge.com/minecraft/mc-mods/hoarfrost-snow-blizzards): leaves blow the way the rain
  slants and the snow drifts (`leaves.livingWind`).
- [Sodium](https://modrinth.com/mod/sodium) works.

## Installation

Supported versions:

- Minecraft 1.21.1 with [NeoForge](https://neoforged.net) - branch `main`.
- Minecraft 1.20.1 with [Forge](https://files.minecraftforge.net) 47 or [Fabric](https://fabricmc.net) - branch `1.20.1`.

1. Install [NeoForge](https://neoforged.net) for Minecraft 1.21.1, or [Forge](https://files.minecraftforge.net) or
   [Fabric](https://fabricmc.net) for Minecraft 1.20.1. On Fabric the mod also needs
   [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) and
   [Forge Config API Port](https://www.curseforge.com/minecraft/mc-mods/forge-config-api-port-fabric).
2. Put this mod into the `mods` folder of your client. The server does not need it; installed there as well, it
   shows every player's raking at the exact spot to the players nearby.

## Building

```sh
./gradlew build
```

The jars are written to `forge/build/libs/` and `fabric/build/libs/` (the shared code is in `common/`).

## Credits

- Author: **Pocky**.
- Falling leaf and petal textures: from newer Minecraft versions, adapted for tinting. Pile textures and logo:
  original, made for this mod. Sounds: CC0 field recordings from [Freesound](https://freesound.org) by Shain_T,
  SpliceSound, Magdaadga, lolamadeus, nickmaysoundmusic, audioninja001, Ryding, Garuda1982 and Stefan21100190, cut
  for the mod.

<!-- more-mods:start -->
## More mods by Pocky

<table>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/holy-staff"><img src="https://raw.githubusercontent.com/Pocky-l/holy-staff/main/docs/icon.png" width="96" alt="Holy Staff"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/holy-staff"><b>Holy Staff</b></a><br>
      A holy staff with three healing skills, aim previews and flying heal numbers.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/holy-staff"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1725465?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/holy-staff"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/lumen-rigs"><img src="https://raw.githubusercontent.com/Pocky-l/lumen-rigs/main/docs/icon.png" width="96" alt="Lumen Rigs"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/lumen-rigs"><b>Lumen Rigs</b></a><br>
      Aimable spotlights, floodlights, searchlights and soft panels with colored light and visible beams.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/lumen-rigs"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1727739?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/lumen-rigs"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks"><img src="https://raw.githubusercontent.com/Pocky-l/neon-glowsticks/main/docs/icon.png" width="96" alt="Neon Glowsticks"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks"><b>Neon Glowsticks</b></a><br>
      Throwable glowsticks that bounce, roll and light up the dark with colored light.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1727688?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/neon-glowsticks"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><img src="https://raw.githubusercontent.com/Pocky-l/petrichor/main/docs/icon.png" width="96" alt="Petrichor: Rain & Storms"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><b>Petrichor: Rain & Storms</b></a><br>
      Realistic rain and storms: rain types, puddles, runoff and drips, branching lightning with delayed thunder.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1729574?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/petrichor"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack"><img src="https://raw.githubusercontent.com/Pocky-l/ranchers-vacpack/main/docs/icon.png" width="96" alt="Rancher's Vacpack"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack"><b>Rancher's Vacpack</b></a><br>
      A Slime Rancher inspired vacuum gun: suck up items and small mobs, store them in a tank and shoot them back out.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1725381?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/ranchers-vacpack"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
</table>
<!-- more-mods:end -->

## License

[MIT](LICENSE)
