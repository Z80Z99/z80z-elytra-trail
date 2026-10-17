package cn.blockforge.generated.modedbbb538;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class TrailNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private TrailNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(TrailStatePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TrailStatePacket::encode)
                .decoder(TrailStatePacket::decode)
                .consumerMainThread(TrailStatePacket::handle)
                .add();
    }

    public static void sendToTracking(net.minecraft.world.entity.Entity entity, TrailStatePacket packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    public record TrailStatePacket(int entityId, boolean active, boolean rocketBoost, double speed, int color) {
        private static void encode(TrailStatePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeBoolean(packet.active);
            buffer.writeBoolean(packet.rocketBoost);
            buffer.writeDouble(packet.speed);
            buffer.writeInt(packet.color);
        }

        private static TrailStatePacket decode(FriendlyByteBuf buffer) {
            return new TrailStatePacket(
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readDouble(),
                    buffer.readInt()
            );
        }

        private static void handle(TrailStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientTrailManager.accept(packet)));
            context.setPacketHandled(true);
        }
    }
}
