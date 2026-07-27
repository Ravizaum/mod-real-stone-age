package com.realstoneage.realstoneagemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

// The Forge screen (opened from either a vanilla Anvil or a Basic Anvil - see
// RealStoneAge#onRightClickAnvil). No dedicated GUI art exists for this, so the background is
// composited from vanilla's crafting table panel for the grid/result/inventory area, plus two more
// crops of that same texture's plain slot squares (one of the crafting-grid cells) as the Tool and
// Fuel slots' backdrops. The empty-slot ghost icons (see ForgeMenu's ForgeToolSlot/ForgeFuelSlot
// #setBackground) are drawn automatically by AbstractContainerScreen's own generic ghost-icon
// mechanism - same one vanilla uses for the enchanting table's lapis slot or the brewing stand's
// fuel slot - so no custom rendering code is needed for it here. Both icons are always shown
// regardless of the current Working mode; only actual item placement is gated (see
// ForgeFuelSlot#mayPlace).
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final Identifier CRAFTING_TABLE_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    // Top-left corner (including the 1px recessed border) of one of the crafting table's own
    // 3x3-grid slot squares, reused here purely as a source crop for a plain slot backdrop.
    private static final int PLAIN_SLOT_U = 29;
    private static final int PLAIN_SLOT_V = 16;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = this.leftPos;
        int yo = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE_LOCATION, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        blitPlainSlot(graphics, xo, yo, this.menu.getSlot(ForgeMenu.TOOL_SLOT));
        blitPlainSlot(graphics, xo, yo, this.menu.getSlot(ForgeMenu.FUEL_SLOT));
    }

    private void blitPlainSlot(GuiGraphicsExtractor graphics, int xo, int yo, Slot slot) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE_LOCATION, xo + slot.x - 1, yo + slot.y - 1, PLAIN_SLOT_U, PLAIN_SLOT_V, 18, 18, 256, 256);
    }

    // Fades the result item (drawn semi-transparent over the panel's own gray, rather than a plain
    // full-opacity render) whenever it's only a preview and can't actually be taken yet - see
    // ForgeMenu#canCraftNow, which is fed by recalculateResult's grid-only match (working-type/fuel
    // are deliberately not part of that match - see there) so the item can show up before the right
    // Tool/Fuel is in place. The real item render pipeline has no exposed alpha control for GUI item
    // icons, so this fakes translucency the same way vanilla's own quick-craft highlight overlay
    // does a few lines up in AbstractContainerScreen - a flat tint blended on top, here matching the
    // panel's own background gray (0xC6C6C6) instead of vanilla's yellow, so it reads as "faded into
    // the slot" instead of "highlighted".
    private static final int FADE_OVERLAY_COLOR = 0x80C6C6C6;

    @Override
    protected void renderSlotContents(GuiGraphicsExtractor graphics, ItemStack itemstack, Slot slot, @Nullable String countString) {
        super.renderSlotContents(graphics, itemstack, slot, countString);
        if (slot.index == ForgeMenu.RESULT_SLOT && !itemstack.isEmpty() && !this.menu.canCraftNow()) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, FADE_OVERLAY_COLOR);
        }
    }
}
