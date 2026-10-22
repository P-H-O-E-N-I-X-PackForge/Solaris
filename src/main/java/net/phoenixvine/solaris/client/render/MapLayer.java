package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface MapLayer {

    void render(GuiGraphics g, ResourceLocation dimension, MapProjection projection, float partialTick);
}
