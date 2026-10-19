package com.mansoority.shadow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ShadowRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TheShadow.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TheShadow.MODID);

    public static final RegistryObject<EntityType<ShadowEntity>> SHADOW = ENTITIES.register("shadow",
            () -> EntityType.Builder.<ShadowEntity>of(ShadowEntity::new, MobCategory.MISC)
                    .sized(2.2F, 11.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build("shadow"));

    /** index in this list is what the network packets send */
    public static final List<RegistryObject<SoundEvent>> SOUND_LIST = new ArrayList<>();
    public static final RegistryObject<SoundEvent> SCREAM = sound("scream");
    public static final RegistryObject<SoundEvent> STINGER = sound("stinger");
    public static final RegistryObject<SoundEvent> HIT = sound("hit");
    public static final RegistryObject<SoundEvent> DRONE = sound("drone");
    public static final RegistryObject<SoundEvent> WHISPER = sound("whisper");
    public static final RegistryObject<SoundEvent> STATIC = sound("static");
    public static final RegistryObject<SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final RegistryObject<SoundEvent> BREATH = sound("breath");
    public static final RegistryObject<SoundEvent> SNAP = sound("snap");
    public static final RegistryObject<SoundEvent> RISER = sound("riser");

    private static RegistryObject<SoundEvent> sound(String name) {
        RegistryObject<SoundEvent> ro = SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TheShadow.MODID, name)));
        SOUND_LIST.add(ro);
        return ro;
    }

    public static int indexOf(RegistryObject<SoundEvent> s) {
        return SOUND_LIST.indexOf(s);
    }

    private ShadowRegistry() {}
}
