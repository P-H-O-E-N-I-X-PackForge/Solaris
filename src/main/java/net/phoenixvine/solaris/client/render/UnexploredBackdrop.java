package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Paints what shows behind the map wherever it is unexplored. The map's unexplored pixels are transparent
 * under {@link UnexploredStyle#BACKDROP}, so this is what the player sees there.
 */
@FunctionalInterface
public interface UnexploredBackdrop {

    /**
     * Called every frame inside the map frame's scissor, before the tiles are drawn.
     *
     * @param centerWorldX world X at the middle of the map view, so the backdrop can move with panning
     * @param centerWorldZ world Z at the middle of the map view
     */
    void render(GuiGraphics g, int left, int top, int right, int bottom, double centerWorldX, double centerWorldZ,
                float partialTick);
}
