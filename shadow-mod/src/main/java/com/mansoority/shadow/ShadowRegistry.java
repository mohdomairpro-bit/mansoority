package com.mansoority.shadow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ShadowRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TheShadow.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TheShadow.MODID);
    public static final RegistryObject<SimpleParticleType> WISP = PARTICLES.register("wisp", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> DROOL = PARTICLES.register("drool", () -> new SimpleParticleType(false));
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TheShadow.MODID);

    public static final RegistryObject<EntityType<ShadowEntity>> SHADOW = ENTITIES.register("shadow",
            () -> EntityType.Builder.<ShadowEntity>of(ShadowEntity::new, MobCategory.MISC)
                    .sized(2.2F, 11.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build("shadow"));

    public static final RegistryObject<EntityType<MansoorityBoss>> BOSS = ENTITIES.register("mansoority_boss",
            () -> EntityType.Builder.<MansoorityBoss>of(MansoorityBoss::new, MobCategory.MONSTER)
                    .sized(2.2F, 11.5F).clientTrackingRange(16).updateInterval(1).fireImmune().build("mansoority_boss"));
    public static final RegistryObject<EntityType<ShadeEntity>> SHADE = ENTITIES.register("shade",
            () -> EntityType.Builder.<ShadeEntity>of(ShadeEntity::new, MobCategory.MONSTER)
                    .sized(0.55F, 1.6F).clientTrackingRange(8).fireImmune().build("shade"));

    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TheShadow.MODID);
    public static final RegistryObject<net.minecraft.world.item.Item> SHADOW_BLADE = ITEMS.register("shadow_blade", ShadowBladeItem::new);

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
    public static final RegistryObject<SoundEvent> FAR = sound("far");
    // the body, the boss, its voice
    public static final RegistryObject<SoundEvent> STEP = sound("step");
    public static final RegistryObject<SoundEvent> SLAM = sound("slam");
    public static final RegistryObject<SoundEvent> CRACK = sound("crack");
    public static final RegistryObject<SoundEvent> ROAR = sound("roar");
    public static final RegistryObject<SoundEvent> SCREECH = sound("screech");
    public static final RegistryObject<SoundEvent> GURGLE = sound("gurgle");
    public static final RegistryObject<SoundEvent> SPRAY = sound("spray");
    public static final RegistryObject<SoundEvent> CRUNCH = sound("crunch");
    public static final RegistryObject<SoundEvent> SPIKES = sound("spikes");
    public static final RegistryObject<SoundEvent> FLOOD = sound("flood");
    public static final RegistryObject<SoundEvent> V_DESTROY = sound("voice_destroy"), V_MINE = sound("voice_mine"), V_KNEEL = sound("voice_kneel"),
            V_AGAIN = sound("voice_again"), V_TASTE = sound("voice_taste"), V_NOWHERE = sound("voice_nowhere"), V_SLOW = sound("voice_slow"),
            V_STAY = sound("voice_stay"), V_YOURS = sound("voice_yours"), V_LAST = sound("voice_last");
    public static final RegistryObject<SoundEvent> MUSIC_1 = sound("boss_music_1"), MUSIC_2 = sound("boss_music_2"), MUSIC_3 = sound("boss_music_3");

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
