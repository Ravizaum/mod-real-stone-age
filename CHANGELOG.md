# Changelog

All notable changes to this mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

## [1.0.0] - 2026-07-22

### Added

- Rock item/block: a stone-tool ingredient, placeable in the world, breaks instantly.
- Flint placement: right-clicking vanilla flint onto a floor places a decorative flint
  shard block; breaking it or picking it returns plain flint.
- Bellows item, used to craft a blast furnace (8 bricks + 1 bellows).
- Rocks and flint shards scatter across the overworld surface during world generation.
- Stone tools can now be crafted from flint or rocks, in addition to cobblestone,
  blackstone, and cobbled deepslate.
- Copper tools and armor craftable directly from raw copper (no smelting required),
  at half durability if not smelted.
- Punching stone-family blocks (stone, cobblestone, deepslate, cobbled deepslate,
  mossy cobblestone) by hand now drops 1-2 rocks instead of nothing.
- Punching copper ore or deepslate copper ore by hand now drops 1 raw copper.
- Leather drop rate from animals (cow, horse, llama, trader llama, donkey, mule,
  mooshroom, hoglin) increased 50%.
- Stick drops from all leaf types, leaf litter, bush, and dead bush standardized to a
  25% chance per break.

### Changed

- Regular furnaces can no longer smelt ores or cobblestone into stone; a blast
  furnace is required.
- Blast furnace cook time doubled (100 -> 200 ticks) to match regular furnace speed.
- Logs take as long to break by hand as stone/cobblestone, regardless of tool.

### Removed

- Wooden tool recipes (pickaxe, axe, shovel, hoe, sword).
- Golden tool and armor recipes (pickaxe, axe, shovel, hoe, sword, helmet,
  chestplate, leggings, boots).

[Unreleased]: https://github.com/Ravizaum/mod-real-stone-age/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Ravizaum/mod-real-stone-age/releases/tag/v1.0.0
