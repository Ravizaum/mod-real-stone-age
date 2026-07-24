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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

// A purely decorative small ground object (rock, flint, stick, ...) - placeable only on top of a
// block (like a flower or sapling), never on walls or ceilings.
public class RockBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RockBlock> CODEC = simpleCodec(p -> new RockBlock(p, Footprint.SMALL));

    public enum Footprint { SMALL, LARGE, STICK }

    // Small, button-sized footprint (30% bigger than vanilla's own button), centered on the surface.
    // Kept in sync with models/block/rock.json's element.
    private static final VoxelShape SMALL_FLOOR_NS = Block.box(4.1, 0.0, 5.4, 11.9, 2.6, 10.6);
    private static final VoxelShape SMALL_FLOOR_EW = Block.box(5.4, 0.0, 4.1, 10.6, 2.6, 11.9);

    // Larger, near-half-block footprint (used so a full texture/icon isn't cropped), thinner than
    // the small shape - kept in sync with models/block/flint.json's element heights.
    private static final VoxelShape LARGE_FLOOR_SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 1.6, 12.0);

    // Per-texture-row footprint of the diagonal stick silhouette traced from the vanilla stick
    // icon, one {x1, z1, x2, z2} entry per row - kept in sync with models/block/stick.json's
    // elements (same coordinates, already centered on the block so it rotates cleanly below).
    private static final double[][] STICK_NORTH_ROWS = {
            {12.5, 1.5, 14.5, 2.5},
            {11.5, 2.5, 14.5, 3.5},
            {10.5, 3.5, 13.5, 4.5},
            {9.5, 4.5, 12.5, 5.5},
            {8.5, 5.5, 11.5, 6.5},
            {7.5, 6.5, 10.5, 7.5},
            {6.5, 7.5, 9.5, 8.5},
            {5.5, 8.5, 8.5, 9.5},
            {4.5, 9.5, 7.5, 10.5},
            {3.5, 10.5, 6.5, 11.5},
            {2.5, 11.5, 5.5, 12.5},
            {1.5, 12.5, 4.5, 13.5},
            {1.5, 13.5, 3.5, 14.5},
    };

    // One shape per horizontal facing (indexed by clockwise quarter-turns from north, matching
    // the blockstate's own "y": 0/90/180/270 rotation), each the union of that row footprint
    // rotated the same amount around the block's center - i.e. the actual traced stick shape
    // rather than its bounding box, so the outline never pokes out past (or falls short of) the
    // visible model at any facing.
    private static final VoxelShape[] STICK_FLOOR_SHAPES = buildStickShapes();

    private static VoxelShape[] buildStickShapes() {
        VoxelShape[] shapes = new VoxelShape[4];
        for (int quarters = 0; quarters < 4; quarters++) {
            VoxelShape shape = Shapes.empty();
            for (double[] row : STICK_NORTH_ROWS) {
                shape = Shapes.joinUnoptimized(shape, rotatedRowBox(row, quarters), BooleanOp.OR);
            }
            shapes[quarters] = shape.optimize();
        }
        return shapes;
    }

    // Rotates a row's {x1, z1, x2, z2} corners by 90 degrees clockwise (viewed from above) per
    // quarter, around the block's center (8, 8), then re-derives an axis-aligned box from the
    // rotated corners (a 90-degree turn of an axis-aligned box is itself axis-aligned).
    private static VoxelShape rotatedRowBox(double[] row, int quarters) {
        double ax = row[0], az = row[1], bx = row[2], bz = row[3];
        double[] corner1 = rotatedCorner(ax, az, quarters);
        double[] corner2 = rotatedCorner(bx, bz, quarters);
        double minX = Math.min(corner1[0], corner2[0]);
        double maxX = Math.max(corner1[0], corner2[0]);
        double minZ = Math.min(corner1[1], corner2[1]);
        double maxZ = Math.max(corner1[1], corner2[1]);
        return Block.box(minX, 0.0, minZ, maxX, 1.6, maxZ);
    }

    private static double[] rotatedCorner(double x, double z, int quarters) {
        double dx = x - 8.0;
        double dz = z - 8.0;
        for (int i = 0; i < quarters; i++) {
            double nextDx = -dz;
            double nextDz = dx;
            dx = nextDx;
            dz = nextDz;
        }
        return new double[] { dx + 8.0, dz + 8.0 };
    }

    private final Footprint footprint;

    public RockBlock(BlockBehaviour.Properties properties, Footprint footprint) {
        super(properties);
        this.footprint = footprint;
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
        boolean northSouth = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        return switch (this.footprint) {
            case LARGE -> LARGE_FLOOR_SHAPE;
            case STICK -> STICK_FLOOR_SHAPES[quartersFromNorth(state.getValue(FACING))];
            case SMALL -> northSouth ? SMALL_FLOOR_NS : SMALL_FLOOR_EW;
        };
    }

    // Clockwise quarter-turns from north, matching the blockstate's own "y" rotation values
    // (north=0, east=90, south=180, west=270).
    private static int quartersFromNorth(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        // The flint and stick blocks have no item of their own (vanilla flint/stick stay the one
        // true items), so middle-clicking them should pick the vanilla item rather than one that
        // doesn't exist.
        return switch (this.footprint) {
            case LARGE -> new ItemStack(Items.FLINT);
            case STICK -> new ItemStack(Items.STICK);
            case SMALL -> super.getCloneItemStack(level, pos, state, includeData);
        };
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
