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
- **Retuned stone/copper/iron/diamond tool stats**, applied via NeoForge's
  `ModifyDefaultComponentsEvent` directly on the vanilla items. Every field below is
  explicitly declared per tier (`TierToolStats` in `RealStoneAge.java`), even where it
  matches vanilla, so the table is a complete standalone spec rather than a diff:

  | Tier | Durability | Efficiency (speed) | Enchantability | Sword damage | Axe damage |
  |---|---|---|---|---|---|
  | Stone | 125 (was 131) | 4 | 5 | 3 (was 4) | 4 (was 8) |
  | Copper | 190 | 5 | 10 (was 13) | 4 | 6 (was 8) |
  | Iron | 350 (was 250) | 6 | 10 (was 14) | 5 | 8 |
  | Diamond | 1750 (was 1561) | 8 | 10 | 6 | 8 |

  Stone and copper axes/swords lose a noticeable chunk of their attack damage on top
  of stone's already-short lifespan; iron becomes dramatically more durable while
  keeping vanilla iron's mining speed; diamond gets a durability bump but is
  otherwise unchanged. Pickaxe/shovel/hoe damage and every tier's attack speed are
  left at vanilla values throughout.
- **Retuned leather/copper/iron/diamond armor stats**, same mechanism and same
  explicit-per-tier-table approach (`ArmorTierStats`):

  | Material | Helmet dur. | Chest dur. | Legs dur. | Boots dur. | Helmet def | Chest def | Legs def | Boots def | Toughness | Knockback resist |
  |---|---|---|---|---|---|---|---|---|---|---|
  | Leather | 80 (was 55) | 100 (was 80) | 100 (was 75) | 80 (was 65) | 1 | 3 | 3 (was 2) | 1 | 0.0 | 0.5 (was 0.0) |
  | Copper | 125 (was 121) | 175 (was 176) | 175 (was 165) | 125 (was 143) | 2 | 4 | 4 (was 3) | 2 (was 1) | 0.0 | 0.0 |
  | Iron | 200 (was 165) | 250 (was 240) | 250 (was 225) | 200 (was 195) | 2 | 6 | 6 (was 5) | 2 | 1.0 (was 0.0) | 0.0 |
  | Diamond | 400 (was 363) | 500 (was 528) | 500 (was 495) | 400 (was 429) | 3 | 8 | 8 (was 6) | 3 | 2.0 (unchanged) | 0.0 |

  Leather gets a durability and leg-defense bump plus some knockback resistance (a
  first for leather); copper, iron, and diamond all get more consistent durability
  across pieces and stronger leg defense; iron notably picks up armor toughness for
  the first time.

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
- **Stick Bundle** (`realstoneage:stick_bundle`) — a plain, non-placeable item used to
  build a Crafting Bench. Crafted from 4 sticks in a 2x2 shape.
- **Crafting Bench** (`realstoneage:crafting_bench`) — a wood-free, temporary
  alternative to the vanilla Crafting Table, meant to break the mod's own
  bootstrapping deadlock: axes/pickaxes need a 3x3 crafting grid, a real Crafting
  Table needs planks, and planks need an axe to cut (see "Breaking Blocks" below) -
  without some wood-free way to reach a 3x3 grid there'd be no way to ever get started.
  - Crafted from 2 Rocks + 2 Stick Bundles in a 2x2 shape — fits the player's personal
    inventory grid, so it needs zero prior infrastructure to obtain.
  - Right-clicking it opens a full 3x3 crafting UI with the same recipe access as a
    real Crafting Table.
  - Survives 10 successful crafts, then self-destructs with no drop.
  - Manually mining it before then drops it as an item instead, with its remaining
    uses preserved, so it can be picked up and relocated.
  - Stacks like a normal item (up to 64). Remaining uses aren't tracked via item
    durability (a damageable item is forced to a max stack size of 1 in vanilla) -
    instead it's a plain int tucked into the item's custom data component, present
    only when uses are below the max, so fresh (full-uses) benches still stack
    together with each other and with ones straight out of the recipe.
  - Still shows the usual inventory durability bar despite not using real item
    durability - `CraftingBenchItem` overrides `isBarVisible`/`getBarWidth`/
    `getBarColor` to feed vanilla's own bar-rendering formulas from the custom-data
    uses-left value instead of the (unused) damage component.
  - Has its own block textures (front/side/top).
  - Added to the vanilla `minecraft:mineable/axe` tag so axes get their normal mining
    speed bonus on it, matching the real Crafting Table (custom blocks aren't in any
    `mineable_with_*` tag by default, so without this every tool - including axes -
    would mine it at the generic unlisted-tool speed).
  - Uses `SoundType.WOOD` for its place/break sounds, matching the real Crafting
    Table (a plain `Block` defaults to a stone-ish sound otherwise). Self-destructing
    after the 10th use also plays this same break sound, even though no player is
    actually mining it at that moment.
  - Shows its remaining uses visually with the same crack overlay a player sees
    while actively mining a block, without anyone actually mining it - each craft
    rebroadcasts a `destroyBlockProgress` packet (using a fake, position-derived
    "breaker" id instead of a real player) so nearby clients see progressively more
    cracked textures as uses run out. Since that overlay auto-clears after 20 seconds
    without an update, a block-entity ticker resends it periodically for as long as
    the bench has any wear; it's cleared immediately whenever the bench is removed
    (mined or self-destructed) so the crack doesn't linger on whatever replaces it.

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
- **Logs no longer drop wood without an axe.** Breaking a log with anything other
  than a tool tagged `minecraft:axes` (fist, pickaxe, shovel, etc.) drops nothing,
  unlike vanilla where logs always drop themselves regardless of tool.
- **Logs, planks, wooden slabs, and wooden stairs now effectively require an axe for
  full mining speed**, the same way stone blocks require a pickaxe. Vanilla never
  gives these blocks the "requires correct tool" flag stone has, so breaking them
  with the wrong tool never got vanilla's speed penalty; this reproduces that penalty
  by hand (mining speed × 30/100 without an axe), which works out to the exact same
  final speed as if the block had that flag set. Deliberately scoped to just these
  four block tags rather than all of `minecraft:mineable_with_axe`, which also covers
  things like bookshelves, ladders, and scaffolding.
- **Logs' hardness is equalized to stone's** (1.5, down from vanilla's 2.0). A
  block's hardness is baked into the Block instance at construction with no
  datapack/component override available (unlike item stats), so this reproduces an
  effective hardness of 1.5 by hand: mining speed on any `minecraft:logs` block is
  multiplied by 2.0/1.5, which yields the exact same final break progress per tick
  as if the block's hardness were actually 1.5.

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
- The Crafting Bench's limited-uses UI (`CraftingBenchBlock`/`CraftingBenchBlockEntity`
  /`CraftingBenchMenu`) is built almost entirely on top of vanilla's own crafting
  classes rather than reimplementing the crafting grid: `CraftingBenchMenu` extends
  vanilla's `CraftingMenu` and overrides only `addResultSlot` (to swap in a custom
  `ResultSlot` that decrements a use counter on take) and `stillValid` (vanilla's own
  checks against `Blocks.CRAFTING_TABLE` specifically, which isn't us) - no
  crafting-grid or recipe-matching logic is duplicated. The use counter itself lives
  in a small `BlockEntity` (since a `Menu` isn't persisted across game sessions) and
  is modeled as item durability on the block's own item form, so it survives
  place/break cycles for free via vanilla's existing damage-value item component
  instead of a bespoke one.

## License

MIT — see [LICENSE](LICENSE). Free to use, fork, and include in modpacks.

Project scaffolding originally generated from the [NeoForge MDK](https://github.com/NeoForged/MDK).

## Support

Manda um pix paizão: `ravifmattar@gmail.com`
