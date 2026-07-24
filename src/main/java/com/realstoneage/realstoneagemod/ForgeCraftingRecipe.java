package com.realstoneage.realstoneagemod;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

// A shaped-crafting recipe usable only inside ForgeMenu (see that class), never in a vanilla
// Crafting Table or this mod's own Crafting Bench. That exclusivity is the whole reason this class
// exists instead of a plain minecraft:crafting_shaped recipe: recipe lookup groups recipes by
// RecipeType identity (RecipeMap#byType), and anything registered under RecipeType.CRAFTING is
// automatically visible to every vanilla-crafting-grid menu. Giving these their own RecipeType
// keeps them out of that pool entirely, while reusing vanilla's own ShapedRecipePattern/
// ItemStackTemplate so the JSON format stays byte-for-byte a normal shaped recipe.
public class ForgeCraftingRecipe implements Recipe<CraftingInput> {
    public static final MapCodec<ForgeCraftingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result)
            ).apply(i, ForgeCraftingRecipe::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeCraftingRecipe> STREAM_CODEC = StreamCodec.composite(
            ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
            ItemStackTemplate.STREAM_CODEC, o -> o.result,
            ForgeCraftingRecipe::new
    );
    public static final RecipeSerializer<ForgeCraftingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    public ForgeCraftingRecipe(ShapedRecipePattern pattern, ItemStackTemplate result) {
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return this.result.create();
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<ForgeCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<ForgeCraftingRecipe> getType() {
        return RealStoneAge.FORGE_CRAFTING_TYPE;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
