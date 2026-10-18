package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowRegistry;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientSetup {
    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onLayers);
        modBus.addListener(ClientSetup::onRenderers);
        modBus.addListener(ClientSetup::onOverlays);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onCamera);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onLogout);
    }

    private static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ShadowModel.LAYER, ShadowModel::createBodyLayer);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ShadowRegistry.SHADOW.get(), ShadowRenderer::new);
    }

    private static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("theshadow_fx", ClientFx::renderOverlay);
    }

    private ClientSetup() {}
}
