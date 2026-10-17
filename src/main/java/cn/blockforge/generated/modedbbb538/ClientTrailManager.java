package cn.blockforge.generated.modedbbb538;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTrailManager {
    private static final Map<Integer, TrailState> STATES = new HashMap<>();
    private static final int HISTORY_LENGTH = 40;
    private static int particleBudget;

    private ClientTrailManager() {
    }

    public static void accept(TrailNetwork.TrailStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        TrailState state = STATES.computeIfAbsent(packet.entityId(), TrailState::new);
        state.active = packet.active();
        state.speed = packet.speed();
        state.color = packet.color();
        state.lastPacketTime = now;
        if (packet.rocketBoost() && !state.rocketBoost && TrailConfig.ROCKET_SOUND.get() && minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(packet.entityId());
            if (entity != null) {
                minecraft.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.75F, 0.85F, false);
            }
        }
        state.rocketBoost = packet.rocketBoost();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        while (TrailKeys.OPEN_CONFIG.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new TrailConfigScreen());
            }
        }
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            STATES.clear();
            return;
        }

        particleBudget = getParticleBudget(minecraft);
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, TrailState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrailState> entry = iterator.next();
            TrailState state = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player) || now - state.lastPacketTime > 40L) {
                iterator.remove();
                continue;
            }

            if (!state.active || now - state.lastPacketTime > 8L) {
                state.clearHistory();
                state.rocketBoost = false;
                continue;
            }

            updateAnchors(player, state);
            renderTrail(level, player, state, now);
            playWind(level, player, state, now);
        }
    }

    private static int getParticleBudget(Minecraft minecraft) {
        int budget = TrailConfig.PARTICLE_LIMIT.get();
        if (!TrailConfig.SMART_LOAD_SHEDDING.get()) {
            return budget;
        }
        ParticleStatus status = (ParticleStatus) minecraft.options.particles().get();
        int fps = minecraft.getFps();
        if (status != ParticleStatus.ALL || (fps > 0 && fps < TrailConfig.LOW_FPS_THRESHOLD.get())) {
            budget /= 2;
        }
        return Math.max(10, budget);
    }

    private static void updateAnchors(Player player, TrailState state) {
        Vec3 movement = player.getDeltaMovement();
        Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
        Vec3 right;
        if (horizontal.lengthSqr() > 1.0E-5D) {
            horizontal = horizontal.normalize();
            right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        } else {
            double yaw = Math.toRadians(player.getYRot());
            right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        }

        Vec3 feet = player.position().add(0.0D, 0.1D, 0.0D);
        Vec3 chest = player.position().add(0.0D, 0.78D, 0.0D);
        push(state.center, feet);
        push(state.left, chest.add(right.scale(0.86D)));
        push(state.right, chest.add(right.scale(-0.86D)));
    }

    private static void push(Deque<Vec3> history, Vec3 value) {
        history.addFirst(value);
        while (history.size() > HISTORY_LENGTH) {
            history.removeLast();
        }
    }

    private static void renderTrail(ClientLevel level, Player player, TrailState state, long now) {
        if (state.center.size() < 2 || particleBudget <= 0) {
            return;
        }

        TrailConfig.TrailStyle style = TrailConfig.TRAIL_STYLE.get();
        // 连续线体由 ClientTrailRenderer 的丝带网格绘制；这里只给特效样式撒少量火花点缀
        if (style != TrailConfig.TrailStyle.DUST && style != TrailConfig.TrailStyle.RAINBOW
                && level.random.nextInt(3) == 0) {
            List<Vec3> points = new ArrayList<>(state.center);
            Vec3 point = points.get(level.random.nextInt(points.size()));
            spawn(level, trailParticle(style, state.color, now, level.random.nextInt(64), 0.72F),
                    point, 0.0D, riseOf(style), 0.0D);
        }

        Vec3 newest = state.center.peekFirst();
        if (newest == null) {
            return;
        }

        if (TrailConfig.STARDUST.get() && level.random.nextDouble() < TrailConfig.STARDUST_DENSITY.get()) {
            spawn(level, ParticleTypes.END_ROD, newest.add(randomSpread(level, 0.22D)), 0.0D, 0.005D, 0.0D);
        }

        if (TrailConfig.WATER_DROPS.get()) {
            boolean raining = level.isRainingAt(player.blockPosition());
            int attempts = raining ? 3 : 1;
            double chance = raining ? 0.7D : 0.08D;
            for (int i = 0; i < attempts; i++) {
                if (level.random.nextDouble() < chance) {
                    spawn(level, ParticleTypes.BUBBLE_POP, newest.add(randomSpread(level, 0.15D)),
                            0.0D, -0.015D, 0.0D);
                }
            }
        }

        if (TrailConfig.AFTERIMAGES.get() && now % 10L == Math.floorMod(player.getId(), 10)) {
            renderAfterimage(level, player, state.color);
        }
    }

    private static double riseOf(TrailConfig.TrailStyle style) {
        return style == TrailConfig.TrailStyle.FLAME || style == TrailConfig.TrailStyle.SOUL_FIRE ? 0.012D : 0.0D;
    }

    private static ParticleOptions trailParticle(TrailConfig.TrailStyle style, int color, long now, int index, float size) {
        return switch (style) {
            case DUST -> dust(color, size);
            case RAINBOW -> dust(Mth.hsvToRgb((float) ((now * 0.02D + index * 0.07D) % 1.0D), 0.85F, 1.0F), size);
            case FLAME -> ParticleTypes.FLAME;
            case SOUL_FIRE -> ParticleTypes.SOUL_FIRE_FLAME;
            case END_ROD -> ParticleTypes.END_ROD;
            case ENCHANT -> ParticleTypes.ENCHANT;
            case SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case CHERRY -> ParticleTypes.CHERRY_LEAVES;
        };
    }

    private static void renderAfterimage(ClientLevel level, Player player, int color) {
        DustParticleOptions ghost = dust(color, 1.15F);
        Vec3 movement = player.getDeltaMovement();
        Vec3 back = movement.lengthSqr() > 1.0E-5D ? movement.normalize().scale(-0.8D) : Vec3.ZERO;
        Vec3 base = player.position().add(back);
        double[] heights = {0.15D, 0.48D, 0.82D, 1.12D, 1.42D};
        double[] widths = {0.10D, 0.18D, 0.30D, 0.24D, 0.13D};
        double yaw = Math.toRadians(player.getYRot());
        Vec3 side = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        for (int i = 0; i < heights.length; i++) {
            spawn(level, ghost, base.add(0.0D, heights[i], 0.0D), 0.0D, 0.008D, 0.0D);
            if (i > 0 && i < heights.length - 1) {
                spawn(level, ghost, base.add(side.scale(widths[i])).add(0.0D, heights[i], 0.0D),
                        0.0D, 0.008D, 0.0D);
                spawn(level, ghost, base.add(side.scale(-widths[i])).add(0.0D, heights[i], 0.0D),
                        0.0D, 0.008D, 0.0D);
            }
        }
    }

    private static void playWind(ClientLevel level, Player player, TrailState state, long now) {
        if (!TrailConfig.WIND_SOUND.get() || now % 18L != Math.floorMod(player.getId(), 18)) {
            return;
        }
        float volume = (float) Math.min(0.85D, 0.08D + state.speed * 0.025D);
        float pitch = (float) Math.min(1.45D, 0.72D + state.speed * 0.015D);
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING,
                SoundSource.PLAYERS, volume, pitch, false);
    }

    private static Vec3 randomSpread(ClientLevel level, double radius) {
        return new Vec3(
                (level.random.nextDouble() - 0.5D) * radius * 2.0D,
                (level.random.nextDouble() - 0.5D) * radius * 2.0D,
                (level.random.nextDouble() - 0.5D) * radius * 2.0D
        );
    }

    private static DustParticleOptions dust(int color, float size) {
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        return new DustParticleOptions(new Vector3f(red, green, blue), size);
    }

    private static void spawn(ClientLevel level, ParticleOptions particle, Vec3 position,
                              double velocityX, double velocityY, double velocityZ) {
        if (particleBudget <= 0) {
            return;
        }
        level.addParticle(particle, position.x, position.y, position.z, velocityX, velocityY, velocityZ);
        particleBudget--;
    }

    public static java.util.Collection<TrailState> trails() {
        return java.util.Collections.unmodifiableCollection(STATES.values());
    }

    /** 渲染器用：按实体 id 查询轨迹状态，以便计算插值头部锚点。 */
    public static java.util.Map<Integer, TrailState> trailsById() {
        return java.util.Collections.unmodifiableMap(STATES);
    }

    static final class TrailState {
        final int entityId;
        final Deque<Vec3> center = new ArrayDeque<>();
        final Deque<Vec3> left = new ArrayDeque<>();
        final Deque<Vec3> right = new ArrayDeque<>();
        boolean active;
        boolean rocketBoost;
        double speed;
        int color = 0x66CCFF;
        long lastPacketTime;

        TrailState(int entityId) {
            this.entityId = entityId;
        }

        private void clearHistory() {
            center.clear();
            left.clear();
            right.clear();
        }
    }
}
