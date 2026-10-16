package net.phoenixvine.solaris.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.config.SolarisPresets;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

@OnlyIn(Dist.CLIENT)
public class SolarisPresetLoadScreen extends Screen {

    private static final int BOX_W = 220;
    private static final int ROW_H = 20;
    private static final int MAX_VISIBLE = 6;

    private final Screen parent;
    private final Runnable onLoaded;
    private int boxH;
    private int scrollOffset = 0;

    public SolarisPresetLoadScreen(Screen parent, Runnable onLoaded) {
        super(Component.literal("Load Preset"));
        this.parent = parent;
        this.onLoaded = onLoaded;
    }

    private int boxX() {
        return (width - BOX_W) / 2;
    }

    private int boxY() {
        return (height - boxH) / 2;
    }

    @Override
    protected void init() {
        clearWidgets();
        List<String> names = SolarisPresets.names();
        int rows = Math.max(1, Math.min(MAX_VISIBLE, names.size()));
        scrollOffset = Math.max(0, Math.min(scrollOffset, names.size() - rows));
        boxH = 24 + rows * ROW_H + 26;

        int x = boxX();
        int y = boxY() + 24;
        int contentW = BOX_W - 20;
        int deleteW = 34;
        int gap = 4;

        for (int i = scrollOffset; i < Math.min(names.size(), scrollOffset + rows); i++) {
            String name = names.get(i);
            boolean deletable = SolarisPresets.isDeletable(name);
            int loadW = deletable ? contentW - deleteW - gap : contentW;

            addRenderableWidget(Button.builder(Component.literal(name), b -> {
                SolarisPresets.load(name);
                onLoaded.run();
                onClose();
            }).bounds(x + 10, y, loadW, 18).build());
            if (deletable) {
                addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                    SolarisPresets.delete(name);
                    init();
                }).bounds(x + 10 + loadW + gap, y, deleteW, 18).build());
            }
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + 10, boxY() + boxH - 22, contentW, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, SolarisPresets.names().size() - MAX_VISIBLE);
        if (max == 0) return false;
        int next = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(delta)));
        if (next != scrollOffset) {
            scrollOffset = next;
            init();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xC0101014);

        int x = boxX();
        int y = boxY();
        g.fill(x, y, x + BOX_W, y + boxH, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, boxH + 16, C_PANEL);
        g.drawCenteredString(font, title, x + BOX_W / 2, y + 6, C_ACCENT);

        int total = SolarisPresets.names().size();
        if (total > MAX_VISIBLE) {
            int trackTop = y + 24;
            int trackH = MAX_VISIBLE * ROW_H - 2;
            int trackX = x + BOX_W - 7;
            g.fill(trackX, trackTop, trackX + 3, trackTop + trackH, 0x40FFFFFF);
            int thumbH = Math.max(10, trackH * MAX_VISIBLE / total);
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / (total - MAX_VISIBLE);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, C_ACCENT);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
