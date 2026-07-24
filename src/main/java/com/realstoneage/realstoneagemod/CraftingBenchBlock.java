package com.realstoneage.realstoneagemod;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

// A wood-free, temporary Crafting Table: full 3x3 crafting access (see CraftingBenchMenu), but it
// only survives CraftingBenchBlockEntity.MAX_USES crafts before self-destructing with no drop.
// Manually mining it before then instead drops it as an item with its remaining uses preserved
// (see playerWillDestroy/setPlacedBy, CraftingBenchBlockEntity#getUsesLeft/setUsesLeft) - drops are
// handled entirely by hand here, there's deliberately no loot table (getDrops always returns empty).
public class CraftingBenchBlock extends BaseEntityBlock {
    public static final MapCodec<CraftingBenchBlock> CODEC = simpleCodec(CraftingBenchBlock::new);

    public CraftingBenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CraftingBenchBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingBenchBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealStoneAge.CRAFTING_BENCH_BLOCK_ENTITY.get(), CraftingBenchBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CraftingBenchBlockEntity blockEntity) {
            blockEntity.setUsesLeft(CraftingBenchBlockEntity.getUsesLeft(stack));
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CraftingBenchBlockEntity blockEntity) {
            ItemStack drop = new ItemStack(RealStoneAge.CRAFTING_BENCH.get());
            CraftingBenchBlockEntity.setUsesLeft(drop, blockEntity.getUsesLeft());
            Block.popResource(level, pos, drop);
            blockEntity.clearCrackStage();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return Collections.emptyList();
    }
}
