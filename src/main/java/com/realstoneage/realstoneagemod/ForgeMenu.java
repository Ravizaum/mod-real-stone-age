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
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import org.jspecify.annotations.Nullable;

// The Forge menu: a 3x3 crafting grid laid out identically to a vanilla CraftingMenu, plus a Tool
// slot and a fuel slot. The Tool slot picks the current Working mode: a Hammer (anything in the
// realstoneage:hammers tag - a Rock or an Iron Hammer) selects Cold Working (no fuel needed, only
// Cold recipes match); a Mold selects Hot Working (needs 1 coal per craft, only Hot recipes match,
// and is only accepted at all when there's a currently-adjacent Blast Furnace - see
// hotWorkingAvailable). Opened by right-clicking a vanilla Anvil or a Basic Anvil - see
// RealStoneAge#onRightClickAnvil - regardless of whether a furnace is adjacent; the furnace
// adjacency check now only gates Hot Working availability inside the menu, checked live on every
// recalculation, rather than gating whether the menu opens at all. Both anvil types offer the
// exact same recipes; the only difference is a Basic Anvil (limitedUseSource non-null) breaks
// after BasicAnvilBlockEntity.MAX_USES crafts, while a real Anvil doesn't.
//
// Extends AbstractContainerMenu directly rather than vanilla's CraftingMenu/AbstractCraftingMenu,
// since recipe lookup there is hardwired to RecipeType.CRAFTING - see ForgeCraftingRecipe for why
// that has to be a distinct recipe type instead. No recipe-book integration; that's a deliberate
// scope reduction, since it's tied to the same vanilla RecipeType.CRAFTING machinery being avoided
// here.
public class ForgeMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    private static final int CRAFT_SLOT_START = 1;
    public static final int TOOL_SLOT = 10;
    public static final int FUEL_SLOT = 11;
    private static final int INV_SLOT_START = 12;

    private final ContainerLevelAccess access;
    private final Player player;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final Container toolSlotContainer = new SimpleContainer(1);
    private final Container fuelSlotContainer = new SimpleContainer(1);
    private final @Nullable BasicAnvilBlockEntity limitedUseSource;
    // Synced to the client via the standard DataSlot mechanism (like a furnace's burn-time fields),
    // rather than having the client independently re-derive furnace adjacency: the client's own copy
    // of this menu is constructed with ContainerLevelAccess.NULL (no real level/pos), so a client-side
    // access.evaluate(...) call would always see "no furnace" regardless of the real world state -
    // that would silently block the player from ever placing a Mold, since Slot#mayPlace runs
    // client-side too for click prediction. Recomputed every tick from the real adjacency check in
    // broadcastChanges() (server-side only; the client just receives whatever value the server sends).
    private final DataSlot hotWorkingAvailable = DataSlot.standalone();
    // The recipe currently matching the 3x3 grid, if any - tracked independently of the Tool/Fuel
    // slots (see recalculateResult) so the result preview always appears as soon as the grid itself
    // matches something, regardless of what order the player filled slots in or whether a Tool/Fuel
    // item is present yet. Whether it can actually be taken is a separate, always-live check - see
    // canCraftNow() - so the preview and the "can I have it" gate can disagree (e.g. right ingredients,
    // wrong or missing Tool) without the preview itself flickering in and out as Tool/Fuel change.
    private @Nullable RecipeHolder<ForgeCraftingRecipe> currentRecipe;
    // Synced copy of currentRecipe's workingType (0 = no recipe matched, 1 = COLD, 2 = HOT) - the
    // RecipeHolder itself isn't synced to the client, but ForgeScreen needs to know whether the
    // previewed result is actually takeable right now (see canCraftNow()) to render it faded when
    // it isn't, so this carries just the one bit of information that decision needs across.
    private final DataSlot requiredWorkingTypeOrdinal = DataSlot.standalone();

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
        // Side by side, one row below the result slot (stacking them vertically instead would
        // overlap the result slot above - 18px-tall slots need more than the ~6px of headroom
        // available between y=35 and y=60 here), centered as a pair under the result slot's own
        // center (124 + 9), with a few pixels of breathing room between the two.
        this.addSlot(new ForgeToolSlot(this.toolSlotContainer, 0, 113, 60)
                .setBackground(Identifier.fromNamespaceAndPath(RealStoneAge.MODID, "container/slot/tool")));
        this.addSlot(new ForgeFuelSlot(this.fuelSlotContainer, 0, 135, 60)
                .setBackground(Identifier.fromNamespaceAndPath(RealStoneAge.MODID, "container/slot/coal")));
        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlot(this.hotWorkingAvailable);
        this.addDataSlot(this.requiredWorkingTypeOrdinal);
    }

    private boolean hasFuel() {
        return !this.fuelSlotContainer.getItem(0).isEmpty();
    }

    // Whether Hot Working is currently reachable at all (a Blast Furnace is adjacent right now).
    // Reads the synced DataSlot rather than checking level access directly - see the field comment.
    private boolean hotWorkingAvailable() {
        return this.hotWorkingAvailable.get() != 0;
    }

    // Called automatically every server tick (ServerPlayer#doTick -> containerMenu.broadcastChanges())
    // for whichever player has this menu open - the same "live, no stored link" check the mod already
    // uses elsewhere (RealStoneAge#hasAdjacentBlastFurnace re-checked on every anvil right-click)
    // applied here on a per-tick cadence instead, with the result pushed to the client via the data
    // slot. If availability actually changed, the current result also needs recomputing (a Mold that
    // just went inert, or a furnace that just appeared, can flip which recipe currently matches).
    @Override
    public void broadcastChanges() {
        boolean available = this.access.evaluate((level, pos) -> RealStoneAge.hasAdjacentBlastFurnace(level, pos), this.hotWorkingAvailable());
        if (available != this.hotWorkingAvailable()) {
            this.hotWorkingAvailable.set(available ? 1 : 0);
            this.access.execute((level, pos) -> {
                if (level instanceof ServerLevel serverLevel) {
                    recalculateResult(serverLevel);
                }
            });
        }
        super.broadcastChanges();
    }

    // What the Tool slot's current contents select: a Hammer (tag-based, so both Rock and the
    // durable Iron Hammer qualify) selects Cold Working unconditionally; a Mold selects Hot Working
    // only while a Blast Furnace is actually adjacent right now (if it's been pulled away, the Mold
    // just goes inert for crafting purposes - see ForgeToolSlot#mayPlace, which is what stops a Mold
    // from getting stuck there on a furnace-less Forge in the first place). Empty tool slot, or any
    // other item, means nothing can be crafted.
    private ForgeCraftingRecipe.@Nullable WorkingType currentWorkingType() {
        ItemStack tool = this.toolSlotContainer.getItem(0);
        if (tool.is(RealStoneAge.HAMMERS_TAG)) {
            return ForgeCraftingRecipe.WorkingType.COLD;
        }
        if (tool.is(RealStoneAge.MOLD.get()) && hotWorkingAvailable()) {
            return ForgeCraftingRecipe.WorkingType.HOT;
        }
        return null;
    }

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((level, pos) -> {
            if (level instanceof ServerLevel serverLevel) {
                recalculateResult(serverLevel);
            }
        });
    }

    // Matches purely against the grid's contents - deliberately NOT filtered by the Tool slot's
    // current Working mode, so the preview shows up regardless of Tool/Fuel state or the order they
    // were filled in (see the currentRecipe field comment). Whether the match can actually be taken
    // is decided separately, at pickup time, by canCraftNow().
    private void recalculateResult(ServerLevel level) {
        var input = this.craftSlots.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer) this.player;
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<ForgeCraftingRecipe>> maybeRecipe =
                level.getServer().getRecipeManager().recipeMap().byType(RealStoneAge.FORGE_CRAFTING_TYPE).stream()
                        .filter(holder -> holder.value().matches(input, level))
                        .findFirst();
        this.currentRecipe = maybeRecipe.orElse(null);
        this.requiredWorkingTypeOrdinal.set(this.currentRecipe == null ? 0 : this.currentRecipe.value().workingType().ordinal() + 1);
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

    // Whether the currently-previewed result can actually be taken right now: the matched recipe's
    // Working type has to match what the Tool slot currently selects (right tool for the job), and
    // Hot Working additionally needs fuel present. Checked fresh every call - not cached - so it
    // reacts instantly to Tool/Fuel changes without needing recalculateResult to re-run (which stays
    // purely grid-driven, see above). Reads the synced requiredWorkingTypeOrdinal rather than
    // currentRecipe directly (that field only exists server-side) so this also works correctly when
    // called from client-side rendering code (see ForgeScreen) - currentWorkingType() and hasFuel()
    // are already both client-safe, reading straight off the normally-synced Tool/Fuel slot contents.
    boolean canCraftNow() {
        int ordinal = this.requiredWorkingTypeOrdinal.get();
        if (ordinal == 0) {
            return false;
        }
        ForgeCraftingRecipe.WorkingType required = ForgeCraftingRecipe.WorkingType.values()[ordinal - 1];
        return required == currentWorkingType() && (required != ForgeCraftingRecipe.WorkingType.HOT || hasFuel());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.craftSlots);
            this.clearContainer(player, this.toolSlotContainer);
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
                if (!this.canCraftNow()) {
                    return ItemStack.EMPTY;
                }
                stack.getItem().onCraftedBy(stack, player);
                if (!this.moveItemStackTo(stack, INV_SLOT_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, clicked);
            } else if (slotIndex >= INV_SLOT_START) {
                if (!this.moveItemStackTo(stack, CRAFT_SLOT_START, TOOL_SLOT, false)
                        && !this.moveItemStackTo(stack, TOOL_SLOT, FUEL_SLOT, false)
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
    // (no bucket-style leftovers), so this just shrinks each non-empty grid slot, the fuel slot (if
    // Hot Working - harmless no-op otherwise, since the fuel slot can't hold anything during Cold
    // Working), and the tool slot by 1 instead of reproducing that machinery.
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
            return this.hasItem() && ForgeMenu.this.canCraftNow();
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

            ItemStack tool = ForgeMenu.this.toolSlotContainer.getItem(0);
            if (!tool.isEmpty()) {
                if (tool.isDamageableItem()) {
                    // Iron Hammer: real per-craft durability loss, breaking (and vanishing from the
                    // slot) like any vanilla tool once it hits 0 - unlike a Rock or Mold, which are
                    // plain non-damageable items and get fully consumed below after exactly 1 craft.
                    ForgeMenu.this.access.execute((level, pos) -> {
                        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                            tool.hurtAndBreak(1, serverLevel, serverPlayer,
                                    item -> ForgeMenu.this.toolSlotContainer.setItem(0, ItemStack.EMPTY));
                        }
                    });
                } else {
                    ForgeMenu.this.toolSlotContainer.removeItem(0, 1);
                }
            }

            ForgeMenu.this.onCraftTaken();
        }
    }

    // Accepts a Hammer (Rock or Iron Hammer, via the realstoneage:hammers tag) unconditionally, or a
    // Mold only while Hot Working is actually reachable right now - this is what stops a Mold from
    // being placeable at all on a furnace-less Forge ("Molds can't be put in").
    private class ForgeToolSlot extends Slot {
        ForgeToolSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(RealStoneAge.HAMMERS_TAG)
                    || (stack.is(RealStoneAge.MOLD.get()) && ForgeMenu.this.hotWorkingAvailable());
        }
    }

    // Only accepts coal while Hot Working is actually selected (a Mold in the Tool slot, with an
    // adjacent furnace) - nothing can ever be inserted there outside Hot Working. The coal ghost
    // icon itself is always shown regardless (no isActive override here) - only placement is gated.
    private class ForgeFuelSlot extends Slot {
        ForgeFuelSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.COALS) && ForgeMenu.this.currentWorkingType() == ForgeCraftingRecipe.WorkingType.HOT;
        }
    }
}
