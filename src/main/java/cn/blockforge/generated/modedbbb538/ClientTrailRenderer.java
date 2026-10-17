package cn.blockforge.generated.modedbbb538;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 连续拖尾丝带：走标准实体渲染管线（事件 PoseStack + MultiBufferSource + 自定义加色 RenderType），
 * 矩阵与提交全部由原版处理。丝带沿相机右向量展开（billboard，任意视角不退化），
 * 头宽亮尾细暗；距相机 1.2 格内的段剔除并淡出，避免第一人称贴脸楔形伪影。
 */
@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTrailRenderer {

    private static final double NEAR_CULL = 1.2D;

    private static final float[] FLAME_HEAD = {1.00F, 0.82F, 0.30F};
    private static final float[] FLAME_TAIL = {0.85F, 0.18F, 0.05F};
    private static final float[] SOUL_HEAD = {0.45F, 0.95F, 0.90F};
    private static final float[] SOUL_TAIL = {0.15F, 0.30F, 0.90F};

    /** 加色、无背面剔除、不写深度的 QUADS 渲染类型。 */
    private static final RenderType RIBBON_TYPE = RenderType.create(
            GeneratedMod.MOD_ID + ":trail_ribbon",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            512,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "trail_additive",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                            },
                            () -> {
                                RenderSystem.disableBlend();
                                RenderSystem.defaultBlendFunc();
                            }))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                    .createCompositeState(false));

    private ClientTrailRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || ClientTrailManager.trails().isEmpty()) {
            return;
        }

        TrailConfig.TrailShape shape = TrailConfig.TRAIL_SHAPE.get();
        TrailConfig.TrailStyle style = TrailConfig.TRAIL_STYLE.get();
        boolean streamer = shape == TrailConfig.TrailShape.STREAMER || shape == TrailConfig.TrailShape.BOTH;
        boolean wingTips = shape == TrailConfig.TrailShape.WING_TIPS || shape == TrailConfig.TrailShape.BOTH;
        if (!streamer && !wingTips) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        Quaternionf rotation = camera.rotation();
        float partialTick = event.getPartialTick();
        long now = level.getGameTime();

        Vector3f rightVec = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
        Vec3 cameraRight = new Vec3(rightVec.x(), rightVec.y(), rightVec.z());

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = pose.last().pose();

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RIBBON_TYPE);
        for (ClientTrailManager.TrailState state : ClientTrailManager.trails()) {
            if (!state.active) {
                continue;
            }
            net.minecraft.world.entity.Entity entity = level.getEntity(state.entityId);
            if (entity == null) {
                continue;
            }
            Vec3 headFeet = entity.getPosition(partialTick).add(0.0D, 0.1D, 0.0D);
            Vec3 wingRight = horizontalRight(entity);
            Vec3 headChest = entity.getPosition(partialTick).add(0.0D, 0.78D, 0.0D);
            if (streamer) {
                ribbon(consumer, matrix, cameraRight, state.center, headFeet, camPos, 0.16D, 1.25D, style, state.color, now, 2);
            }
            if (wingTips) {
                ribbon(consumer, matrix, cameraRight, state.left, headChest.add(wingRight.scale(0.86D)), camPos, 0.09D, 0.90D, style, state.color, now, 2);
                ribbon(consumer, matrix, cameraRight, state.right, headChest.add(wingRight.scale(-0.86D)), camPos, 0.09D, 0.90D, style, state.color, now, 2);
            }
        }
        pose.popPose();
        buffers.endBatch(RIBBON_TYPE);
    }

    /** 与 ClientTrailManager.updateAnchors 相同的水平右向向量算法。 */
    private static Vec3 horizontalRight(net.minecraft.world.entity.Entity entity) {
        Vec3 movement = entity.getDeltaMovement();
        Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
        if (horizontal.lengthSqr() > 1.0E-5D) {
            horizontal = horizontal.normalize();
            return new Vec3(-horizontal.z, 0.0D, horizontal.x);
        }
        double yaw = Math.toRadians(entity.getYRot());
        return new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
    }

    /**
     * 沿轨迹样条生成 billboard 丝带；history 头部是最新点（贴身），尾部最旧。
     * headOverride 为实体的插值位置：每帧覆盖头部 tick 采样点，使丝带头帧级平滑（消除 20Hz 跳变闪烁）。
     * 顶点为世界绝对坐标，变换由 matrix 完成。
     */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3 cameraRight, Deque<Vec3> history,
                               Vec3 headOverride, Vec3 camPos, double halfWidth, double intensity,
                               TrailConfig.TrailStyle style, int color, long now, int subdivisions) {
        int count = history.size();
        if (count < 3) {
            return;
        }
        Vec3[] samples = new Vec3[count];
        int index = 0;
        for (Iterator<Vec3> iterator = history.descendingIterator(); iterator.hasNext(); ) {
            samples[index++] = iterator.next();
        }
        samples[count - 1] = headOverride;

        List<Vec3> spline = new ArrayList<>((count - 1) * subdivisions + 1);
        for (int i = 0; i < count - 1; i++) {
            Vec3 p0 = samples[Math.max(0, i - 1)];
            Vec3 p1 = samples[i];
            Vec3 p2 = samples[i + 1];
            Vec3 p3 = samples[Math.min(count - 1, i + 2)];
            for (int s = 0; s < subdivisions; s++) {
                spline.add(catmullRom(p0, p1, p2, p3, s / (double) subdivisions));
            }
        }
        spline.add(samples[count - 1]);

        int total = spline.size();
        for (int i = 0; i + 1 < total; i++) {
            Vec3 a = spline.get(i);
            Vec3 b = spline.get(i + 1);
            if (a.distanceToSqr(b) < 1.0E-12D) {
                continue;
            }

            double distA = a.distanceTo(camPos);
            double distB = b.distanceTo(camPos);
            if (Math.max(distA, distB) < NEAR_CULL) {
                continue;
            }

            // spline[0] 是最旧的轨迹点（尾部），progress 越小越贴近玩家头部
            float progressA = 1.0F - i / (float) (total - 1);
            float progressB = 1.0F - (i + 1) / (float) (total - 1);
            double widthA = halfWidth * (1.0D - progressA) + 0.008D;
            double widthB = halfWidth * (1.0D - progressB) + 0.008D;
            float nearFadeA = (float) Mth.clamp((distA - NEAR_CULL) / NEAR_CULL, 0.0D, 1.0D);
            float nearFadeB = (float) Mth.clamp((distB - NEAR_CULL) / NEAR_CULL, 0.0D, 1.0D);
            float brightnessA = (1.0F - progressA) * (float) intensity * nearFadeA;
            float brightnessB = (1.0F - progressB) * (float) intensity * nearFadeB;
            float[] colorA = styleColor(style, color, progressA, now, i);
            float[] colorB = styleColor(style, color, progressB, now, i + 1);

            putVertex(consumer, matrix, a.add(cameraRight.scale(widthA)), colorA, brightnessA);
            putVertex(consumer, matrix, a.subtract(cameraRight.scale(widthA)), colorA, brightnessA);
            putVertex(consumer, matrix, b.subtract(cameraRight.scale(widthB)), colorB, brightnessB);
            putVertex(consumer, matrix, b.add(cameraRight.scale(widthB)), colorB, brightnessB);
        }
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(
                0.5D * ((2.0D * p1.x) + (-p0.x + p2.x) * t
                        + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2
                        + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3),
                0.5D * ((2.0D * p1.y) + (-p0.y + p2.y) * t
                        + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2
                        + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3),
                0.5D * ((2.0D * p1.z) + (-p0.z + p2.z) * t
                        + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2
                        + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3)
        );
    }

    private static float[] styleColor(TrailConfig.TrailStyle style, int color, float progress, long now, int index) {
        return switch (style) {
            case DUST -> unpack(color);
            case RAINBOW -> unpack(Mth.hsvToRgb(frac(now * 0.02F + index * 0.015F - progress * 0.25F), 0.85F, 1.0F));
            case FLAME -> mix(FLAME_HEAD, FLAME_TAIL, progress);
            case SOUL_FIRE -> mix(SOUL_HEAD, SOUL_TAIL, progress);
            case END_ROD -> new float[]{1.00F, 0.95F, 0.78F};
            case ENCHANT -> new float[]{0.62F, 0.30F, 0.95F};
            case SPARK -> new float[]{0.70F, 0.90F, 1.00F};
            case CHERRY -> new float[]{1.00F, 0.66F, 0.82F};
        };
    }

    private static void putVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos,
                                  float[] rgb, double brightness) {
        int r = Mth.clamp((int) (rgb[0] * brightness * 255.0D), 0, 255);
        int g = Mth.clamp((int) (rgb[1] * brightness * 255.0D), 0, 255);
        int b = Mth.clamp((int) (rgb[2] * brightness * 255.0D), 0, 255);
        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, 255)
                .endVertex();
    }

    private static float frac(float value) {
        return value - (float) Math.floor(value);
    }

    private static float[] unpack(int rgb) {
        return new float[]{((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F};
    }

    private static float[] mix(float[] from, float[] to, float t) {
        return new float[]{
                Mth.lerp(t, from[0], to[0]),
                Mth.lerp(t, from[1], to[1]),
                Mth.lerp(t, from[2], to[2])
        };
    }
}
