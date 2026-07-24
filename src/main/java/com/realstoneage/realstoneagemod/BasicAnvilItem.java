package com.realstoneage.realstoneagemod;

import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

// A plain BlockItem rendering the usual inventory durability bar from BasicAnvilBlockEntity's
// remaining-uses tracking - see CraftingBenchItem, whose identical bar formulas this copies.
public class BasicAnvilItem extends BlockItem {
    public BasicAnvilItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BasicAnvilBlockEntity.getUsesLeft(stack) < BasicAnvilBlockEntity.MAX_USES;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int usesLeft = BasicAnvilBlockEntity.getUsesLeft(stack);
        return Mth.clamp(Math.round(13.0F * usesLeft / BasicAnvilBlockEntity.MAX_USES), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int usesLeft = BasicAnvilBlockEntity.getUsesLeft(stack);
        float fraction = Math.max(0.0F, (float) usesLeft / BasicAnvilBlockEntity.MAX_USES);
        return Mth.hsvToRgb(fraction / 3.0F, 1.0F, 1.0F);
    }
}
