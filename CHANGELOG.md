# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - Unreleased
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
