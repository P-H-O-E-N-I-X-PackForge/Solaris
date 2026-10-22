package net.phoenixvine.solaris.client.page;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.client.render.MapPage;
import net.phoenixvine.solaris.client.render.MapPages;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_HEADER;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_TEXT;

public final class PageTabs {

    private static final int HEIGHT = 14;
    private static final int GAP = 3;
    private static final int X = 8;
    private static final int Y = 4;

    private record Chip(@Nullable MapPage page, String label, int x, int w) {}

    private PageTabs() {}

    private static List<Chip> layout(Font font) {
        List<Chip> chips = new ArrayList<>();
        int x = X;
        String world = "World";
        chips.add(new Chip(null, world, x, font.width(world) + 12));
        x += chips.get(0).w() + GAP;
        for (MapPage page : MapPages.available()) {
            String label = page.title().getString();
            chips.add(new Chip(page, label, x, font.width(label) + 12));
            x += font.width(label) + 12 + GAP;
        }
        return chips;
    }

    public static boolean any() {
        return !MapPages.available().isEmpty();
    }

    public static void render(GuiGraphics g, Font font, int mx, int my, @Nullable MapPage active) {
        if (!any()) return;
        for (Chip chip : layout(font)) {
            boolean selected = chip.page() == active;
            boolean hover = mx >= chip.x() && mx < chip.x() + chip.w() && my >= Y && my < Y + HEIGHT;
            int fill = selected ? (C_ACCENT & 0x00FFFFFF) | 0xCC000000 :
                    hover ? C_HEADER : (C_HEADER & 0x00FFFFFF) | 0xB0000000;
            g.fill(chip.x(), Y, chip.x() + chip.w(), Y + HEIGHT, fill);
            g.renderOutline(chip.x(), Y, chip.w(), HEIGHT, selected ? C_ACCENT : C_BORDER);
            g.drawString(font, chip.label(), chip.x() + 6, Y + 3, selected ? 0xFFFFFFFF : C_TEXT, false);
        }
    }

    public static boolean click(double mx, double my, Font font, @Nullable MapPage active, @Nullable Screen parent) {
        if (!any() || my < Y || my >= Y + HEIGHT) return false;
        for (Chip chip : layout(font)) {
            if (mx < chip.x() || mx >= chip.x() + chip.w()) continue;
            if (chip.page() == active) return true;
            if (chip.page() == null) {
                if (!SolarisAPI.openMap(parent)) Minecraft.getInstance().setScreen(parent);
            } else {
                Minecraft.getInstance().setScreen(new SolarisPageScreen(chip.page(), parent));
            }
            return true;
        }
        return false;
    }
}
