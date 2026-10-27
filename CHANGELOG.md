# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.6.0] - Unreleased
### Added
- Shader packs can sway with the leaves' wind: while an [Iris](https://www.curseforge.com/minecraft/mc-mods/irisshaders) shader pack is on, the mod hands its wind
  and gust waves to the pack every frame (`rustling_leaves:shader_data`, a small data texture any pack can read).
  [Pocky Shaders](https://github.com/Pocky-l/pocky-shaders) bends grass and tree crowns with the very gusts that blow the leaves off.

## [2.5.0] - 2026-10-27
### Added
- Compatibility with [Living Wind](https://www.curseforge.com/minecraft/mc-mods/living-wind): leaves blow with its
  wind, the way its wind vanes point and as hard as it blows, from a light breeze on a clear day to a gale in a
  storm. Gust waves, squalls and whirlwinds still sweep through the forest on top of it. Client option
  `leaves.livingWind` lets the leaves keep their own wind.
- With Living Wind, Rustling Leaves takes over the leaves it blows out of the tree crowns, so trees do not shed twice;
  the grass, plants and dust it blows stay. Option `leaves.fallingLeavesMod` keeps both, as with Falling Leaves.

## [2.4.0] - 2026-10-27
### Added
- Pixel shadows. Falling leaves cast a crisp leaf-shaped shadow along the sunbeam: under them at noon, a little to
  the side in the morning and evening. Like a real leaf's shadow it is sharp only close to the ground and fades out
  over longer beams (so at sunset only low leaves cast one); weak under the trees and in rain, none at night.
  Leaves lying on the ground get a faint contact shadow, leaves on a pile shade the leaves beneath them, the sides and
  hollows of a pile are darker than its crest, and piles shade the ground around their foot, so they read as heaps
  instead of flat patches. The shadows are drawn like the leaves themselves and work with shader packs; client option
  `leaves.shadows` turns them off.
- Client command `/rustlingleaves fallrate <value>` sets how many leaves fall from the trees (1 = normal, up to 10),
  right away and saved to the config; `/rustlingleaves fallrate` shows the current value. It works on any server.

## [2.3.0] - 2026-10-27
### Added
- Client option `leaves.oldLeafTextures` brings back the mod's original leaf textures (several leaf shapes, needles
  and petals). It switches in game, without a restart.

### Changed
- Falling leaves and cherry petals use the leaf and petal textures of the newest Minecraft versions, in three shades
  each, still coloured by their tree and the season. Piles keep their textures.

### Fixed
- Some leaf piles in forests were black: piles that formed on their own could get no tree colour. New piles get the
  colour of the tree above them, and black piles already lying in your worlds take the colour of the leaves around
  them.

## [2.2.0] - 2026-10-26
### Added
- Raking is visible to other players: when someone rakes leaves together, everyone around sees their own leaves
  gather into the pile, with the raking sound.
  - With Rustling Leaves on the server, the exact spot of every stroke is passed on to the players nearby. Installing
    it on the server stays optional, and players without the mod can still join.
  - Without it on the server, your game recognises a rake stroke when another player sneaks with a hoe or shovel and
    swings it at the ground. Digging while sneaking with a shovel can then look like raking.

## [2.1.0] - 2026-10-26
### Added
- Compatibility with [Realistic Waves](https://www.curseforge.com/minecraft/mc-mods/realistic-waves): leaves floating
  on the sea ride its swell and surf instead of lying on the flat water underneath. They rise and sink with every wave,
  tilt with its slope and land on a crest where they touch it; they still drift, gather into rafts and soak as before.
- Compatibility with [Falling Leaves](https://www.curseforge.com/minecraft/mc-mods/falling-leaves-forge): with both mods
  installed, trees no longer shed twice - Rustling Leaves takes over, and the flat leaf particles of Falling Leaves are
  left out. Its snow falling from snowy leaves stays.
- New client options `leaves.realisticWaves` (turn the wave riding off) and `leaves.fallingLeavesMod` (keep the
  leaves of Falling Leaves as well).

## [2.0.1] - 2026-10-25
### Removed
- The wind sound of leaf whirlwinds. Rustling Leaves makes no wind sounds any more; the rustle of leaves under your
  feet and the raking sound stay.

## [2.0.0] - 2026-10-18
### Removed
- The leaf tools (Leaf Blower, Leaf Bag, Autumn Bomb, Staff of Winds) and their recipes. Rustling Leaves is purely
  client-side again: it adds no items, entities or network traffic, so it works on any server and with any set of
  mods. Tools already in a world disappear from it.

### Fixed
- Opening the creative inventory no longer crashes the game on servers that do not have the mod.

### Changed
- Works with NeoForge 21.1.144 and newer (it needed 21.1.253 before).

## [1.4.3] - 2026-10-13
### Changed
- Raking leaves with a hoe or shovel now needs sneaking (sneak + right click). Without sneaking the hoe tills the
  ground and the shovel makes paths as usual, also where leaves lie.

## [1.4.2] - 2026-10-12
### Fixed
- With a low render distance, fallen and falling leaves no longer show up beyond it, hanging in the fog where no
  ground is drawn. Leaves are drawn only over the terrain the game renders.

## [1.4.1] - 2026-10-11
### Fixed
- Leaf carpets and piles on slabs, stairs and other non-full blocks no longer stretch into slanted sheets over the
  step: the layer stays level up to the edge of the block it lies on.
- Leaves on the ground no longer flicker in the distance. Far away the litter is drawn in a simplified, solid form
  (patches of leaves lying a little above the ground) instead of a layer with holes, and halfway out carpets and piles
  show their leaf layer without tiny single leaves on top.
- The leaf layer of carpets and piles no longer repeats the same pattern block by block.
- Leaves no longer hover half a block above fences, walls and fence gates: they lie on the top of the post, where
  the block is drawn.
- Placing a block over fallen leaves no longer lifts them on top of it. Under a slab, a carpet, a fence or another
  partial block the leaves stay where they lie; a full block smothers them.

## [1.4.0] - 2026-10-10
### Added
- Fallen leaves rot away. Leaves on the ground no longer stay forever: they thin out slowly, leaf by leaf, and near
  you the last ones crumble away instead of just vanishing. A natural carpet lasts about ten in-game days, thick piles
  much longer. Under the trees new leaves keep falling and the forest floor fills up again over time, also where you
  have not been for a while; paths you cleared and piles you raked slowly go back to the forest. Ground you come back
  to after a long time has rotted for as long as you were away.
- Snow and leaves do not mix: leaves are gone as soon as snow covers them, and leaves on snow rot within a minute or
  two. Leaf piles of Immersive Weathering are not affected, they rot only with their block.
- New client option `leaves.litterLifetime` (in-game days until a carpet of fallen leaves has rotted away, 0 keeps the
  leaves until something moves them).
- New client options `seasons.springSummerFallRate` (leaf fall in spring and summer) and `seasons.seasonalDecay` (how
  fast the litter rots in late autumn and winter).

### Changed
- With [Serene Seasons](https://www.curseforge.com/minecraft/mc-mods/serene-seasons), leaves fall only when they should: leaf fall is an autumn event now. The first leaves come down
  in early autumn, most of them in mid and late autumn and the last few brown ones in early winter; in winter, spring
  and summer the trees keep their leaves (cherry trees still shed their blossom in spring). From late autumn on the
  leaves on the ground rot faster, so they are gone early in winter, before the snow builds up - in step with the
  seasons however long they are set in Serene Seasons. Ground you see for the first time gets a thick carpet of leaves
  only in autumn; in spring and summer just a scatter of old leaves, in winter none. Tropical biomes and dimensions
  without seasons are not affected.
- `seasons.autumnFallRate` now sets leaf fall at the peak of autumn (mid and late autumn), and `seasons.winterFallRate`
  defaults to 0 (no leaves in mid and late winter; a config file from an older version keeps its value).

## [1.3.0] - 2026-10-10
### Added
- Compatibility with [Immersive Weathering](https://modrinth.com/mod/immersive-weather-renewed) (1.21.1 port): its leaf piles are now drawn as Rustling
  Leaves litter instead of flat blocks - soft, rounded piles that blend into the forest floor and into each other, in
  the color of the tree they came from. A pile stays as long as its block: when Immersive Weathering lets it grow,
  leaves fall onto it from the tree above and it fills up with them like any other leaf pile; it rots away as
  Immersive Weathering decides, and breaking it throws up a handful of leaves. Piles stacked higher than a block are
  one tall pile. Leaves falling onto a pile stay on top and are blown and kicked around as anywhere else.
- New client option `leaves.immersiveWeatheringPiles` to keep Immersive Weathering's own leaf pile look instead;
  falling leaves then land on top of the piles instead of sinking into them.

## [1.2.0] - 2026-10-09
### Added
- Seasons with [Serene Seasons](https://www.curseforge.com/minecraft/mc-mods/serene-seasons): in autumn many more leaves fall and more and more of them turn yellow, orange and red, until by
  late autumn nearly every falling leaf does; in winter hardly a leaf falls (the litter on the ground stays); in
  spring only a few leaves fall, while blooming cherry trees shed a few more petals; summer stays as before. Leaf fall
  changes gradually through the sub-seasons. Biomes and dimensions without seasons (tropical biomes, dimensions not
  whitelisted in Serene Seasons) are not affected.
- New client config section `seasons`: turn the season effect off, or set how many leaves fall in late autumn and in
  winter and how strongly the autumn colors grow. These multiply the existing fall rate and autumn color options.

## [1.1.0] - 2026-10-07
### Added
- Every tree sheds its own way: birches drop leaves more often, spruces and other conifers hardly ever lose a needle.
  The new `treeFallRates` client option turns this off.

### Changed
- Cherry trees no longer drop the vanilla petal particles that ignored wind and the ground: every one of them is now a
  simulated petal that flutters down and stays on the ground, at the same rate as in vanilla.

## [1.0.0] - 2026-10-06
### Added
- Physically simulated falling leaves: trees shed leaves that flutter and glide down.
- A forest floor of leaf litter: carpets under trees, drifts against walls and trunks, natural leaf piles. Piles keep
  a natural slope and are saved with the world.
- Wading through piles pushes part of the leaves aside and leaves a trail; a few leaves fly up after you. Sneaking keeps a thin
  carpet undisturbed, jumping into a pile splashes leaves up.
- Raking: right click leaf litter with a hoe or shovel to gather it into a pile.
- Leaves behave like a granular material: piles settle into cones, fill hollows and bowls and run over the rim.
- Leaf Blower: blow paths through the litter and heap leaves up.
- Leaf Bag: suck leaves up, pour them out anywhere, compost them.
- Autumn Bomb: a throwable burst of autumn leaves.
- Staff of Winds: raise leaf whirlwinds and send squalls.
- Visible wind: gust waves that skitter leaves over the ground, squalls that strip the trees, leaf whirlwinds.
- Wind charges and explosions blow leaves away; breaking leaves blocks releases a burst of leaves.
- Leaves on water darken as they soak, drift and spin with the current, gather into rafts and along banks, go down
  waterfalls, are pushed under by pouring water and finally sink to the bottom;
  they burn in lava. Litter falls when the block under it is removed.
- Leaf colors match the tree and biome, with autumn colors; different shapes for broad-leaved trees, birch, spruce
  and cherry.
- Rustling sounds; client config for leaves, wind, interactions and sound.
- Works with shader packs ([Iris](https://modrinth.com/mod/iris)): leaves are lit and shaded by the pack like the blocks around them.
