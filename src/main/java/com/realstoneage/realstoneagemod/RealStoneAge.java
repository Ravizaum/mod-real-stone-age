package com.realstoneage.realstoneagemod;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

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
    // Create a Deferred Register to hold BlockEntityTypes which will all be registered under the "realstoneage" namespace
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold RecipeSerializers which will all be registered under the "realstoneage" namespace
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    // Create a Deferred Register to hold MenuTypes which will all be registered under the "realstoneage" namespace
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);

    // Ore feature used by the above-Y48 "surface" iron/copper configured features - allows at most
    // one exposed-to-air block per vein instead of vanilla's all-or-nothing discard chance.
    public static final DeferredHolder<Feature<?>, CappedExposureOreFeature> ORE_EXPOSED_CAPPED =
            FEATURES.register("ore_exposed_capped", () -> new CappedExposureOreFeature(OreConfiguration.CODEC));

    // A rock, usable in place of cobblestone for stone tools, placeable in the world just like a button
    public static final DeferredBlock<RockBlock> ROCK_BLOCK = BLOCKS.registerBlock("rock", p -> new RockBlock(p, RockBlock.Footprint.SMALL),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noCollision().strength(0.12F).pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<BlockItem> ROCK = ITEMS.registerSimpleBlockItem("rock", ROCK_BLOCK);

    // A decorative flint shard found lying on the ground (world gen only; no placeable item -
    // vanilla flint stays a plain item, obtained from gravel or by breaking this block)
    public static final DeferredBlock<RockBlock> FLINT_BLOCK = BLOCKS.registerBlock("flint", p -> new RockBlock(p, RockBlock.Footprint.LARGE),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noCollision().strength(0.12F).pushReaction(PushReaction.DESTROY));

    // A decorative stick found lying on the ground (world gen only; no placeable item - vanilla
    // stick stays a plain item, obtained from leaves/bushes or by breaking this block)
    public static final DeferredBlock<RockBlock> STICK_BLOCK = BLOCKS.registerBlock("stick", p -> new RockBlock(p, RockBlock.Footprint.STICK),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).noCollision().strength(0.12F).pushReaction(PushReaction.DESTROY));

    // Bellows, used to build a blast furnace. Not placeable in the world.
    public static final DeferredItem<Item> BELLOWS = ITEMS.registerSimpleItem("bellows");

    // A bundle of 4 sticks, used to build a Crafting Bench. Not placeable in the world.
    public static final DeferredItem<Item> STICK_BUNDLE = ITEMS.registerSimpleItem("stick_bundle");

    // A wood-free, temporary Crafting Table - see CraftingBenchBlock/CraftingBenchBlockEntity for
    // the limited-uses mechanic.
    public static final DeferredBlock<CraftingBenchBlock> CRAFTING_BENCH_BLOCK = BLOCKS.registerBlock("crafting_bench", CraftingBenchBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noLootTable().noOcclusion());
    public static final DeferredItem<CraftingBenchItem> CRAFTING_BENCH =
            ITEMS.registerItem("crafting_bench", props -> new CraftingBenchItem(CRAFTING_BENCH_BLOCK.get(), props),
                    () -> new Item.Properties().useBlockDescriptionPrefix());
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<CraftingBenchBlockEntity>> CRAFTING_BENCH_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("crafting_bench",
                    () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(CraftingBenchBlockEntity::new, CRAFTING_BENCH_BLOCK.get()));

    // Recipe type for ForgeCraftingRecipe - deliberately NOT registered in a registry. RecipeMap
    // groups recipes by RecipeType identity alone (see RecipeMap#byType), so a plain instance from
    // RecipeType.simple is sufficient for recipe lookup to work; only the RecipeSerializer (which
    // the "type" field in recipe JSON actually dispatches on) needs registering below.
    public static final RecipeType<ForgeCraftingRecipe> FORGE_CRAFTING_TYPE =
            RecipeType.simple(Identifier.fromNamespaceAndPath(MODID, "forge_crafting"));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ForgeCraftingRecipe>> FORGE_CRAFTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("forge_crafting", () -> ForgeCraftingRecipe.SERIALIZER);

    // The Forge menu - opened by right-clicking a vanilla Anvil or a Basic Anvil that currently has
    // an adjacent Blast Furnace (see onRightClickAnvil below). Neither anvil type has its own block
    // entity that owns this menu type, since the vanilla Anvil is an unmodified vanilla block.
    public static final DeferredHolder<MenuType<?>, MenuType<ForgeMenu>> FORGE_MENU =
            MENU_TYPES.register("forge", () -> new MenuType<>(ForgeMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    // The Basic Anvil - a cheaper, limited-durability stand-in for a vanilla Anvil (see
    // BasicAnvilBlockEntity.MAX_USES), used the same way: right-click it next to a Blast Furnace to
    // open the Forge menu.
    public static final DeferredBlock<BasicAnvilBlock> BASIC_ANVIL_BLOCK = BLOCKS.registerBlock("basic_anvil", BasicAnvilBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5.0F, 1200.0F).sound(SoundType.ANVIL).noLootTable().noOcclusion());
    public static final DeferredItem<BasicAnvilItem> BASIC_ANVIL =
            ITEMS.registerItem("basic_anvil", props -> new BasicAnvilItem(BASIC_ANVIL_BLOCK.get(), props),
                    () -> new Item.Properties().useBlockDescriptionPrefix());
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<BasicAnvilBlockEntity>> BASIC_ANVIL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("basic_anvil",
                    () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(BasicAnvilBlockEntity::new, BASIC_ANVIL_BLOCK.get()));

    // Recipes to remove entirely (wood/gold tools+armor can no longer be crafted; vanilla's own
    // copper-ingot tool+armor recipes, and iron/diamond tools+armor, move to being Forge-exclusive -
    // see the forge_copper_*/forge_iron_*/forge_diamond_* recipes and ForgeCraftingRecipe. The
    // raw-copper recipes stay Crafting-Table-craftable as before - see copper_*_from_raw_copper.json).
    // The vanilla Anvil recipe is deliberately NOT removed - it's craftable as normal, since it's now
    // central to the Forge system (see onRightClickAnvil).
    private static final String[] REMOVED_RECIPES = {
            "wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_hoe", "wooden_sword",
            "golden_pickaxe", "golden_axe", "golden_shovel", "golden_hoe", "golden_sword",
            "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots",
            "copper_pickaxe", "copper_axe", "copper_shovel", "copper_hoe", "copper_sword",
            "copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots",
            "iron_pickaxe", "iron_axe", "iron_shovel", "iron_hoe", "iron_sword",
            "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
            "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe", "diamond_sword",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"
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
        BLOCK_ENTITIES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        MENU_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);

        // Register our items into the appropriate vanilla creative tabs
        modEventBus.addListener(this::addCreative);

        // Retune stone/copper/iron/diamond tool stats
        modEventBus.addListener(this::onModifyDefaultComponents);
        // Retune leather/copper/iron/diamond armor stats
        modEventBus.addListener(this::onModifyDefaultArmorComponents);
    }

    // Complete, explicit stat table for the retuned tiers - every field is set on every tier's
    // tools regardless of whether it actually differs from vanilla, so this table is the single
    // source of truth for tool balance rather than a diff against whatever vanilla happens to ship.
    // axeAttackSpeed is vanilla's per-tier axe attack-speed modifier, kept as-is since only the
    // damage number is being retuned.
    private record TierToolStats(int durability, float miningSpeed, int enchantability,
                                  float swordDamage, float axeDamage, float axeAttackSpeed,
                                  TagKey<Block> incorrectBlocksForDrops) {
    }

    private static final TierToolStats STONE_STATS =
            new TierToolStats(125, 4.0F, 5, 3.0F, 4.0F, -3.2F, ToolMaterial.STONE.incorrectBlocksForDrops());
    private static final TierToolStats COPPER_STATS =
            new TierToolStats(190, 5.0F, 10, 4.0F, 6.0F, -3.2F, ToolMaterial.COPPER.incorrectBlocksForDrops());
    private static final TierToolStats IRON_STATS =
            new TierToolStats(350, 6.0F, 10, 5.0F, 8.0F, -3.1F, ToolMaterial.IRON.incorrectBlocksForDrops());
    private static final TierToolStats DIAMOND_STATS =
            new TierToolStats(1750, 8.0F, 10, 6.0F, 8.0F, -3.0F, ToolMaterial.DIAMOND.incorrectBlocksForDrops());

    private void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        applyTierToolStats(event, STONE_STATS, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE, Items.STONE_SWORD);
        applyTierToolStats(event, COPPER_STATS, Items.COPPER_PICKAXE, Items.COPPER_AXE, Items.COPPER_SHOVEL, Items.COPPER_HOE, Items.COPPER_SWORD);
        applyTierToolStats(event, IRON_STATS, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SWORD);
        applyTierToolStats(event, DIAMOND_STATS, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_SWORD);
    }

    private void applyTierToolStats(ModifyDefaultComponentsEvent event, TierToolStats stats,
                                     Item pickaxe, Item axe, Item shovel, Item hoe, Item sword) {
        event.modify(pickaxe, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.durability());
            builder.set(DataComponents.ENCHANTABLE, new Enchantable(stats.enchantability()));
            builder.set(DataComponents.TOOL, miningTool(context, stats, BlockTags.MINEABLE_WITH_PICKAXE));
        });
        event.modify(shovel, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.durability());
            builder.set(DataComponents.ENCHANTABLE, new Enchantable(stats.enchantability()));
            builder.set(DataComponents.TOOL, miningTool(context, stats, BlockTags.MINEABLE_WITH_SHOVEL));
        });
        event.modify(hoe, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.durability());
            builder.set(DataComponents.ENCHANTABLE, new Enchantable(stats.enchantability()));
            builder.set(DataComponents.TOOL, miningTool(context, stats, BlockTags.MINEABLE_WITH_HOE));
        });
        event.modify(axe, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.durability());
            builder.set(DataComponents.ENCHANTABLE, new Enchantable(stats.enchantability()));
            builder.set(DataComponents.TOOL, miningTool(context, stats, BlockTags.MINEABLE_WITH_AXE));
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS, attackAttributes(stats.axeDamage(), stats.axeAttackSpeed()));
        });
        event.modify(sword, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.durability());
            builder.set(DataComponents.ENCHANTABLE, new Enchantable(stats.enchantability()));
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS, attackAttributes(stats.swordDamage(), -2.4F));
        });
    }

    // Reconstructs a pickaxe/axe/shovel/hoe's TOOL component (which block tag it's incorrect for,
    // which tag it mines efficiently and at what speed) the same way vanilla's ToolMaterial does,
    // just pulling the numbers from our own tier table instead of ToolMaterial's built-in fields.
    private static Tool miningTool(HolderLookup.Provider context, TierToolStats stats, TagKey<Block> minesEfficiently) {
        var blocks = context.lookupOrThrow(Registries.BLOCK);
        return new Tool(
                java.util.List.of(
                        Tool.Rule.deniesDrops(blocks.getOrThrow(stats.incorrectBlocksForDrops())),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(minesEfficiently), stats.miningSpeed())
                ),
                1.0F, 1, true
        );
    }

    // Rebuilds the mainhand attack-damage/attack-speed attribute modifiers with a new attack
    // damage total (attack speed left at vanilla's value for that item).
    private static ItemAttributeModifiers attackAttributes(float attackDamage, float attackSpeedBaseline) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeedBaseline, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    // Complete, explicit per-piece stat table for the retuned armor materials - every field is set
    // regardless of whether it differs from vanilla, same rationale as TierToolStats above.
    private record ArmorTierStats(int helmetDurability, int chestplateDurability, int leggingsDurability, int bootsDurability,
                                   int helmetDefense, int chestplateDefense, int leggingsDefense, int bootsDefense,
                                   float toughness, float knockbackResistance) {
    }

    private static final ArmorTierStats LEATHER_STATS = new ArmorTierStats(80, 100, 100, 80, 1, 3, 3, 1, 0.0F, 0.5F);
    private static final ArmorTierStats COPPER_ARMOR_STATS = new ArmorTierStats(125, 175, 175, 125, 2, 4, 4, 2, 0.0F, 0.0F);
    private static final ArmorTierStats IRON_ARMOR_STATS = new ArmorTierStats(200, 250, 250, 200, 2, 6, 6, 2, 1.0F, 0.0F);
    private static final ArmorTierStats DIAMOND_ARMOR_STATS = new ArmorTierStats(400, 500, 500, 400, 3, 8, 8, 3, 2.0F, 0.0F);

    private void onModifyDefaultArmorComponents(ModifyDefaultComponentsEvent event) {
        applyArmorTierStats(event, LEATHER_STATS, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        applyArmorTierStats(event, COPPER_ARMOR_STATS, Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS);
        applyArmorTierStats(event, IRON_ARMOR_STATS, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        applyArmorTierStats(event, DIAMOND_ARMOR_STATS, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
    }

    private void applyArmorTierStats(ModifyDefaultComponentsEvent event, ArmorTierStats stats,
                                      Item helmet, Item chestplate, Item leggings, Item boots) {
        event.modify(helmet, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.helmetDurability());
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    armorAttributes("helmet", stats.helmetDefense(), stats.toughness(), stats.knockbackResistance(), EquipmentSlotGroup.HEAD));
        });
        event.modify(chestplate, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.chestplateDurability());
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    armorAttributes("chestplate", stats.chestplateDefense(), stats.toughness(), stats.knockbackResistance(), EquipmentSlotGroup.CHEST));
        });
        event.modify(leggings, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.leggingsDurability());
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    armorAttributes("leggings", stats.leggingsDefense(), stats.toughness(), stats.knockbackResistance(), EquipmentSlotGroup.LEGS));
        });
        event.modify(boots, (builder, context, i) -> {
            builder.set(DataComponents.MAX_DAMAGE, stats.bootsDurability());
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    armorAttributes("boots", stats.bootsDefense(), stats.toughness(), stats.knockbackResistance(), EquipmentSlotGroup.FEET));
        });
    }

    // Rebuilds an armor piece's defense/toughness/knockback-resistance attribute modifiers, mirroring
    // vanilla ArmorMaterial#createAttributes's structure and modifier-id naming ("armor.<piece>").
    private static ItemAttributeModifiers armorAttributes(String pieceName, int defense, float toughness,
                                                            float knockbackResistance, EquipmentSlotGroup slotGroup) {
        Identifier modifierId = Identifier.withDefaultNamespace("armor." + pieceName);
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(modifierId, defense, AttributeModifier.Operation.ADD_VALUE), slotGroup)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(modifierId, toughness, AttributeModifier.Operation.ADD_VALUE), slotGroup)
                .add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(modifierId, knockbackResistance, AttributeModifier.Operation.ADD_VALUE), slotGroup)
                .build();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ROCK);
            event.accept(STICK_BUNDLE);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BELLOWS);
            event.accept(CRAFTING_BENCH);
            event.accept(BASIC_ANVIL);
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

    // Vanilla stick has no block form of its own, so give the plain item a block-placing
    // interaction (mirroring what a BlockItem would do) instead of registering a new item.
    @SubscribeEvent
    public void onUseStickOnBlock(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK
                || !event.getItemStack().is(Items.STICK)) {
            return;
        }

        var context = new BlockPlaceContext(event.getUseOnContext());
        var pos = context.getClickedPos();
        var level = context.getLevel();
        var placedState = STICK_BLOCK.get().getStateForPlacement(context);
        if (placedState == null || !context.canPlace() || !placedState.canSurvive(level, pos)) {
            return;
        }

        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, placedState);
            SoundType sound = STICK_BLOCK.get().defaultBlockState().getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
            var player = event.getPlayer();
            if (player != null && !player.getAbilities().instabuild) {
                event.getItemStack().shrink(1);
            }
        }
        event.cancelWithResult(InteractionResult.SUCCESS);
    }

    private static final net.minecraft.network.chat.Component FORGE_TITLE = net.minecraft.network.chat.Component.translatable("container.realstoneage.forge");

    // Repurposes right-clicking a vanilla Anvil or a Basic Anvil: instead of the normal
    // repair/rename menu (which this mod drops entirely), it opens the Forge menu - but only when
    // the anvil currently has a Blast Furnace in an adjacent block space. With no adjacent furnace,
    // right-clicking either anvil does nothing. This check is purely live (done on every
    // right-click, and again in ForgeMenu#stillValid while the menu is open) - there's no stored
    // link, so moving the furnace away just makes the anvil useless again.
    @SubscribeEvent
    public void onRightClickAnvil(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getEntity().level();
        BlockPos pos = event.getPos();
        var state = level.getBlockState(pos);
        boolean isBasicAnvil = state.is(BASIC_ANVIL_BLOCK.get());
        if (!state.is(Blocks.ANVIL) && !isBasicAnvil) {
            return;
        }

        // Suppress the block's own default interaction (vanilla's repair/rename menu) on both
        // sides, without touching the held item's own interaction (e.g. placing another block
        // against this one still works normally).
        event.setUseBlock(net.minecraft.util.TriState.FALSE);
        if (level.isClientSide() || !hasAdjacentBlastFurnace(level, pos)) {
            return;
        }

        BasicAnvilBlockEntity blockEntity = isBasicAnvil ? (BasicAnvilBlockEntity) level.getBlockEntity(pos) : null;
        MenuProvider menuProvider = new SimpleMenuProvider(
                (containerId, inventory, player) -> new ForgeMenu(containerId, inventory, ContainerLevelAccess.create(level, pos), blockEntity),
                FORGE_TITLE);
        event.getEntity().openMenu(menuProvider);
    }

    public static boolean hasAdjacentBlastFurnace(LevelReader level, BlockPos pos) {
        return findAdjacentBlastFurnace(level, pos).isPresent();
    }

    public static Optional<BlockPos> findAdjacentBlastFurnace(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.getBlockState(neighbor).is(Blocks.BLAST_FURNACE)) {
                return Optional.of(neighbor);
            }
        }
        return Optional.empty();
    }

    // How long the Blast Furnace visually "lights up" for after a Forge craft is taken (see
    // ForgeMenu#onCraftTaken) - purely cosmetic, doesn't touch the furnace's own real fuel/burn
    // state, so it's tracked independently here rather than on the furnace's block entity.
    private static final int FURNACE_FLASH_DURATION_TICKS = 20;
    private static final Map<ServerLevel, Map<BlockPos, Integer>> PENDING_FURNACE_UNLIGHTS = new HashMap<>();

    public static void flashBlastFurnaceLight(ServerLevel level, BlockPos furnacePos) {
        BlockState state = level.getBlockState(furnacePos);
        if (!state.is(Blocks.BLAST_FURNACE)) {
            return;
        }
        if (!state.getValue(BlastFurnaceBlock.LIT)) {
            level.setBlock(furnacePos, state.setValue(BlastFurnaceBlock.LIT, true), 3);
        }
        PENDING_FURNACE_UNLIGHTS.computeIfAbsent(level, l -> new HashMap<>()).put(furnacePos, FURNACE_FLASH_DURATION_TICKS);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_FURNACE_UNLIGHTS.isEmpty()) {
            return;
        }
        var levelIterator = PENDING_FURNACE_UNLIGHTS.entrySet().iterator();
        while (levelIterator.hasNext()) {
            var levelEntry = levelIterator.next();
            ServerLevel level = levelEntry.getKey();
            var posIterator = levelEntry.getValue().entrySet().iterator();
            while (posIterator.hasNext()) {
                var posEntry = posIterator.next();
                int ticksLeft = posEntry.getValue() - 1;
                if (ticksLeft <= 0) {
                    BlockPos pos = posEntry.getKey();
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.BLAST_FURNACE) && state.getValue(BlastFurnaceBlock.LIT)) {
                        level.setBlock(pos, state.setValue(BlastFurnaceBlock.LIT, false), 3);
                    }
                    posIterator.remove();
                } else {
                    posEntry.setValue(ticksLeft);
                }
            }
            if (levelEntry.getValue().isEmpty()) {
                levelIterator.remove();
            }
        }
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

    // Logs normally drop themselves regardless of tool in vanilla; suppress that drop entirely
    // unless the tool used is an axe.
    @SubscribeEvent
    public void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (event.getState().is(net.minecraft.tags.BlockTags.LOGS) && !event.getTool().is(ItemTags.AXES)) {
            event.setCanceled(true);
        }
    }

    // Logs, planks, wooden slabs, and wooden stairs don't require a "correct tool" in vanilla the
    // way stone requires a pickaxe, so breaking them without an axe never gets hit with the same
    // 30->100 mining-speed divisor penalty stone blocks get. This reproduces that penalty by hand:
    // multiplying speed by 30/100 without an axe gives the exact same final getDestroyProgress
    // result as if the block had vanilla's "requires correct tool" flag set, without needing to
    // touch the block's own (unmodifiable-via-datapack) properties. Deliberately scoped to just
    // these four tags rather than the full minecraft:mineable_with_axe tag (which also covers
    // things like bookshelves, ladders, and scaffolding).
    @SubscribeEvent
    public void onWoodBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        var state = event.getState();
        boolean isWoodFamily = state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.tags.BlockTags.PLANKS)
                || state.is(net.minecraft.tags.BlockTags.WOODEN_SLABS)
                || state.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS);
        if (isWoodFamily && !event.getEntity().getMainHandItem().is(ItemTags.AXES)) {
            event.setNewSpeed(event.getNewSpeed() * 0.3F);
        }
    }

    // Logs have vanilla hardness 2.0 vs stone's 1.5. A block's hardness is baked into the Block
    // instance at construction (no datapack/component override exists for it, unlike item stats),
    // so this reproduces an equivalent hardness of 1.5 by hand: getDestroyProgress divides speed by
    // hardness, so multiplying speed by (2.0 / 1.5) here yields the exact same final progress as if
    // the block's hardness were actually 1.5.
    @SubscribeEvent
    public void onLogHardness(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getState().is(net.minecraft.tags.BlockTags.LOGS)) {
            event.setNewSpeed(event.getNewSpeed() * (2.0F / 1.5F));
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
            java.util.Map.entry("blocks/oak_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:oak_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:oak_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.005,0.0055555557,0.00625,0.008333334,0.025],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:apple\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/oak_leaves\"}"),
            java.util.Map.entry("blocks/spruce_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:spruce_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:spruce_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/spruce_leaves\"}"),
            java.util.Map.entry("blocks/birch_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:birch_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:birch_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/birch_leaves\"}"),
            java.util.Map.entry("blocks/jungle_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:jungle_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.025,0.027777778,0.03125,0.041666668,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:jungle_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/jungle_leaves\"}"),
            java.util.Map.entry("blocks/acacia_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:acacia_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:acacia_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/acacia_leaves\"}"),
            java.util.Map.entry("blocks/dark_oak_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:dark_oak_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:dark_oak_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.005,0.0055555557,0.00625,0.008333334,0.025],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:apple\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/dark_oak_leaves\"}"),
            java.util.Map.entry("blocks/mangrove_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:mangrove_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}]}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/mangrove_leaves\"}"),
            java.util.Map.entry("blocks/cherry_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:cherry_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:cherry_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/cherry_leaves\"}"),
            java.util.Map.entry("blocks/pale_oak_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:pale_oak_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:pale_oak_sapling\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/pale_oak_leaves\"}"),
            java.util.Map.entry("blocks/azalea_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:azalea_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:azalea\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/azalea_leaves\"}"),
            java.util.Map.entry("blocks/flowering_azalea_leaves", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"name\":\"minecraft:flowering_azalea_leaves\"},{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"},{\"chances\":[0.05,0.0625,0.083333336,0.1],\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\"}],\"name\":\"minecraft:flowering_azalea\"}]}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/flowering_azalea_leaves\"}"),
            java.util.Map.entry("blocks/bush", "{\"type\":\"minecraft:block\",\"pools\":[{\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}],\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:bush\"}],\"rolls\":1.0},{\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}}],\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/bush\"}"),
            java.util.Map.entry("blocks/dead_bush", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.2}],\"functions\":[{\"count\":{\"type\":\"minecraft:uniform\",\"max\":2.0,\"min\":0.0},\"function\":\"minecraft:set_count\"},{\"function\":\"minecraft:explosion_decay\"}],\"name\":\"minecraft:stick\"}],\"rolls\":1.0}],\"random_sequence\":\"minecraft:blocks/dead_bush\"}")
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
