package com.mansoority.shadow;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ShadowNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TheShadow.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(FxPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode)
                .decoder(FxPacket::new)
                .consumerMainThread(FxPacket::handle)
                .add();
    }

    public static void send(ServerPlayer player, FxPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private ShadowNet() {}
}
