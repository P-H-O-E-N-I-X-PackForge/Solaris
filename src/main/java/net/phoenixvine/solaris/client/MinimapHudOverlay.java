package net.phoenixvine.solaris.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.perf.SolarisProfiler;
import net.phoenixvine.solaris.client.ping.PingMarker;
import net.phoenixvine.solaris.client.ping.PingTracker;
import net.phoenixvine.solaris.client.player.RemotePlayerTracker;
import net.phoenixvine.solaris.client.render.LineRenderer;
import net.phoenixvine.solaris.client.render.MinimapShape;
import net.phoenixvine.solaris.client.render.ModernPanel;
import net.phoenixvine.solaris.client.render.PlayerArrow;
import net.phoenixvine.solaris.client.render.SmoothShapes;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.TextureAddressing;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_ACCENT;
import static net.phoenixvine.solaris.client.SolarisThemeUtils.C_BORDER;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public class MinimapHudOverlay {

    private static final int MARGIN = 6;

    private static SolarisTexture texture;

    private static SolarisTexture texture() {
        int radius = Math.min(SolarisConfig.MINIMAP_RADIUS_CHUNKS.get(), SolarisConfig.MAX_MINIMAP_RANGE_CHUNKS.get());

        if (texture == null || texture.getRadiusChunks() != radius) {
            if (texture != null) texture.close();
            texture = new SolarisTexture("minimap", radius);
        }
        return texture;
    }

    private static boolean isVisible(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return false;
        return SolarisAPI.getFeatureState(SolarisAPI.FEATURE_MINIMAP, mc.level.dimension().location())
                .atLeast(SolarisFeatureState.VISIBLE);
    }

    private static int[] bounds(Minecraft mc) {
        int screenSize = SolarisConfig.MINIMAP_SIZE.get();
        int screenW = mc.getWindow().getGuiScaledWidth();
        return new int[] { screenW - screenSize - MARGIN, MARGIN, screenSize };
    }

    private static final double ZOOM_SCROLL_STEP = 0.5;

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isVisible(mc)) return;
        if (!SolarisKeybinds.CYCLE_MINIMAP_STYLE.isDown()) return;

        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        int[] b = bounds(mc);
        if (mx < b[0] || mx > b[0] + b[2] || my < b[1] || my > b[1] + b[2]) return;

        double zoom = Mth.clamp(SolarisConfig.MINIMAP_ZOOM.get() + event.getScrollDeltaY() * ZOOM_SCROLL_STEP, 1.0,
                8.0);
        SolarisConfig.MINIMAP_ZOOM.set(zoom);
        SolarisConfig.MINIMAP_ZOOM.save();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        Minecraft mc = Minecraft.getInstance();
        if (!isVisible(mc)) return;

        SolarisProfiler.time("minimapRender", () -> onRenderHudImpl(event, mc));
    }

    private static void onRenderHudImpl(RenderGuiLayerEvent.Post event, Minecraft mc) {
        SolarisTexture tex = texture();
        int screenSize = SolarisConfig.MINIMAP_SIZE.get();

        int viewPixels = Math.max(16, (int) (tex.getRadiusChunks() * 16 / SolarisConfig.MINIMAP_ZOOM.get()));

        BlockPos blockPos = mc.player.blockPosition();
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        ChunkKey center = ChunkKey.of(mc.level, new ChunkPos(chunkX, chunkZ));
        tex.maybeRebuild(center);

        double fracX = mc.player.getX() - (chunkX << 4);
        double fracZ = mc.player.getZ() - (chunkZ << 4);
        int radiusPixels = tex.getRadiusChunks() * 16;
        double playerPixelX = radiusPixels + fracX;
        double playerPixelZ = radiusPixels + fracZ;

        float u = (float) (playerPixelX - viewPixels / 2.0);
        float v = (float) (playerPixelZ - viewPixels / 2.0);

        int spanChunks = tex.getRadiusChunks() * 2 + 1;
        int originX = TextureAddressing.properMod(chunkX - tex.getRadiusChunks(), spanChunks) * 16;
        int originZ = TextureAddressing.properMod(chunkZ - tex.getRadiusChunks(), spanChunks) * 16;

        int[] b = bounds(mc);
        int x = b[0];
        int y = b[1];

        GuiGraphics g = event.getGuiGraphics();
        MinimapShape shape = SolarisConfig.MINIMAP_SHAPE.get();
        int cx = x + screenSize / 2;
        int cy = y + screenSize / 2;

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        Waypoint hoveredWaypoint = null;

        boolean showBorder = SolarisConfig.MINIMAP_SHOW_BORDER.get();
        if (shape == MinimapShape.SQUARE && showBorder) {
            ModernPanel.draw(g, x - 5, y - 5, screenSize + 10, screenSize + 10, C_BORDER);
        }

        boolean rotate = SolarisConfig.MINIMAP_ROTATE.get();
        float contentAngle = rotate ? 180f - mc.player.getYRot() : 0f;

        g.enableScissor(x, y, x + screenSize, y + screenSize);
        g.pose().pushPose();
        if (rotate) {
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(contentAngle));
            g.pose().translate(-cx, -cy, 0);
        }

        float scale = screenSize / (float) viewPixels;
        float wrappedU = TextureAddressing.properMod(originX + u, tex.getSizePixels());
        float wrappedV = TextureAddressing.properMod(originZ + v, tex.getSizePixels());

        if (shape == MinimapShape.SQUARE) {
            if (rotate) {
                float overscan = 1.5f;
                int renderSize = Math.round(screenSize * overscan);
                int renderViewPixels = Math.round(viewPixels * overscan);
                int renderX = x - (renderSize - screenSize) / 2;
                int renderY = y - (renderSize - screenSize) / 2;
                float renderU = (float) (playerPixelX - renderViewPixels / 2.0);
                float renderV = (float) (playerPixelZ - renderViewPixels / 2.0);
                float wrappedRenderU = TextureAddressing.properMod(originX + renderU, tex.getSizePixels());
                float wrappedRenderV = TextureAddressing.properMod(originZ + renderV, tex.getSizePixels());
                g.blit(tex.textureId(), renderX, renderY, renderSize, renderSize, wrappedRenderU, wrappedRenderV,
                        renderViewPixels, renderViewPixels, tex.getSizePixels(), tex.getSizePixels());
            } else {
                g.blit(tex.textureId(), x, y, screenSize, screenSize, wrappedU, wrappedV, viewPixels, viewPixels,
                        tex.getSizePixels(), tex.getSizePixels());
            }
        } else {
            drawClippedTerrain(g, shape, tex, x, y, screenSize, wrappedU, wrappedV, scale);
        }

        int radius = screenSize / 2;
        List<Waypoint> offScreenWaypoints = null;
        if (SolarisConfig.MINIMAP_SHOW_WAYPOINTS.get()) {
            List<Waypoint> waypoints = new ArrayList<>(
                    WaypointManager.getVisibleForDimension(mc.level.dimension().location()));
            waypoints.sort(Comparator.comparingDouble(
                    w -> w.distanceSq(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
            int maxWaypoints = SolarisConfig.MINIMAP_MAX_WAYPOINTS.get();
            if (waypoints.size() > maxWaypoints) waypoints = waypoints.subList(0, maxWaypoints);

            boolean showDirections = SolarisConfig.MINIMAP_SHOW_WAYPOINT_DIRECTIONS.get();
            for (Waypoint w : waypoints) {
                double wPixelX = radiusPixels + (w.x - (chunkX << 4));
                double wPixelZ = radiusPixels + (w.z - (chunkZ << 4));
                if (wPixelX < u || wPixelX > u + viewPixels || wPixelZ < v || wPixelZ > v + viewPixels) {
                    if (showDirections) {
                        if (offScreenWaypoints == null) offScreenWaypoints = new ArrayList<>();
                        offScreenWaypoints.add(w);
                    }
                    continue;
                }
                int wx = x + (int) ((wPixelX - u) * scale);
                int wy = y + (int) ((wPixelZ - v) * scale);

                if (shape != MinimapShape.SQUARE && !containsPoint(shape, wx - x, wy - y, screenSize)) continue;
                g.fill(wx - 3, wy - 3, wx + 3, wy + 3, 0xFF000000);
                g.fill(wx - 2, wy - 2, wx + 2, wy + 2, w.colorArgb());

                int[] finalPos = rotate ? rotateAroundCenter(wx, wy, cx, cy, contentAngle) : new int[] { wx, wy };
                double hdx = finalPos[0] - mouseX;
                double hdy = finalPos[1] - mouseY;
                if (hdx * hdx + hdy * hdy <= HOVER_RADIUS_SQ) hoveredWaypoint = w;
            }
        }

        g.pose().popPose();
        g.disableScissor();

        String hoveredPlayerName = null;
        List<RemotePlayerTracker.MapPlayer> offScreenPlayers = new ArrayList<>();
        for (RemotePlayerTracker.MapPlayer other : RemotePlayerTracker.collect(mc)) {
            double plPixelX = radiusPixels + (other.x() - (chunkX << 4));
            double plPixelZ = radiusPixels + (other.z() - (chunkZ << 4));
            if (plPixelX < u || plPixelX > u + viewPixels || plPixelZ < v || plPixelZ > v + viewPixels) {
                offScreenPlayers.add(other);
                continue;
            }

            int plX = x + (int) ((plPixelX - u) * scale);
            int plY = y + (int) ((plPixelZ - v) * scale);
            int[] plPos = rotate ? rotateAroundCenter(plX, plY, cx, cy, contentAngle) : new int[] { plX, plY };
            int faceR = 4;
            if (plPos[0] < x + faceR || plPos[0] > x + screenSize - faceR || plPos[1] < y + faceR ||
                    plPos[1] > y + screenSize - faceR) {
                continue;
            }
            if (shape != MinimapShape.SQUARE && !containsPoint(shape, plPos[0] - x, plPos[1] - y, screenSize)) {
                continue;
            }

            PlayerArrow.draw(g, plPos[0], plPos[1], faceR, other.yaw() + (rotate ? contentAngle : 0f), 0xFFAAAAAA,
                    other.skin());
            double pdx = plPos[0] - mouseX;
            double pdy = plPos[1] - mouseY;
            if (pdx * pdx + pdy * pdy <= HOVER_RADIUS_SQ) hoveredPlayerName = other.name();
        }

        for (PingTracker.Ping ping : PingTracker.collect(mc)) {
            double pgPixelX = radiusPixels + (ping.x() + 0.5 - (chunkX << 4));
            double pgPixelZ = radiusPixels + (ping.z() + 0.5 - (chunkZ << 4));
            if (pgPixelX < u || pgPixelX > u + viewPixels || pgPixelZ < v || pgPixelZ > v + viewPixels) continue;

            int pgX = x + (int) ((pgPixelX - u) * scale);
            int pgY = y + (int) ((pgPixelZ - v) * scale);
            int[] pgPos = rotate ? rotateAroundCenter(pgX, pgY, cx, cy, contentAngle) : new int[] { pgX, pgY };
            if (pgPos[0] < x + 4 || pgPos[0] > x + screenSize - 4 || pgPos[1] < y + 4 ||
                    pgPos[1] > y + screenSize - 4) {
                continue;
            }
            if (shape != MinimapShape.SQUARE && !containsPoint(shape, pgPos[0] - x, pgPos[1] - y, screenSize)) {
                continue;
            }

            PingMarker.draw(g, pgPos[0], pgPos[1], 4, ping);
            double gdx = pgPos[0] - mouseX;
            double gdy = pgPos[1] - mouseY;
            if (gdx * gdx + gdy * gdy <= HOVER_RADIUS_SQ) hoveredPlayerName = ping.sender() + " (ping)";
        }

        for (RemotePlayerTracker.MapPlayer other : offScreenPlayers) {
            int[] edgePos = edgePosition(other.x(), other.z(), mc, cx, cy, radius, rotate, contentAngle);
            if (edgePos == null) continue;

            double outX = other.x() - mc.player.getX();
            double outZ = other.z() - mc.player.getZ();
            float outward = (float) Math.toDegrees(Math.atan2(-outX, outZ)) + (rotate ? contentAngle : 0f);
            PlayerArrow.draw(g, edgePos[0], edgePos[1], 3, outward, 0xFFAAAAAA, other.skin());

            double edx = edgePos[0] - mouseX;
            double edy = edgePos[1] - mouseY;
            if (edx * edx + edy * edy <= HOVER_RADIUS_SQ) {
                hoveredPlayerName = other.name() + " (" + Math.round(Math.hypot(outX, outZ)) + "m)";
            }
        }

        if (offScreenWaypoints != null) {
            for (Waypoint w : offScreenWaypoints) {
                drawWaypointDirection(g, w, mc, cx, cy, radius, rotate, contentAngle);
                int[] pos = waypointEdgePosition(w, mc, cx, cy, radius, rotate, contentAngle);
                if (pos == null) continue;
                double hdx = pos[0] - mouseX;
                double hdy = pos[1] - mouseY;
                if (hdx * hdx + hdy * hdy <= HOVER_RADIUS_SQ) hoveredWaypoint = w;
            }
        }

        if (showBorder) {
            if (shape == MinimapShape.CIRCLE) {

                SmoothShapes.drawRing(g, cx, cy, radius + 4, ModernPanel.shade(C_BORDER, -ModernPanel.SHADE_AMOUNT));
                SmoothShapes.drawRing(g, cx, cy, radius + 2, ModernPanel.shade(C_BORDER, ModernPanel.SHADE_AMOUNT));
            } else if (shape.isPolygon()) {
                drawPolygonOutline(g, shape, cx, cy, screenSize);
            }
        }

        PlayerArrow.draw(g, cx, cy, 6, rotate ? 180f : mc.player.getYRot(), C_ACCENT,
                mc.player.getSkin().texture());

        drawInfoText(g, mc, x, y, screenSize);

        String hoverLabel = hoveredWaypoint != null ? hoveredWaypoint.name : hoveredPlayerName;
        if (hoverLabel != null) {
            String name = hoverLabel;
            int tw = mc.font.width(name);
            int tx = (int) mouseX + 10;
            int ty = (int) mouseY - 4;
            g.fill(tx - 2, ty - 2, tx + tw + 2, ty + mc.font.lineHeight + 2, 0xCC000000);
            g.drawString(mc.font, name, tx, ty, 0xFFFFFFFF, false);
        }
    }

    private static void drawWaypointDirection(GuiGraphics g, Waypoint w, Minecraft mc, int cx, int cy, int radius,
                                              boolean rotate, float contentAngle) {
        int[] pos = waypointEdgePosition(w, mc, cx, cy, radius, rotate, contentAngle);
        if (pos == null) return;
        g.fill(pos[0] - 3, pos[1] - 3, pos[0] + 3, pos[1] + 3, 0xFF000000);
        g.fill(pos[0] - 2, pos[1] - 2, pos[0] + 2, pos[1] + 2, w.colorArgb());
    }

    private static int[] waypointEdgePosition(Waypoint w, Minecraft mc, int cx, int cy, int radius, boolean rotate,
                                              float contentAngle) {
        return edgePosition(w.x, w.z, mc, cx, cy, radius, rotate, contentAngle);
    }

    private static int[] edgePosition(double targetX, double targetZ, Minecraft mc, int cx, int cy, int radius,
                                      boolean rotate, float contentAngle) {
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        if (dx == 0 && dz == 0) return null;

        double angle;
        if (rotate) {
            double rad = Math.toRadians(contentAngle);
            double rx = dx * Math.cos(rad) - dz * Math.sin(rad);
            double rz = dx * Math.sin(rad) + dz * Math.cos(rad);
            angle = Math.atan2(rz, rx);
        } else {
            angle = Math.atan2(dz, dx);
        }

        int edge = radius - 6;
        int ex = cx + (int) Math.round(Math.cos(angle) * edge);
        int ey = cy + (int) Math.round(Math.sin(angle) * edge);
        return new int[] { ex, ey };
    }

    private static final int HOVER_RADIUS_SQ = 5 * 5;

    private static int[] rotateAroundCenter(int px, int py, int cx, int cy, float angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double dx = px - cx;
        double dy = py - cy;
        double rx = dx * Math.cos(rad) - dy * Math.sin(rad);
        double ry = dx * Math.sin(rad) + dy * Math.cos(rad);
        return new int[] { cx + (int) Math.round(rx), cy + (int) Math.round(ry) };
    }

    private static void drawInfoText(GuiGraphics g, Minecraft mc, int x, int y, int screenSize) {
        boolean showTime = SolarisConfig.MINIMAP_SHOW_TIME.get();
        boolean showCoords = SolarisConfig.MINIMAP_SHOW_COORDS.get();
        boolean showBiome = SolarisConfig.MINIMAP_SHOW_BIOME.get();
        if (!showTime && !showCoords && !showBiome) return;

        boolean withLabels = SolarisConfig.MINIMAP_SHOW_TEXT_LABELS.get();
        int textY = y + screenSize + 7;
        int cx = x + screenSize / 2;
        if (showTime) {
            String time = formatTime(mc.level.getDayTime());
            g.drawCenteredString(mc.font, withLabels ? "Time: " + time : time, cx, textY, C_ACCENT);
            textY += mc.font.lineHeight + 1;
        }
        if (showCoords) {
            BlockPos pos = mc.player.blockPosition();
            String coords = withLabels ? "x: " + pos.getX() + ", y: " + pos.getY() + ", z: " + pos.getZ() :
                    pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            g.drawCenteredString(mc.font, coords, cx, textY, C_ACCENT);
            textY += mc.font.lineHeight + 1;
        }
        if (showBiome) {
            g.drawCenteredString(mc.font, biomeName(mc), cx, textY, C_ACCENT);
        }
    }

    private static String formatTime(long dayTime) {
        long ticks = ((dayTime % 24000) + 24000) % 24000;
        int hour = (int) ((ticks / 1000 + 6) % 24);
        int minute = (int) (ticks % 1000 * 60 / 1000);
        return String.format("%02d:%02d", hour, minute);
    }

    private static String biomeName(Minecraft mc) {
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(key -> Component.translatable(Util.makeDescriptionId("biome", key.location())).getString())
                .orElse("");
    }

    private static void drawClippedTerrain(GuiGraphics g, MinimapShape shape, SolarisTexture tex, int x, int y,
                                           int screenSize, float u, float v, float scale) {
        RenderSystem.setShaderTexture(0, tex.textureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);

        float texW = tex.getSizePixels();
        float texH = tex.getSizePixels();

        for (int i = 0; i < screenSize; i++) {
            float ny = (i + 0.5f) / screenSize;
            float[] span = shape.rowSpan(ny);
            if (span == null) continue;

            float destX = x + (span[0] * screenSize);
            float destW = (span[1] - span[0]) * screenSize;
            if (destW <= 0) continue;

            float destY = y + i;

            float srcX = u + (destX - x) / scale;
            float srcY = v + i / scale;
            float srcW = destW / scale;
            float srcH = 1.0f / scale;

            float u0 = srcX / texW;
            float v0 = srcY / texH;
            float u1 = (srcX + srcW) / texW;
            float v1 = (srcY + srcH) / texH;

            buffer.addVertex(destX, destY + 1, 0).setUv(u0, v1);
            buffer.addVertex(destX + destW, destY + 1, 0).setUv(u1, v1);
            buffer.addVertex(destX + destW, destY, 0).setUv(u1, v0);
            buffer.addVertex(destX, destY, 0).setUv(u0, v0);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static boolean containsPoint(MinimapShape shape, int lx, int ly, int screenSize) {
        return shape.containsPoint(lx, ly, screenSize);
    }

    private static void drawPolygonOutline(GuiGraphics g, MinimapShape shape, int cx, int cy, int outlineSize) {
        float[][] verts = shape.vertices();
        int n = verts.length;
        for (int i = 0; i < n; i++) {
            float[] a = verts[i];
            float[] b = verts[(i + 1) % n];
            int x1 = cx + Math.round((a[0] - 0.5f) * outlineSize);
            int y1 = cy + Math.round((a[1] - 0.5f) * outlineSize);
            int x2 = cx + Math.round((b[0] - 0.5f) * outlineSize);
            int y2 = cy + Math.round((b[1] - 0.5f) * outlineSize);

            float midY = (a[1] + b[1]) / 2f;
            int edgeColor = midY < 0.5f ? ModernPanel.shade(C_BORDER, ModernPanel.SHADE_AMOUNT) :
                    ModernPanel.shade(C_BORDER, -ModernPanel.SHADE_AMOUNT);
            LineRenderer.drawLine(g, x1, y1, x2, y2, 2, edgeColor);
        }
    }
}
