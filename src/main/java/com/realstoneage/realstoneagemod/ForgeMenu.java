package com.realstoneage.realstoneagemod;

import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import org.jspecify.annotations.Nullable;

// The Forge menu: a 3x3 crafting grid laid out identically to a vanilla CraftingMenu, plus one fuel
// slot that only accepts coal (any type) and gets consumed - 1 per craft, alongside the grid
// ingredients - when the result is taken. Opened by right-clicking a vanilla Anvil or a Basic Anvil
// that currently has an adjacent Blast Furnace - see RealStoneAge#onRightClickAnvil. Both anvil
// types offer the exact same recipes; the only difference is a Basic Anvil (limitedUseSource
// non-null) breaks after BasicAnvilBlockEntity.MAX_USES crafts, while a real Anvil doesn't.
//
// Extends AbstractContainerMenu directly rather than vanilla's CraftingMenu/AbstractCraftingMenu,
// since recipe lookup there is hardwired to RecipeType.CRAFTING - see ForgeCraftingRecipe for why
// that has to be a distinct recipe type instead. No recipe-book integration; that's a deliberate
// scope reduction, since it's tied to the same vanilla RecipeType.CRAFTING machinery being avoided
// here.
public class ForgeMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    private static final int CRAFT_SLOT_START = 1;
    public static final int FUEL_SLOT = 10;
    private static final int INV_SLOT_START = 11;

    private final ContainerLevelAccess access;
    private final Player player;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final Container fuelSlotContainer = new SimpleContainer(1);
    private final @Nullable BasicAnvilBlockEntity limitedUseSource;

    public ForgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL, null);
    }

    public ForgeMenu(int containerId, Inventory inventory, ContainerLevelAccess access, @Nullable BasicAnvilBlockEntity limitedUseSource) {
        super(RealStoneAge.FORGE_MENU.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        this.limitedUseSource = limitedUseSource;

        this.addSlot(new ForgeResultSlot(124, 35));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                this.addSlot(new Slot(this.craftSlots, x + y * 3, 30 + x * 18, 17 + y * 18));
            }
        }
        this.addSlot(new ForgeFuelSlot(this.fuelSlotContainer, 0, 124, 60)
                .setBackground(Identifier.fromNamespaceAndPath(RealStoneAge.MODID, "container/slot/coal")));
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    private boolean hasFuel() {
        return !this.fuelSlotContainer.getItem(0).isEmpty();
    }

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((level, pos) -> {
            if (level instanceof ServerLevel serverLevel) {
                recalculateResult(serverLevel);
            }
        });
    }

    private void recalculateResult(ServerLevel level) {
        var input = this.craftSlots.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer) this.player;
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<ForgeCraftingRecipe>> maybeRecipe =
                level.getServer().getRecipeManager().getRecipeFor(RealStoneAge.FORGE_CRAFTING_TYPE, input, level);
        if (maybeRecipe.isPresent()) {
            RecipeHolder<ForgeCraftingRecipe> recipeHolder = maybeRecipe.get();
            if (this.resultSlots.setRecipeUsed(serverPlayer, recipeHolder)) {
                ItemStack recipeResult = recipeHolder.value().assemble(input);
                if (recipeResult.isItemEnabled(level.enabledFeatures())) {
                    result = recipeResult;
                }
            }
        }

        this.resultSlots.setItem(0, result);
        this.setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), RESULT_SLOT, result));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.craftSlots);
            this.clearContainer(player, this.fuelSlotContainer);
        });
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.resultSlots && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> {
            var state = level.getBlockState(pos);
            return (state.is(Blocks.ANVIL) || state.is(RealStoneAge.BASIC_ANVIL_BLOCK.get()))
                    && RealStoneAge.hasAdjacentBlastFurnace(level, pos)
                    && player.isWithinBlockInteractionRange(pos, 4.0);
        }, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex == RESULT_SLOT) {
                if (!this.hasFuel()) {
                    return ItemStack.EMPTY;
                }
                stack.getItem().onCraftedBy(stack, player);
                if (!this.moveItemStackTo(stack, INV_SLOT_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, clicked);
            } else if (slotIndex >= INV_SLOT_START) {
                if (!this.moveItemStackTo(stack, CRAFT_SLOT_START, FUEL_SLOT, false)
                        && !this.moveItemStackTo(stack, FUEL_SLOT, INV_SLOT_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, INV_SLOT_START, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == clicked.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stack);
            if (slotIndex == RESULT_SLOT) {
                player.drop(stack, false);
            }
        }

        return clicked;
    }

    private void onCraftTaken() {
        this.access.execute((level, pos) -> {
            if (level instanceof ServerLevel serverLevel) {
                // Same level event vanilla's own AnvilMenu uses for its repair sound.
                serverLevel.levelEvent(1030, pos, 0);
                RealStoneAge.findAdjacentBlastFurnace(level, pos).ifPresent(furnacePos -> RealStoneAge.flashBlastFurnaceLight(serverLevel, furnacePos));
            }
        });

        if (this.limitedUseSource != null && this.limitedUseSource.decrementUses()) {
            this.access.execute((level, pos) -> {
                if (level instanceof ServerLevel) {
                    SoundType sound = level.getBlockState(pos).getSoundType();
                    level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
                    this.limitedUseSource.clearCrackStage();
                    level.removeBlock(pos, false);
                }
            });
        }
    }

    // Not a vanilla ResultSlot, since that class's onTake hardcodes RecipeType.CRAFTING remainder
    // lookups that don't apply to ForgeCraftingRecipe. None of our recipes have crafting remainders
    // (no bucket-style leftovers), so this just shrinks each non-empty grid slot and the fuel slot
    // by 1 instead of reproducing that machinery.
    private class ForgeResultSlot extends Slot {
        ForgeResultSlot(int x, int y) {
            super(ForgeMenu.this.resultSlots, RESULT_SLOT, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.hasItem() && ForgeMenu.this.hasFuel();
        }

        @Override
        public boolean isFake() {
            return true;
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            carried.getItem().onCraftedBy(carried, player);
            net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(player, carried, ForgeMenu.this.craftSlots);

            for (int i = 0; i < ForgeMenu.this.craftSlots.getContainerSize(); i++) {
                if (!ForgeMenu.this.craftSlots.getItem(i).isEmpty()) {
                    ForgeMenu.this.craftSlots.removeItem(i, 1);
                }
            }
            ForgeMenu.this.fuelSlotContainer.removeItem(0, 1);

            ForgeMenu.this.onCraftTaken();
        }
    }

    private static class ForgeFuelSlot extends Slot {
        ForgeFuelSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.COALS);
        }
    }
}
