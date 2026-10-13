package net.phoenixvine.solaris.client.plan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.config.SolarisConfig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

@EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, value = Dist.CLIENT)
public class PlanShapeRenderer {

    private static final int CIRCLE_SEGMENTS = 32;

    private static final int STRUT_INTERVAL = 4;
    private static final float ALPHA = 0.8f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!SolarisConfig.SHOW_PLAN_SHAPES.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<PlanShape> shapes = PlanShapeManager.getVisibleForDimension(mc.level.dimension().location());
        if (shapes.isEmpty()) return;

        double rangeSq = (double) SolarisConfig.PLAN_SHAPE_RANGE.get() * SolarisConfig.PLAN_SHAPE_RANGE.get();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (PlanShape shape : shapes) {
            if (shape.points.isEmpty()) continue;

            double cx = centroidX(shape);
            double cz = centroidZ(shape);
            double dx = cx - mc.player.getX();
            double dz = cz - mc.player.getZ();
            if (dx * dx + dz * dz > rangeSq) continue;

            int argb = shape.colorArgb();
            float r = (argb >> 16 & 255) / 255f;
            float g = (argb >> 8 & 255) / 255f;
            float b = (argb & 255) / 255f;

            switch (shape.type) {
                case RECTANGLE -> renderRectangle(poseStack, lines, shape, r, g, b);
                case CIRCLE -> renderCircle(poseStack, lines, shape, r, g, b);
                case LINE -> renderLine(poseStack, lines, shape, r, g, b);
            }
        }
        bufferSource.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    private static void renderRectangle(PoseStack poseStack, VertexConsumer lines, PlanShape shape, float r, float g,
                                        float b) {
        int[] p1 = shape.points.get(0);
        int[] p2 = shape.points.get(1);
        int minX = Math.min(p1[0], p2[0]);
        int maxX = Math.max(p1[0], p2[0]) + 1;
        int minZ = Math.min(p1[1], p2[1]);
        int maxZ = Math.max(p1[1], p2[1]) + 1;
        LevelRenderer.renderLineBox(poseStack, lines, minX, shape.baseY, minZ, maxX, shape.baseY + shape.height, maxZ,
                r, g, b, ALPHA);
    }

    private static void renderCircle(PoseStack poseStack, VertexConsumer lines, PlanShape shape, float r, float g,
                                     float b) {
        int[] center = shape.points.get(0);
        double cx = center[0] + 0.5;
        double cz = center[1] + 0.5;
        double radius = shape.radius;
        double baseY = shape.baseY;
        double topY = shape.baseY + shape.height;

        PoseStack.Pose pose = poseStack.last();

        double[] xs = new double[CIRCLE_SEGMENTS];
        double[] zs = new double[CIRCLE_SEGMENTS];
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / CIRCLE_SEGMENTS;
            xs[i] = cx + Math.cos(angle) * radius;
            zs[i] = cz + Math.sin(angle) * radius;
        }

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            int j = (i + 1) % CIRCLE_SEGMENTS;
            line(lines, pose, xs[i], baseY, zs[i], xs[j], baseY, zs[j], r, g, b);
            line(lines, pose, xs[i], topY, zs[i], xs[j], topY, zs[j], r, g, b);
            if (i % STRUT_INTERVAL == 0) {
                line(lines, pose, xs[i], baseY, zs[i], xs[i], topY, zs[i], r, g, b);
            }
        }
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer lines, PlanShape shape, float r, float g,
                                   float b) {
        PoseStack.Pose pose = poseStack.last();
        double baseY = shape.baseY;
        double topY = shape.baseY + shape.height;
        List<int[]> pts = shape.points;

        for (int i = 0; i < pts.size() - 1; i++) {
            double x1 = pts.get(i)[0] + 0.5;
            double z1 = pts.get(i)[1] + 0.5;
            double x2 = pts.get(i + 1)[0] + 0.5;
            double z2 = pts.get(i + 1)[1] + 0.5;
            line(lines, pose, x1, baseY, z1, x2, baseY, z2, r, g, b);
            line(lines, pose, x1, topY, z1, x2, topY, z2, r, g, b);
        }
        for (int[] pt : pts) {
            double x = pt[0] + 0.5;
            double z = pt[1] + 0.5;
            line(lines, pose, x, baseY, z, x, topY, z, r, g, b);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, double x1, double y1,
                             double z1, double x2, double y2, double z2, float r, float g, float b) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6f) return;
        nx /= len;
        ny /= len;
        nz /= len;

        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, ALPHA)
                .setNormal(pose, nx, ny, nz);

        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, ALPHA)
                .setNormal(pose, nx, ny, nz);
    }

    private static double centroidX(PlanShape shape) {
        double sum = 0;
        for (int[] p : shape.points) sum += p[0];
        return sum / shape.points.size();
    }

    private static double centroidZ(PlanShape shape) {
        double sum = 0;
        for (int[] p : shape.points) sum += p[1];
        return sum / shape.points.size();
    }
}
