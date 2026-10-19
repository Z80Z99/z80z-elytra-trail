package cn.blockforge.generated.modedbbb538;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "z80z_elytra_trail";

    public GeneratedMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TrailConfig.SERVER_SPEC, "elytra_trails-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TrailConfig.CLIENT_SPEC, "elytra_trails-client.toml");
        TrailNetwork.register();
        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new TrailConfigScreen(parent)));
        }
    }
}
