package com.realstoneage.realstoneagemod;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

// The Basic Anvil: a cheaper, limited-durability stand-in for a vanilla Anvil - see
// BasicAnvilBlockEntity.MAX_USES. Mirrors CraftingBenchBlock's identical use-count/no-loot-table/
// preserve-on-drop pattern, plus vanilla AnvilBlock's FACING/rotation/hitbox behavior (minus its
// FallingBlock gravity, which this block doesn't have). Has no interaction logic of its own -
// right-clicking it (like a real Anvil) is handled centrally by RealStoneAge#onRightClickAnvil,
// which opens ForgeMenu only when this block has an adjacent Blast Furnace.
public class BasicAnvilBlock extends BaseEntityBlock {
    public static final MapCodec<BasicAnvilBlock> CODEC = simpleCodec(BasicAnvilBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    // Identical to AnvilBlock.SHAPES - the anvil's actual narrow-footprint hitbox (base, legs,
    // top slab), rotated per horizontal axis so it lines up with FACING.
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(
            Shapes.or(Block.column(12.0, 0.0, 4.0), Block.column(8.0, 10.0, 4.0, 5.0), Block.column(4.0, 8.0, 5.0, 10.0), Block.column(10.0, 16.0, 10.0, 16.0)));

    public BasicAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BasicAnvilBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).getAxis());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BasicAnvilBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealStoneAge.BASIC_ANVIL_BLOCK_ENTITY.get(), BasicAnvilBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BasicAnvilBlockEntity blockEntity) {
            blockEntity.setUsesLeft(BasicAnvilBlockEntity.getUsesLeft(stack));
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BasicAnvilBlockEntity blockEntity) {
            ItemStack drop = new ItemStack(RealStoneAge.BASIC_ANVIL.get());
            BasicAnvilBlockEntity.setUsesLeft(drop, blockEntity.getUsesLeft());
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
