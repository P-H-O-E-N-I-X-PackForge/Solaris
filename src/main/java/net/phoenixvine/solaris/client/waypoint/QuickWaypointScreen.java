package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.solaris.client.SolarisMapScreen;
import net.phoenixvine.solaris.client.SolarisText;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.util.SolarisLang;

import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_DIM;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_PANEL;

@OnlyIn(Dist.CLIENT)
public class QuickWaypointScreen extends Screen {

    private static final int BOX_W = 216;
    private static final int BOX_H = 250;
    private static final int SCREEN_MARGIN = 12;

    private static final String[] SWATCHES = {
            "FFFFFFFF", "FFFF5555", "FFFFAA00", "FFFFFF55", "FF55FF55", "FF55FFFF", "FF5599FF", "FFFF55FF"
    };

    private float uiScale = 1f;
    private int vw;
    private int vh;

    private final Screen parent;
    private final ResourceLocation dimension;

    private final int initialX;
    private final int initialY;
    private final int initialZ;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox colorBox;
    private EditBox labelColorBox;
    private String icon = WaypointIcon.DOT.name();
    private boolean visible = true;

    public QuickWaypointScreen(Screen parent, ResourceLocation dimension, int wx, int wy, int wz) {
        super(SolarisLang.tr("solaris.quick_waypoint_screen.new_waypoint"));
        this.parent = parent;
        this.dimension = dimension;
        this.initialX = wx;
        this.initialY = wy;
        this.initialZ = wz;
    }

    private int boxX() {
        return (vw - BOX_W) / 2;
    }

    private int boxY() {
        return (vh - BOX_H) / 2;
    }

    @Override
    protected void init() {
        if (parent != null) {
            Minecraft mc = Minecraft.getInstance();
            parent.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        int needW = BOX_W + 16 + 2 * SCREEN_MARGIN;
        int needH = BOX_H + 16 + 2 * SCREEN_MARGIN;
        uiScale = (width < needW || height < needH) ?
                Math.min(width / (float) needW, height / (float) needH) : 1f;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        int x = boxX();
        int y = boxY();

        nameBox = new EditBox(font, x + 10, y + 32, BOX_W - 20, 16,
                SolarisLang.tr("solaris.solaris_preset_save_screen.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue("Waypoint " + (WaypointManager.getAll().size() + 1));
        addWidget(nameBox);
        setInitialFocus(nameBox);

        int coordW = (BOX_W - 20 - 8) / 3;
        xBox = new EditBox(font, x + 10, y + 64, coordW, 16, Component.literal("X"));
        xBox.setMaxLength(8);
        xBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        xBox.setValue(String.valueOf(initialX));
        addWidget(xBox);

        yBox = new EditBox(font, x + 10 + coordW + 4, y + 64, coordW, 16, Component.literal("Y"));
        yBox.setMaxLength(8);
        yBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        yBox.setValue(String.valueOf(initialY));
        addWidget(yBox);

        zBox = new EditBox(font, x + 10 + 2 * (coordW + 4), y + 64, coordW, 16, Component.literal("Z"));
        zBox.setMaxLength(8);
        zBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        zBox.setValue(String.valueOf(initialZ));
        addWidget(zBox);

        colorBox = new EditBox(font, x + 10, y + 96, BOX_W - 20, 16,
                SolarisLang.tr("solaris.plan_shape_list_screen.color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(SWATCHES[0]);
        addWidget(colorBox);

        labelColorBox = new EditBox(font, x + 10, y + 148, BOX_W - 20, 16,
                SolarisLang.tr("solaris.quick_waypoint_screen.label_color"));
        labelColorBox.setMaxLength(8);
        labelColorBox.setHint(SolarisLang.tr("solaris.quick_waypoint_screen.same_as_theme_text"));
        addWidget(labelColorBox);

        addRenderableWidget(Button.builder(
                SolarisLang.tr("solaris.quick_waypoint_screen.icon", WaypointIconManager.label(icon)),
                b -> Minecraft.getInstance().setScreen(new WaypointIconPickerScreen(this, icon, colorArgb(), id -> {
                    icon = id;
                }))).bounds(x + 10, y + 172, BOX_W - 20, 18).build());

        addRenderableWidget(
                Button.builder(Component.literal(visible ? SolarisText.str("solaris.plan_shape_list.visible_on") :
                        SolarisText.str("solaris.plan_shape_list.visible_off")), b -> {
                            visible = !visible;
                            b.setMessage(
                                    Component.literal(visible ? SolarisText.str("solaris.plan_shape_list.visible_on") :
                                            SolarisText.str("solaris.plan_shape_list.visible_off")));
                        }).bounds(x + 10, y + 194, BOX_W - 20, 18).build());

        addRenderableWidget(Button.builder(SolarisLang.tr("solaris.solaris_preset_save_screen.save"), b -> save())
                .bounds(x + 10, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
        addRenderableWidget(Button.builder(SolarisLang.tr("solaris.quick_goto_screen.cancel"), b -> onClose())
                .bounds(x + 14 + (BOX_W - 24) / 2, y + BOX_H - 22, (BOX_W - 24) / 2, 18).build());
    }

    private int colorArgb() {
        try {
            String clean = colorBox.getValue().trim().toUpperCase(java.util.Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);
            return 0xFF000000 | ((int) Long.parseLong(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private int swatchY() {
        return boxY() + 116;
    }

    private int coord(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        if (!WaypointManager.canPlace(dimension)) {
            Minecraft.getInstance().player.displayClientMessage(
                    SolarisLang.tr("solaris.solaris_client_tick_handler.waypoints_aren_t_available_here"), true);
            onClose();
            return;
        }
        if (!WaypointManager.tryBeginPlacement()) return;
        String name = nameBox.getValue().isBlank() ? "Waypoint" : nameBox.getValue();
        String color = colorBox.getValue().isBlank() ? "FFFFFFFF" : colorBox.getValue();
        int wx = coord(xBox, initialX);
        int wy = coord(yBox, initialY);
        int wz = coord(zBox, initialZ);
        Waypoint w = new Waypoint(name, dimension, wx, wy, wz, color);
        w.icon = icon;
        w.visible = visible;
        w.labelColor = labelColorBox.getValue().trim();
        WaypointManager.add(w);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float pt) {
        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        if (parent instanceof SolarisMapScreen mapScreen) {
            mapScreen.renderMapBackground(g);
            g.fill(0, 0, width, height, 0xE0101014);
        } else {
            renderBackground(g);
        }

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int x = boxX();
        int y = boxY();
        g.fill(x, y, x + BOX_W, y + BOX_H, C_PANEL);
        VanillaPanel.draw(g, x - 8, y - 8, BOX_W + 16, BOX_H + 16, C_PANEL);

        g.drawCenteredString(font, SolarisText.str("solaris.quick_waypoint_screen.new_waypoint"), x + BOX_W / 2, y + 6,
                C_ACCENT);
        g.drawString(font, SolarisText.str("solaris.solaris_preset_save_screen.name"), x + 10, y + 22, C_DIM, false);
        g.drawString(font, "X / Y / Z  (" + dimension.getPath() + ")", x + 10, y + 54, C_DIM, false);
        g.drawString(font, SolarisText.str("solaris.plan_shape_list.color_hex"), x + 10, y + 86, C_DIM, false);
        g.drawString(font, SolarisText.str("solaris.quick_waypoint.label_color_hex"), x + 10, y + 138, C_DIM, false);

        List<String> swatches = List.of(SWATCHES);
        int sy = swatchY();
        for (int i = 0; i < swatches.size(); i++) {
            int sx = x + 10 + i * 20;
            int argb = 0xFF000000 | (Integer.parseUnsignedInt(swatches.get(i).substring(2), 16));
            boolean hov = mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
            if (hov) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, C_ACCENT);
            g.fill(sx, sy, sx + 16, sy + 16, argb);
        }

        if (nameBox != null) nameBox.render(g, mx, my, pt);
        if (xBox != null) xBox.render(g, mx, my, pt);
        if (yBox != null) yBox.render(g, mx, my, pt);
        if (zBox != null) zBox.render(g, mx, my, pt);
        if (colorBox != null) colorBox.render(g, mx, my, pt);
        if (labelColorBox != null) labelColorBox.render(g, mx, my, pt);

        super.render(g, mx, my, pt);

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int button) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (button == 0) {
            int x = boxX();
            int sy = swatchY();
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = x + 10 + i * 20;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    colorBox.setValue(SWATCHES[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double rawMx, double rawMy, int btn, double dragX, double dragY) {
        return super.mouseDragged(rawMx / uiScale, rawMy / uiScale, btn, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double rawMx, double rawMy, int btn) {
        return super.mouseReleased(rawMx / uiScale, rawMy / uiScale, btn);
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
