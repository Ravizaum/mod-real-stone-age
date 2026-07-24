package com.realstoneage.realstoneagemod;

import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

// A plain BlockItem, except it renders the usual inventory durability bar based on
// CraftingBenchBlockEntity's remaining-uses tracking instead of vanilla's real item-damage
// component - that component can't be used here since a damageable item is forced to a max stack
// size of 1, and this item needs to stack (see CraftingBenchBlockEntity#getUsesLeft). The bar
// width/color formulas below are copied from Item's own default (damage-component-based)
// implementations, just fed our own uses-left number instead.
public class CraftingBenchItem extends BlockItem {
    public CraftingBenchItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return CraftingBenchBlockEntity.getUsesLeft(stack) < CraftingBenchBlockEntity.MAX_USES;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int usesLeft = CraftingBenchBlockEntity.getUsesLeft(stack);
        return Mth.clamp(Math.round(13.0F * usesLeft / CraftingBenchBlockEntity.MAX_USES), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int usesLeft = CraftingBenchBlockEntity.getUsesLeft(stack);
        float fraction = Math.max(0.0F, (float) usesLeft / CraftingBenchBlockEntity.MAX_USES);
        return Mth.hsvToRgb(fraction / 3.0F, 1.0F, 1.0F);
    }
}
