package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;

@FunctionalInterface
public interface UnexploredBackdrop {

    void render(GuiGraphics g, int left, int top, int right, int bottom, double centerWorldX, double centerWorldZ,
                float partialTick);
}
