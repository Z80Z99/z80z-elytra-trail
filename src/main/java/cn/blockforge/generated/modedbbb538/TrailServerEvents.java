package cn.blockforge.generated.modedbbb538;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrailServerEvents {
    public static final String COLOR_TAG = GeneratedMod.MOD_ID + ":trail_color";
    private static final String ROCKET_TICKS_TAG = GeneratedMod.MOD_ID + ":rocket_ticks";
    private static final int DEFAULT_COLOR = DyeColor.LIGHT_BLUE.getFireworkColor();

    private TrailServerEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof DyeItem dyeItem) {
            int color = dyeItem.getDyeColor().getFireworkColor();
            event.getEntity().getPersistentData().putInt(COLOR_TAG, color);
            event.getEntity().displayClientMessage(
                    Component.translatable("message.mod_edbbb538.color_set",
                            Component.translatable("color.minecraft." + dyeItem.getDyeColor().getName())), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (stack.is(Items.FIREWORK_ROCKET) && event.getEntity().isFallFlying()) {
            event.getEntity().getPersistentData().putInt(ROCKET_TICKS_TAG, TrailConfig.ROCKET_MEMORY_TICKS.get());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        int rocketTicks = event.player.getPersistentData().getInt(ROCKET_TICKS_TAG);
        if (rocketTicks > 0) {
            event.player.getPersistentData().putInt(ROCKET_TICKS_TAG, rocketTicks - 1);
        }

        if (!(event.player instanceof ServerPlayer serverPlayer) || serverPlayer.tickCount % 2 != 0) {
            return;
        }

        boolean rocketBoost = rocketTicks > 0;
        double speed = serverPlayer.getDeltaMovement().length() * 20.0D;
        boolean active = shouldShowTrail(serverPlayer.isFallFlying(), speed, rocketBoost);
        int color = serverPlayer.getPersistentData().contains(COLOR_TAG)
                ? serverPlayer.getPersistentData().getInt(COLOR_TAG)
                : DEFAULT_COLOR;

        TrailNetwork.sendToTracking(serverPlayer,
                new TrailNetwork.TrailStatePacket(serverPlayer.getId(), active, rocketBoost, speed, color));
    }

    private static boolean shouldShowTrail(boolean fallFlying, double speed, boolean rocketBoost) {
        if (!fallFlying) {
            return false;
        }
        return switch (TrailConfig.ACTIVATION_MODE.get()) {
            case GLIDE -> speed > TrailConfig.GLIDE_SPEED.get();
            case HIGH_SPEED -> speed > TrailConfig.HIGH_SPEED.get();
            case ROCKET_ONLY -> rocketBoost;
            case CUSTOM -> speed > TrailConfig.CUSTOM_SPEED.get()
                    && (!TrailConfig.CUSTOM_REQUIRES_ROCKET.get() || rocketBoost);
        };
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(COLOR_TAG)) {
            event.getEntity().getPersistentData().putInt(COLOR_TAG,
                    event.getOriginal().getPersistentData().getInt(COLOR_TAG));
        }
    }
}
