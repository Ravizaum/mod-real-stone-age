package com.realstoneage.realstoneagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// Tracks remaining uses for a placed Crafting Bench (starts at MAX_USES, one use per completed
// craft - see CraftingBenchMenu's result slot). Reaching zero destroys the block with no drop;
// manually mining it before then instead drops it as an item with the remaining uses preserved
// as item durability - see CraftingBenchBlock#playerWillDestroy.
//
// Wear is also shown visually using the same block-breaking crack overlay a player sees while
// actively mining a block (ClientLevel/ServerLevel#destroyBlockProgress), rather than an active
// mining action. Since that overlay is tied to a fake "breaker" id (see breakerId()) instead of a
// real player, it's broadcast to everyone nearby and needs re-sending periodically - the client
// auto-clears any crack overlay that goes 400 ticks (20s) without an update - which the ticker
// below does every 40 ticks. It's cleared explicitly (progress -1) whenever the block goes away,
// see CraftingBenchBlock#playerWillDestroy and CraftingBenchMenu's self-destruct path.
public class CraftingBenchBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_USES = 10;
    private static final int CRACK_REBROADCAST_INTERVAL_TICKS = 40;

    private int usesLeft = MAX_USES;

    public CraftingBenchBlockEntity(BlockPos pos, BlockState state) {
        super(RealStoneAge.CRAFTING_BENCH_BLOCK_ENTITY.get(), pos, state);
    }

    public int getUsesLeft() {
        return usesLeft;
    }

    public void setUsesLeft(int usesLeft) {
        this.usesLeft = usesLeft;
        setChanged();
        broadcastCrackStage();
    }

    // Called from the crafting menu's result slot when a craft is taken. Returns true if this
    // was the last use and the block should now be destroyed.
    public boolean decrementUses() {
        usesLeft--;
        setChanged();
        broadcastCrackStage();
        return usesLeft <= 0;
    }

    // A stable, negative (so it never collides with a real entity/player id) id derived from this
    // block's position, used as the "breaker" for the persistent crack-overlay packets below.
    private int breakerId() {
        return -Math.abs(worldPosition.hashCode()) - 1;
    }

    private void broadcastCrackStage() {
        if (level instanceof ServerLevel serverLevel) {
            int stage = Mth.clamp(MAX_USES - usesLeft, 0, 9);
            serverLevel.destroyBlockProgress(breakerId(), worldPosition, stage);
        }
    }

    // Called when the bench is about to be removed (manually mined or self-destructed), so the
    // crack overlay doesn't linger on whatever ends up at this position afterward.
    public void clearCrackStage() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(breakerId(), worldPosition, -1);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CraftingBenchBlockEntity blockEntity) {
        if (blockEntity.usesLeft < MAX_USES && level.getGameTime() % CRACK_REBROADCAST_INTERVAL_TICKS == 0) {
            blockEntity.broadcastCrackStage();
        }
    }

    // Remaining uses on the *item* form (dropped/held, not yet placed) can't be modeled as real
    // item durability - a damageable item is forced to a max stack size of 1 by vanilla, and this
    // item needs to stack - so it's a plain int tucked into DataComponents.CUSTOM_DATA instead.
    // Stacks only merge when their components match exactly, so fresh (full-uses) benches all stack
    // together as normal, while partially-used ones only stack with others at that exact use count.
    // CraftingBenchItem reads this to render a normal-looking durability bar despite not using the
    // real damage component.
    public static int getUsesLeft(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("uses_left", MAX_USES);
    }

    public static void setUsesLeft(ItemStack stack, int usesLeft) {
        if (usesLeft >= MAX_USES) {
            // Omit the component entirely at full uses, so fresh benches stack with each other
            // (and with ones straight out of the crafting recipe, which also have no component set).
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("uses_left", usesLeft));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        usesLeft = input.getIntOr("uses_left", MAX_USES);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("uses_left", usesLeft);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.crafting");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CraftingBenchMenu(containerId, inventory, ContainerLevelAccess.create(level, worldPosition), this);
    }
}
