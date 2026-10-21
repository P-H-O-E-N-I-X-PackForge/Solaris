package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Extra drawing on the flat fullscreen map: above the tiles and rail network, below waypoints and the player.
 * Already inside the map frame's scissor.
 */
@FunctionalInterface
public interface MapLayer {

    void render(GuiGraphics g, ResourceLocation dimension, MapProjection projection, float partialTick);
}
