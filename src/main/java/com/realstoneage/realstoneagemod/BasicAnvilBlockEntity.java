package com.realstoneage.realstoneagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// Tracks remaining uses for a placed Basic Anvil - identical mechanic to CraftingBenchBlockEntity
// (see that class for the crack-overlay/drop-preservation rationale), just with its own use count.
// Unlike a vanilla Anvil (which lasts forever), a Basic Anvil breaks after MAX_USES Forge crafts -
// ForgeMenu is what actually calls decrementUses() when a craft is taken. This class has no
// interaction/menu logic of its own; right-clicking either anvil type is handled centrally by
// RealStoneAge#onRightClickAnvil, which checks for an adjacent Blast Furnace before opening ForgeMenu.
public class BasicAnvilBlockEntity extends BlockEntity {
    public static final int MAX_USES = 16;
    private static final int CRACK_REBROADCAST_INTERVAL_TICKS = 40;

    private int usesLeft = MAX_USES;

    public BasicAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(RealStoneAge.BASIC_ANVIL_BLOCK_ENTITY.get(), pos, state);
    }

    public int getUsesLeft() {
        return usesLeft;
    }

    public void setUsesLeft(int usesLeft) {
        this.usesLeft = usesLeft;
        setChanged();
        broadcastCrackStage();
    }

    // Called from ForgeMenu when a craft is taken. Returns true if this was the last use and the
    // block should now be destroyed.
    public boolean decrementUses() {
        usesLeft--;
        setChanged();
        broadcastCrackStage();
        return usesLeft <= 0;
    }

    private int breakerId() {
        return -Math.abs(worldPosition.hashCode()) - 1;
    }

    private void broadcastCrackStage() {
        if (level instanceof ServerLevel serverLevel) {
            int stage = Mth.clamp(MAX_USES - usesLeft, 0, 9);
            serverLevel.destroyBlockProgress(breakerId(), worldPosition, stage);
        }
    }

    public void clearCrackStage() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(breakerId(), worldPosition, -1);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BasicAnvilBlockEntity blockEntity) {
        if (blockEntity.usesLeft < MAX_USES && level.getGameTime() % CRACK_REBROADCAST_INTERVAL_TICKS == 0) {
            blockEntity.broadcastCrackStage();
        }
    }

    // See CraftingBenchBlockEntity's identical helpers for why this is a plain int component
    // rather than real item durability.
    public static int getUsesLeft(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("uses_left", MAX_USES);
    }

    public static void setUsesLeft(ItemStack stack, int usesLeft) {
        if (usesLeft >= MAX_USES) {
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
}
