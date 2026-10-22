package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface MapPage {

    record Marker(double x, double y, Component label, int color, ItemStack icon, List<Component> tooltip) {

        public Marker(double x, double y, Component label, int color) {
            this(x, y, label, color, ItemStack.EMPTY, List.of());
        }
    }

    ResourceLocation id();

    Component title();

    default boolean available() {
        return true;
    }

    double width();

    double height();

    default List<Component> variants() {
        return List.of();
    }

    default int selectedVariant() {
        return 0;
    }

    default void selectVariant(int index) {}

    void render(GuiGraphics g, int x, int y, int w, int h, MapProjection projection, float partialTick);

    default List<Marker> markers() {
        return List.of();
    }

    default void renderOverlay(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {}

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean keyPressed(int key, int scanCode, int modifiers) {
        return false;
    }

    default @Nullable Component footer() {
        return null;
    }
}
