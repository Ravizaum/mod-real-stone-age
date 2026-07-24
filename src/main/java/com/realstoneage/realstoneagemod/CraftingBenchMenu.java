package com.realstoneage.realstoneagemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;

// A vanilla CraftingMenu (full 3x3 grid, identical recipe access) with one difference: taking a
// crafted item out of the result slot also spends one of the Crafting Bench's limited uses,
// destroying the block once they run out. Reuses 100% of vanilla's crafting-grid/recipe-matching
// logic - the only override is which Slot class fills the result slot, plus stillValid (vanilla's
// own checks against Blocks.CRAFTING_TABLE specifically, which isn't us).
public class CraftingBenchMenu extends CraftingMenu {
    private final ContainerLevelAccess access;
    private final CraftingBenchBlockEntity blockEntity;

    public CraftingBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access, CraftingBenchBlockEntity blockEntity) {
        super(containerId, inventory, access);
        // addResultSlot (called from the super constructor above, before these fields exist) can't
        // reach blockEntity/access directly, so wire them into the already-created slot after the
        // fact instead.
        this.access = access;
        this.blockEntity = blockEntity;
        ((BenchResultSlot) this.getResultSlot()).init(blockEntity, access);
    }

    @Override
    protected Slot addResultSlot(Player player, int x, int y) {
        return this.addSlot(new BenchResultSlot(player, this.craftSlots, this.resultSlots, 0, x, y));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, RealStoneAge.CRAFTING_BENCH_BLOCK.get());
    }

    private static class BenchResultSlot extends ResultSlot {
        private CraftingBenchBlockEntity blockEntity;
        private ContainerLevelAccess access;

        BenchResultSlot(Player player, CraftingContainer craftSlots, Container container, int id, int x, int y) {
            super(player, craftSlots, container, id, x, y);
        }

        void init(CraftingBenchBlockEntity blockEntity, ContainerLevelAccess access) {
            this.blockEntity = blockEntity;
            this.access = access;
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            super.onTake(player, carried);
            if (blockEntity.decrementUses()) {
                access.execute((level, pos) -> {
                    if (level instanceof ServerLevel) {
                        SoundType sound = level.getBlockState(pos).getSoundType();
                        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
                        blockEntity.clearCrackStage();
                        level.removeBlock(pos, false);
                    }
                });
            }
        }
    }
}
