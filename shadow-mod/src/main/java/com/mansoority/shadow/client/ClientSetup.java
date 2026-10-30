package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowRegistry;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientSetup {
    public static void init(IEventBus modBus) {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ShadowConfigScreen(parent)));
        modBus.addListener(ClientSetup::onRenderers);
        com.mansoority.shadow.SkullBlockEntity.CLIENT_TICK = ReviveFx::tick;
        modBus.addListener(ClientSetup::onOverlays);
        modBus.addListener(ClientSetup::onParticles);
        modBus.addListener(ClientSetup::onLayers);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onCamera);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onFov);
        MinecraftForge.EVENT_BUS.addListener(RingRenderer::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClientScares::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(RingRenderer::onFog);
        MinecraftForge.EVENT_BUS.addListener(RingRenderer::onFogColor);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::onLogout);
        MinecraftForge.EVENT_BUS.addListener(CinematicCam::onHand);
        MinecraftForge.EVENT_BUS.addListener(CinematicCam::onGui);
        MinecraftForge.EVENT_BUS.addListener(CinematicCam::onInput);
        MinecraftForge.EVENT_BUS.addListener(BossFx::fog);
        MinecraftForge.EVENT_BUS.addListener(BossFx::renderLevel);
        MinecraftForge.EVENT_BUS.addListener(BossFx::fogColor);
        if (AutoTest.enabled()) MinecraftForge.EVENT_BUS.addListener(AutoTest::onClientTick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ShadowRegistry.SHADOW.get(), ShadowRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.BOSS.get(), ShadowRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.SHADE.get(), ShadeRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.SPIKE.get(), SpikeRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.THROWN_BLADE.get(), ThrownBladeRenderer::new);
        event.registerBlockEntityRenderer(ShadowRegistry.SKULL_BE.get(), SkullRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.PALE_BITER.get(), PaleSpiderRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.PALE_SPITTER.get(), PaleSpiderRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.HUSK.get(), HuskRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.SCAVENGER.get(), ScavengerRenderer::new);
        event.registerEntityRenderer(ShadowRegistry.WEB_SHOT.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    private static void onLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer<net.minecraft.client.player.AbstractClientPlayer, net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>> r = event.getSkin(skin);
            if (r != null) r.addLayer(new HandprintLayer(r));
        }
    }

    private static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("theshadow_scares", ClientScares::render);
        event.registerAboveAll("theshadow_cinema", CinematicCam::render);
        event.registerAboveAll("theshadow_fx", ClientFx::renderOverlay);
        event.registerAboveAll("theshadow_boss", BossFx::render);
    }

    private static void onParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ShadowRegistry.WISP.get(), WispParticle.Provider::new);
        event.registerSpriteSet(ShadowRegistry.DROOL.get(), DroolParticle.Provider::new);
        event.registerSpriteSet(ShadowRegistry.BILE.get(), sp -> new DroolParticle.Provider(sp, true));
    }

    private ClientSetup() {}
}
