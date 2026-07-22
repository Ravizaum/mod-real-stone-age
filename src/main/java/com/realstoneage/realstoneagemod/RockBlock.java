package com.realstoneage.realstoneagemod;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

// A purely decorative small ground object (rock, flint, ...) - placeable only on top of a block
// (like a flower or sapling), never on walls or ceilings.
public class RockBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RockBlock> CODEC = simpleCodec(p -> new RockBlock(p, false));

    // Small, button-sized footprint (30% bigger than vanilla's own button), centered on the surface.
    // Kept in sync with models/block/rock.json's element.
    private static final VoxelShape SMALL_FLOOR_NS = Block.box(4.1, 0.0, 5.4, 11.9, 2.6, 10.6);
    private static final VoxelShape SMALL_FLOOR_EW = Block.box(5.4, 0.0, 4.1, 10.6, 2.6, 11.9);

    // Larger, near-half-block footprint (used so a full texture/icon isn't cropped), thinner than
    // the small shape - kept in sync with models/block/flint.json's element heights.
    private static final VoxelShape LARGE_FLOOR_SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 1.6, 12.0);

    private final boolean largeFootprint;

    public RockBlock(BlockBehaviour.Properties properties, boolean largeFootprint) {
        super(properties);
        this.largeFootprint = largeFootprint;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<RockBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (this.largeFootprint) {
            return LARGE_FLOOR_SHAPE;
        }
        boolean northSouth = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        return northSouth ? SMALL_FLOOR_NS : SMALL_FLOOR_EW;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        // The flint block has no item of its own (vanilla flint stays the one true item), so
        // middle-clicking it should pick vanilla flint rather than an item that doesn't exist.
        return this.largeFootprint ? new ItemStack(Items.FLINT) : super.getCloneItemStack(level, pos, state, includeData);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }
}
