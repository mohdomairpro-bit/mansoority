package com.mansoority.shadow;

import com.mansoority.shadow.client.ClientSetup;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(TheShadow.MODID)
public class TheShadow {
    public static final String MODID = "theshadow";

    public TheShadow() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ShadowRegistry.ENTITIES.register(bus);
        ShadowRegistry.SOUNDS.register(bus);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShadowConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new ShadowEvents());
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.init(bus);
        }
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ShadowRegistry.SHADOW.get(), ShadowEntity.createAttributes().build());
    }

    private void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ShadowNet::register);
    }
}
