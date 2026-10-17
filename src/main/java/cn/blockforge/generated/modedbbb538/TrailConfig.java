package cn.blockforge.generated.modedbbb538;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TrailConfig {
    public enum ActivationMode {
        GLIDE, HIGH_SPEED, ROCKET_ONLY, CUSTOM
    }

    public enum TrailShape {
        STREAMER, WING_TIPS, BOTH
    }

    public enum TrailStyle {
        DUST, RAINBOW, FLAME, SOUL_FIRE, END_ROD, ENCHANT, SPARK, CHERRY
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.EnumValue<ActivationMode> ACTIVATION_MODE;
    public static final ForgeConfigSpec.DoubleValue GLIDE_SPEED;
    public static final ForgeConfigSpec.DoubleValue HIGH_SPEED;
    public static final ForgeConfigSpec.DoubleValue CUSTOM_SPEED;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_REQUIRES_ROCKET;
    public static final ForgeConfigSpec.IntValue ROCKET_MEMORY_TICKS;

    public static final ForgeConfigSpec.EnumValue<TrailShape> TRAIL_SHAPE;
    public static final ForgeConfigSpec.EnumValue<TrailStyle> TRAIL_STYLE;
    public static final ForgeConfigSpec.BooleanValue STARDUST;
    public static final ForgeConfigSpec.DoubleValue STARDUST_DENSITY;
    public static final ForgeConfigSpec.BooleanValue WATER_DROPS;
    public static final ForgeConfigSpec.BooleanValue AFTERIMAGES;
    public static final ForgeConfigSpec.BooleanValue WIND_SOUND;
    public static final ForgeConfigSpec.BooleanValue ROCKET_SOUND;
    public static final ForgeConfigSpec.BooleanValue SMART_LOAD_SHEDDING;
    public static final ForgeConfigSpec.IntValue PARTICLE_LIMIT;
    public static final ForgeConfigSpec.IntValue LOW_FPS_THRESHOLD;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        server.push("activation");
        ACTIVATION_MODE = server.comment("GLIDE: 0.6 blocks/s, HIGH_SPEED: 1.2 blocks/s, ROCKET_ONLY: boosted flight only, CUSTOM: custom options below")
                .defineEnum("mode", ActivationMode.GLIDE);
        GLIDE_SPEED = server.defineInRange("glideSpeedBlocksPerSecond", 0.6D, 0.0D, 100.0D);
        HIGH_SPEED = server.defineInRange("highSpeedBlocksPerSecond", 1.2D, 0.0D, 100.0D);
        CUSTOM_SPEED = server.defineInRange("customSpeedBlocksPerSecond", 0.9D, 0.0D, 100.0D);
        CUSTOM_REQUIRES_ROCKET = server.define("customRequiresRocket", false);
        ROCKET_MEMORY_TICKS = server.comment("How long a firework boost is considered active after a boost rocket appears")
                .defineInRange("rocketMemoryTicks", 30, 5, 100);
        server.pop();
        SERVER_SPEC = server.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.push("visuals");
        TRAIL_SHAPE = client.defineEnum("shape", TrailShape.BOTH);
        TRAIL_STYLE = client.defineEnum("style", TrailStyle.DUST);
        STARDUST = client.define("stardust", true);
        STARDUST_DENSITY = client.comment("Chance per trail sample; 0.05 is the planned density")
                .defineInRange("stardustDensity", 0.05D, 0.0D, 1.0D);
        WATER_DROPS = client.define("waterDrops", true);
        AFTERIMAGES = client.define("afterimages", true);
        client.pop();

        client.push("audio");
        WIND_SOUND = client.define("windSound", true);
        ROCKET_SOUND = client.define("rocketSound", true);
        client.pop();

        client.push("performance");
        SMART_LOAD_SHEDDING = client.define("smartLoadShedding", true);
        PARTICLE_LIMIT = client.comment("Hard cap for trail particles produced by this mod per client tick")
                .defineInRange("particleLimit", 200, 20, 200);
        LOW_FPS_THRESHOLD = client.comment("Below this FPS, the particle budget is automatically halved")
                .defineInRange("lowFpsThreshold", 45, 20, 120);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private TrailConfig() {
    }
}
