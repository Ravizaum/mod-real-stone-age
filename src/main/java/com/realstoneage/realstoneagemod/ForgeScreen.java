package com.realstoneage.realstoneagemod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

// The Forge screen (opened from either a vanilla Anvil or a Basic Anvil next to a Blast Furnace -
// see RealStoneAge#onRightClickAnvil). No dedicated GUI art exists for this, so the background is
// composited from vanilla's crafting table panel for the grid/result/inventory area, plus a crop of
// that same texture's plain slot squares (one of the crafting-grid cells) as the fuel slot's
// backdrop. The empty-slot coal outline (see ForgeMenu's ForgeFuelSlot#setBackground) is drawn
// automatically by AbstractContainerScreen's own generic ghost-icon mechanism - same one vanilla
// uses for the enchanting table's lapis slot or the brewing stand's fuel slot - so no custom
// rendering code is needed for it here.
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

        var fuelSlot = this.menu.getSlot(ForgeMenu.FUEL_SLOT);
        int fuelX = xo + fuelSlot.x - 1;
        int fuelY = yo + fuelSlot.y - 1;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE_LOCATION, fuelX, fuelY, PLAIN_SLOT_U, PLAIN_SLOT_V, 18, 18, 256, 256);
    }
}
