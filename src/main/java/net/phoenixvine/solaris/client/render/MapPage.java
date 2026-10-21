package net.phoenixvine.solaris.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A map page: a whole separate map another mod provides, shown from a tab next to the world map and independent of
 * the dimension the player is in (a planet surface, a station, a dream...). Solaris owns the tab bar, panning and
 * zooming; the page draws its content and lists its markers.
 *
 * <p>Page space is centred on (0, 0): the content covers {@code -width/2 .. width/2} by {@code -height/2 .. height/2}.
 * Register with {@link MapPages#register(MapPage)}. Client side only.
 */
public interface MapPage {

    /** A pin on the page. {@code icon} may be empty; {@code tooltip} lines are shown while hovering it. */
    record Marker(double x, double y, Component label, int color, ItemStack icon, List<Component> tooltip) {

        public Marker(double x, double y, Component label, int color) {
            this(x, y, label, color, ItemStack.EMPTY, List.of());
        }
    }

    ResourceLocation id();

    Component title();

    /** Whether the tab is shown at all right now. */
    default boolean available() {
        return true;
    }

    /** Size of the content in page units. */
    double width();

    double height();

    /**
     * Several things this page can show (for example one per planet). Solaris shows a selector when there is more
     * than one. Return an empty list for a single-view page.
     */
    default List<Component> variants() {
        return List.of();
    }

    default int selectedVariant() {
        return 0;
    }

    default void selectVariant(int index) {}

    /**
     * Draws the content. {@code projection} converts page coordinates to screen pixels; the clip rectangle is already
     * set to the map area ({@code x, y, w, h}).
     */
    void render(GuiGraphics g, int x, int y, int w, int h, MapProjection projection, float partialTick);

    default List<Marker> markers() {
        return List.of();
    }

    /** Draws on top of everything (panels, lists). Called every frame with the mouse position, outside the map clip. */
    default void renderOverlay(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {}

    /** A click anywhere on the page screen, before it is treated as the start of a drag. Return true to consume it. */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /** A key press on the page screen, before the built-in keys (Q / E, Esc). Return true to consume it. */
    default boolean keyPressed(int key, int scanCode, int modifiers) {
        return false;
    }

    /** A line of text under the map, or null. */
    default @Nullable Component footer() {
        return null;
    }
}
