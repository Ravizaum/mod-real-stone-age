# Real Stone Age — Feature Overview

A NeoForge mod for Minecraft 26.2 that pushes early survival back toward a stone-age
progression: wood and gold tools are gone, stone tools can be made from loose rocks or
flint, copper tooling is an accessible (if fragile) mid-step, and furnaces need a
blast furnace to process ore or stone at all.

## Tools & Progression

- **Wooden tools removed.** The wooden pickaxe, axe, shovel, hoe, and sword recipes no
  longer exist.
- **Golden tools and armor removed.** All six golden tool/armor recipes (pickaxe, axe,
  shovel, hoe, sword, helmet, chestplate, leggings, boots) no longer exist.
- **Stone tools can be crafted from flint or rocks**, in addition to cobblestone,
  blackstone, and cobbled deepslate. (`minecraft:stone_tool_materials` tag extended
  with `minecraft:flint` and `realstoneage:rock`.)
- **Copper tools and armor**, craftable directly from raw copper (no smelting
  required) as an *additional* recipe alongside the normal ingot-based one:
  pickaxe, axe, shovel, hoe, sword, helmet, chestplate, leggings, boots. Each comes
  out at **half durability** (`minecraft:damage` component pre-set to half max) if not smelted.

## New Items & Blocks

- **Rock** (`realstoneage:rock`) — a stone-tool ingredient, styled after a button.
  Placeable in the world (floor-only, like a button), breaks instantly, doesn't block
  movement. Its inventory/dropped-item icon is scaled up 30% relative to the in-world
  block model.
- **Flint** — no new item is registered; the *vanilla* `minecraft:flint` item is given
  a placeable form. Right-clicking a flint item onto a floor places a decorative flint
  shard block (`realstoneage:flint`, floor-only, no collision); breaking it or
  middle-click-picking it gives back plain vanilla flint. The block's model traces the
  flint icon's actual diamond silhouette (per-texture-row multi-box geometry) rather
  than a plain rectangular slab, and is roughly 20% thinner than its first draft.
- **Bellows** (`realstoneage:bellows`) — a plain, non-placeable item used to build a
  blast furnace. Crafted from 6 leather + 2 sticks. Uses the vanilla composter's icon
  for now.

## World Generation

- **Rocks and flint shards** scatter across the overworld surface (grass, stone, or
  sand — any exposed block with air above it), independently of each other:
  - ~16.7% of chunks get a "hit" for each feature (`rarity_filter` chance = 1-in-6).
  - A hit chunk scatters up to **10 candidates** (spread ±5 blocks horizontally, ±3
    vertically), each placed only if the target spot is valid — so a typical hit
    yields several rocks or flints across the chunk.

## Breaking Blocks

- **Punching copper ore or deepslate copper ore** without a pickaxe drops **1 raw
  copper**. This drop is spawned directly (not via the vanilla "correct tool"
  mechanism), so **mining speed for these blocks is completely unaffected** — punching
  them is exactly as slow as vanilla, only the drop changes. Stone-family blocks
  (stone, cobblestone, deepslate, cobbled deepslate, mossy cobblestone) drop nothing
  when punched, same as vanilla.
- **Logs are as tough to break as stone.** Chopping any log (including stripped
  variants, via the `minecraft:logs` tag) now takes as long as breaking stone/
  cobblestone does by hand — regardless of tool, verified against vanilla's actual
  `getDestroyProgress` calculation (hardness × correct-tool divisor), not just a
  flat speed multiplier.
- **Logs no longer drop wood without an axe.** Breaking a log with anything other
  than a tool tagged `minecraft:axes` (fist, pickaxe, shovel, etc.) drops nothing,
  unlike vanilla where logs always drop themselves regardless of tool.

## Furnaces

- **Regular furnaces can no longer smelt ores or stone.** Smelting recipes for
  iron/gold/copper ore (all variants, including raw-metal-only smelting) and
  cobblestone→stone are disabled for the regular furnace (`minecraft:smelting`);
  the corresponding **blasting** recipes remain active, so a blast furnace is
  required for all of that.
- **Blast furnace recipe:** 8 bricks + 1 bellows (in a ring, bellows in the center).
- **Blast furnace runs at normal furnace speed** — all blasting-recipe cook times
  were doubled from the vanilla 100 ticks to 200 ticks, matching the regular
  furnace exactly (no more "blast furnace is 2x faster" vanilla behavior).

## World Generation (Ore)

- **Coal never generates above Y48** (15 blocks below sea level, Y63). The upper coal
  band (vanilla Y136+, the "coal on mountainsides" band) is disabled outright; the
  buried coal band's height cap is lowered from Y192 to Y48. Below that, coal
  generates exactly as vanilla does.
- **Iron and copper veins above Y48 show at most one exposed block, and never place
  more than 2 ore blocks total.** Vein geometry (spread/radius) above Y48 is left at
  vanilla's normal size (9 for iron, 10-20 for copper) so a vein has the same odds of
  reaching an existing cave or cliff face as vanilla does — only the *number of blocks
  actually placed* is capped, to 2, so breaking the one exposed block doesn't reveal a
  full vanilla-size buried vein behind it. Of those (at most 2) placed blocks, only
  one may be exposed to air (a cave wall, a cliff face, open sky); every other exposed
  candidate is discarded instead. Below Y48, both vein size and exposure are fully
  vanilla. This needed a custom worldgen feature (`CappedExposureOreFeature`,
  registered as `realstoneage:ore_exposed_capped`) since vanilla's ore feature has no
  concept of a placement cap independent of the vein's spread; it's a copy of
  vanilla's `OreFeature` placement algorithm with running placed/exposed counters
  added. Implemented by splitting each vanilla ore feature at Y48 into an unchanged
  "deep" placement and a new `realstoneage:*_surface` placement (same vein size, new
  feature type), injected via a `neoforge:add_features` biome modifier at the
  `underground_ores` step.

## Loot Table Tweaks

- **Leather drop from animals increased 50%** (cow, horse, llama, trader llama,
  donkey, mule, mooshroom, hoglin) — base drop range widened from 0–3 to 0–4.5.
- **Stick drops from foliage**, all scaled to the same **20% chance of the stick-drop
  roll firing, dropping 0-2 sticks (uniform) when it does** per break — so at most a
  13.3% chance of walking away with at least one stick:
  - All leaf types (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry,
    pale oak, azalea, flowering azalea)
  - Leaf litter
  - Bush
  - Dead bush (now matches the others instead of its old, much higher vanilla rate)

## Technical Notes (for future maintenance)

- NeoForge ships its own datapack overrides for a number of vanilla recipes and loot
  tables (using `#c:` convention tags and `neoforge:can_item_perform_ability` /
  `shears_dig` checks) at a pack priority **higher** than any mod's own data files.
  Both the recipe removals/replacements and the leaf/bush/dead-bush loot table
  changes have to be force-applied in code (`ModifyRecipeJsonsEvent` and
  `LootTableLoadEvent` respectively in `RealStoneAge.java`) rather than relying on
  plain datapack JSON files, or they get silently overwritten.
- The forced loot table JSON is embedded directly as Java string literals rather than
  read from the mod's own resource files at runtime, because
  `getResourceAsStream("data/minecraft/loot_table/...")` is ambiguous on the merged
  mod/vanilla classpath and was resolving to vanilla's copy instead of ours.

## License

MIT — see [LICENSE](LICENSE). Free to use, fork, and include in modpacks.

Project scaffolding originally generated from the [NeoForge MDK](https://github.com/NeoForged/MDK).

## Support

Manda um pix paizão: `ravifmattar@gmail.com`
