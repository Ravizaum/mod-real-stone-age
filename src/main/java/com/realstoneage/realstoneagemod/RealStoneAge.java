package com.realstoneage.realstoneagemod;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RealStoneAge.MODID)
public class RealStoneAge {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "realstoneage";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "realstoneage" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "realstoneage" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold worldgen Features which will all be registered under the "realstoneage" namespace
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);

    // Ore feature used by the above-Y48 "surface" iron/copper configured features - allows at most
    // one exposed-to-air block per vein instead of vanilla's all-or-nothing discard chance.
    public static final DeferredHolder<Feature<?>, CappedExposureOreFeature> ORE_EXPOSED_CAPPED =
            FEATURES.register("ore_exposed_capped", () -> new CappedExposureOreFeature(OreConfiguration.CODEC));

    // A rock, usable in place of cobblestone for stone tools, placeable in the world just like a button
    public static final DeferredBlock<RockBlock> ROCK_BLOCK = BLOCKS.registerBlock("rock", p -> new RockBlock(p, false),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noCollision().strength(0.12F).pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<BlockItem> ROCK = ITEMS.registerSimpleBlockItem("rock", ROCK_BLOCK);

    // A decorative flint shard found lying on the ground (world gen only; no placeable item -
    // vanilla flint stays a plain item, obtained from gravel or by breaking this block)
    public static final DeferredBlock<RockBlock> FLINT_BLOCK = BLOCKS.registerBlock("flint", p -> new RockBlock(p, true),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noCollision().strength(0.12F).pushReaction(PushReaction.DESTROY));

    // Bellows, used to build a blast furnace. Not placeable in the world.
    public static final DeferredItem<Item> BELLOWS = ITEMS.registerSimpleItem("bellows");

    // Recipes to remove entirely (wood and gold tools/armor can no longer be crafted)
    private static final String[] REMOVED_RECIPES = {
            "wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_hoe", "wooden_sword",
            "golden_pickaxe", "golden_axe", "golden_shovel", "golden_hoe", "golden_sword",
            "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots"
    };

    // Blocks that drop something extra when punched without a pickaxe - see onBreakBlock. This is
    // deliberately NOT done via the "correct tool" harvest-check mechanism, since that same check
    // also drives mining speed (30 vs 100 divisor); doing it there would make punching these
    // blocks faster too, which isn't wanted - only the drop changes, speed stays 100% vanilla.
    private static final Block[] PUNCHABLE_COPPER_ORE = { Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE };

    public RealStoneAge(IEventBus modEventBus) {
        // Register the Deferred Registers to the mod event bus so blocks and items get registered
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        FEATURES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);

        // Register our items into the appropriate vanilla creative tabs
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ROCK);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BELLOWS);
        }
    }

    @SubscribeEvent
    public void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        var recipeJsons = event.getRecipeJsons();

        for (String name : REMOVED_RECIPES) {
            recipeJsons.remove(Identifier.withDefaultNamespace(name));
        }

        // NeoForge itself overrides the blast furnace recipe with "c:" convention tags at a pack priority
        // higher than any mod's own data, so it must be forced back to the intended JSON here. (The extra
        // raw-copper tool/armor recipes don't need this treatment since they use new, non-clashing IDs.)
        replaceRecipe(recipeJsons, "blast_furnace", """
                {"type":"minecraft:crafting_shaped","key":{"B":"minecraft:bricks","#":"realstoneage:bellows"},"pattern":["BBB","B#B","BBB"],"result":{"id":"minecraft:blast_furnace"}}
                """);
    }

    private static void replaceRecipe(java.util.Map<Identifier, JsonElement> recipeJsons, String name, String json) {
        recipeJsons.put(Identifier.withDefaultNamespace(name), JsonParser.parseString(json));
    }

    // Vanilla flint has no block form of its own, so give the plain item a block-placing
    // interaction (mirroring what a BlockItem would do) instead of registering a new item.
    @SubscribeEvent
    public void onUseFlintOnBlock(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK
                || !event.getItemStack().is(Items.FLINT)) {
            return;
        }

        var context = new BlockPlaceContext(event.getUseOnContext());
        var pos = context.getClickedPos();
        var level = context.getLevel();
        var placedState = FLINT_BLOCK.get().getStateForPlacement(context);
        if (placedState == null || !context.canPlace() || !placedState.canSurvive(level, pos)) {
            return;
        }

        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, placedState);
            SoundType sound = FLINT_BLOCK.get().defaultBlockState().getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
            var player = event.getPlayer();
            if (player != null && !player.getAbilities().instabuild) {
                event.getItemStack().shrink(1);
            }
        }
        event.cancelWithResult(InteractionResult.SUCCESS);
    }

    // Punching copper ore without a pickaxe normally drops nothing at all; this adds a reduced drop
    // for that case, spawned manually so vanilla's own tool-correctness check (and the mining speed
    // it drives) is left completely alone.
    @SubscribeEvent
    public void onBreakBlock(BreakBlockEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        var tool = event.getPlayer().getMainHandItem();
        if (tool.is(ItemTags.PICKAXES)) {
            return;
        }

        Block block = event.getState().getBlock();
        for (Block ore : PUNCHABLE_COPPER_ORE) {
            if (block == ore) {
                Block.popResource(level, event.getPos(), new net.minecraft.world.item.ItemStack(Items.RAW_COPPER));
                return;
            }
        }
    }

    // Logs take 50% longer to break (i.e. break 1/1.5 as fast) than vanilla.
    @SubscribeEvent
    public void onBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getState().is(net.minecraft.tags.BlockTags.LOGS)) {
            event.setNewSpeed(event.getNewSpeed() / 2.5f);
        }
    }

    // Logs normally drop themselves regardless of tool in vanilla; suppress that drop entirely
    // unless the tool used is an axe.
    @SubscribeEvent
    public void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (event.getState().is(net.minecraft.tags.BlockTags.LOGS) && !event.getTool().is(ItemTags.AXES)) {
            event.setCanceled(true);
        }
    }

    // Loot table names (registry path, e.g. "blocks/oak_leaves") that NeoForge itself overrides
    // with its own "shears_dig" ability-based tool check at a pack priority higher than any mod's
    // own data - same shadowing issue as the crafting recipes handled in onModifyRecipeJsons, just
    // for loot tables instead. Forced back to our intended content in onLootTableLoad below.
    // Embedded directly as Java string literals (rather than read via classloader resource lookup)
    // because getResourceAsStream("data/minecraft/loot_table/...") is ambiguous on the merged
    // classpath and was resolving to vanilla's own copy instead of ours.
    private static final java.util.Map<String, String> FORCED_LOOT_TABLE_JSON = java.util.Map.ofEntries(
            java.util.Map.entry("blocks/oak_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:oak_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:oak_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.005,0.0055555557,0.00625,0.008333334,0.025],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:apple\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/oak_leaves\"}"),
            java.util.Map.entry("blocks/spruce_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:spruce_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:spruce_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/spruce_leaves\"}"),
            java.util.Map.entry("blocks/birch_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:birch_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:birch_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/birch_leaves\"}"),
            java.util.Map.entry("blocks/jungle_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:jungle_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.025,0.027777778,0.03125,0.041666668,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:jungle_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/jungle_leaves\"}"),
            java.util.Map.entry("blocks/acacia_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:acacia_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:acacia_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/acacia_leaves\"}"),
            java.util.Map.entry("blocks/dark_oak_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:dark_oak_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:dark_oak_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.005,0.0055555557,0.00625,0.008333334,0.025],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:apple\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/dark_oak_leaves\"}"),
            java.util.Map.entry("blocks/mangrove_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:mangrove_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}]}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/mangrove_leaves\"}"),
            java.util.Map.entry("blocks/cherry_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:cherry_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:cherry_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/cherry_leaves\"}"),
            java.util.Map.entry("blocks/pale_oak_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:pale_oak_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:pale_oak_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/pale_oak_leaves\"}"),
            java.util.Map.entry("blocks/azalea_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:azalea_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:azalea\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/azalea_leaves\"}"),
            java.util.Map.entry("blocks/flowering_azalea_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:flowering_azalea_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:flowering_azalea\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":1.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/flowering_azalea_leaves\"}"),
            java.util.Map.entry("blocks/bush", "{\"type\":\"minecraft:block\",\"pools\":[{\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:bush\"}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.375}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/bush\"}"),
            java.util.Map.entry("blocks/dead_bush", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.375}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/dead_bush\"}")
    );

    @SubscribeEvent
    public void onLootTableLoad(net.neoforged.neoforge.event.LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft")) {
            return;
        }
        String path = event.getName().getPath();
        String json = FORCED_LOOT_TABLE_JSON.get(path);
        if (json == null) {
            return;
        }

        var ops = event.getRegistries().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var parsed = net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(ops, JsonParser.parseString(json));
        parsed.resultOrPartial(error -> LOGGER.error("Failed to parse forced loot table {}: {}", path, error))
                .ifPresent(event::setTable);
    }
}
