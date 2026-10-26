package com.mansoority.shadow;

import com.mansoority.shadow.client.ClientSetup;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
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
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheShadow() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ShadowRegistry.ENTITIES.register(bus);
        ShadowRegistry.SOUNDS.register(bus);
        ShadowRegistry.PARTICLES.register(bus);
        ShadowRegistry.ITEMS.register(bus);
        ShadowRegistry.BLOCKS.register(bus);
        ShadowRegistry.BLOCK_ENTITIES.register(bus);
        bus.addListener(this::onTabs);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShadowConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new ShadowEvents());
        MinecraftForge.EVENT_BUS.register(new ShadowTrails());
        if (Boolean.getBoolean("theshadow.servertest")) MinecraftForge.EVENT_BUS.addListener(ServerSmokeTest::onServerTick);
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.init(bus);
        }
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ShadowRegistry.SHADOW.get(), ShadowEntity.createAttributes().build());
        event.put(ShadowRegistry.BOSS.get(), MansoorityBoss.createAttributes().build());
        event.put(ShadowRegistry.SHADE.get(), ShadeEntity.createAttributes().build());
    }

    private void onTabs(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.COMBAT) event.accept(ShadowRegistry.SHADOW_BLADE);
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.INGREDIENTS) event.accept(ShadowRegistry.HOLLOW_HEART);
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(ShadowRegistry.SKULL_ITEM);
    }

    private void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ShadowNet::register);
    }
}
