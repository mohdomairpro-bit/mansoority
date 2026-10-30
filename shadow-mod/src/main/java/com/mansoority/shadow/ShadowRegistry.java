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
    public static final RegistryObject<SimpleParticleType> BILE = PARTICLES.register("bile", () -> new SimpleParticleType(false));
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

    public static final RegistryObject<EntityType<BoneSpike>> SPIKE = ENTITIES.register("bone_spike",
            () -> EntityType.Builder.<BoneSpike>of(BoneSpike::new, MobCategory.MISC).sized(1.2F, 3.2F).clientTrackingRange(8).updateInterval(2).fireImmune().build("bone_spike"));
    public static final RegistryObject<EntityType<ThrownBlade>> THROWN_BLADE = ENTITIES.register("thrown_blade",
            () -> EntityType.Builder.<ThrownBlade>of(ThrownBlade::new, MobCategory.MISC).sized(0.8F, 0.4F).clientTrackingRange(10).updateInterval(1).fireImmune().build("thrown_blade"));

    public static final RegistryObject<EntityType<PaleSpider>> PALE_BITER = ENTITIES.register("pale_biter",
            () -> EntityType.Builder.<PaleSpider>of(PaleSpider::new, MobCategory.MONSTER).sized(1.5F, 0.95F).clientTrackingRange(8).build("pale_biter"));
    public static final RegistryObject<EntityType<PaleSpider>> PALE_SPITTER = ENTITIES.register("pale_spitter",
            () -> EntityType.Builder.<PaleSpider>of(PaleSpider::new, MobCategory.MONSTER).sized(1.2F, 0.8F).clientTrackingRange(8).build("pale_spitter"));
    public static final RegistryObject<EntityType<HollowHusk>> HUSK = ENTITIES.register("hollow_husk",
            () -> EntityType.Builder.<HollowHusk>of(HollowHusk::new, MobCategory.MONSTER).sized(1.0F, 0.8F).clientTrackingRange(8).build("hollow_husk"));
    public static final RegistryObject<EntityType<Scavenger>> SCAVENGER = ENTITIES.register("scavenger",
            () -> EntityType.Builder.<Scavenger>of(Scavenger::new, MobCategory.MISC).sized(1.4F, 1.9F).clientTrackingRange(12).updateInterval(1).fireImmune().build("scavenger"));
    public static final RegistryObject<EntityType<WebShot>> WEB_SHOT = ENTITIES.register("web_shot",
            () -> EntityType.Builder.<WebShot>of(WebShot::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(5).build("web_shot"));

    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TheShadow.MODID);
    public static final RegistryObject<net.minecraft.world.item.Item> SHADOW_BLADE = ITEMS.register("shadow_blade", ShadowBladeItem::new);
    public static final RegistryObject<net.minecraft.world.item.Item> HOLLOW_HEART = ITEMS.register("hollow_heart", HollowHeartItem::new);

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TheShadow.MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TheShadow.MODID);
    public static final RegistryObject<net.minecraft.world.level.block.Block> SKULL_BLOCK = BLOCKS.register("mansoority_skull", SkullBlock::new);
    public static final RegistryObject<net.minecraft.world.item.Item> SKULL_ITEM = ITEMS.register("mansoority_skull",
            () -> new net.minecraft.world.item.BlockItem(SKULL_BLOCK.get(), new net.minecraft.world.item.Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SkullBlockEntity>> SKULL_BE = BLOCK_ENTITIES.register("mansoority_skull",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SkullBlockEntity::new, SKULL_BLOCK.get()).build(null));

    // ---- stage 3: spiders, silk, the ward, Shade armour, pale bone
    public static final RegistryObject<net.minecraft.world.item.Item> PALE_SILK = ITEMS.register("pale_silk", () -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()));
    public static final RegistryObject<net.minecraft.world.item.Item> SHADE_ESSENCE = ITEMS.register("shade_essence", () -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<net.minecraft.world.item.Item> PALE_WARD = ITEMS.register("pale_ward", ShadowGear.PaleWard::new);
    public static final RegistryObject<net.minecraft.world.item.Item> SHADE_HELMET = ITEMS.register("shade_helmet", () -> new ShadowGear.ShadeArmor(net.minecraft.world.item.ArmorItem.Type.HELMET));
    public static final RegistryObject<net.minecraft.world.item.Item> SHADE_CHESTPLATE = ITEMS.register("shade_chestplate", () -> new ShadowGear.ShadeArmor(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));
    public static final RegistryObject<net.minecraft.world.item.Item> SHADE_LEGGINGS = ITEMS.register("shade_leggings", () -> new ShadowGear.ShadeArmor(net.minecraft.world.item.ArmorItem.Type.LEGGINGS));
    public static final RegistryObject<net.minecraft.world.item.Item> SHADE_BOOTS = ITEMS.register("shade_boots", () -> new ShadowGear.ShadeArmor(net.minecraft.world.item.ArmorItem.Type.BOOTS));
    public static final RegistryObject<net.minecraft.world.item.Item> BITER_EGG = ITEMS.register("pale_biter_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(PALE_BITER, 0xE8E4D8, 0x5A0E0E, new net.minecraft.world.item.Item.Properties()));
    public static final RegistryObject<net.minecraft.world.item.Item> HUSK_EGG = ITEMS.register("hollow_husk_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(HUSK, 0xA8A494, 0x1A1420, new net.minecraft.world.item.Item.Properties()));
    public static final RegistryObject<net.minecraft.world.item.Item> SPITTER_EGG = ITEMS.register("pale_spitter_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(PALE_SPITTER, 0xE8E4D8, 0x7C8A56, new net.minecraft.world.item.Item.Properties()));

    public static final List<RegistryObject<net.minecraft.world.level.block.Block>> PALE_BLOCKS = new ArrayList<>();
    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties bone() {
        return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.XYLOPHONE).requiresCorrectToolForDrops()
                .strength(2.2F, 6F).sound(net.minecraft.world.level.block.SoundType.BONE_BLOCK);
    }
    private static RegistryObject<net.minecraft.world.level.block.Block> block(String name, java.util.function.Supplier<net.minecraft.world.level.block.Block> b) {
        RegistryObject<net.minecraft.world.level.block.Block> ro = BLOCKS.register(name, b);
        ITEMS.register(name, () -> new net.minecraft.world.item.BlockItem(ro.get(), new net.minecraft.world.item.Item.Properties()));
        PALE_BLOCKS.add(ro);
        return ro;
    }
    public static final RegistryObject<net.minecraft.world.level.block.Block> PALE_BONE = block("pale_bone", () -> new net.minecraft.world.level.block.RotatedPillarBlock(bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> PALE_BONE_BRICKS = block("pale_bone_bricks", () -> new net.minecraft.world.level.block.Block(bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> CRACKED_PALE_BONE_BRICKS = block("cracked_pale_bone_bricks", () -> new net.minecraft.world.level.block.Block(bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> CHISELED_PALE_BONE = block("chiseled_pale_bone", () -> new net.minecraft.world.level.block.Block(bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> PALE_BONE_TILES = block("pale_bone_tiles", () -> new net.minecraft.world.level.block.Block(bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> PALE_BONE_BRICK_STAIRS = block("pale_bone_brick_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(() -> PALE_BONE_BRICKS.get().defaultBlockState(), bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> PALE_BONE_BRICK_SLAB = block("pale_bone_brick_slab", () -> new net.minecraft.world.level.block.SlabBlock(bone()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> PALE_BONE_BRICK_WALL = block("pale_bone_brick_wall", () -> new net.minecraft.world.level.block.WallBlock(bone().forceSolidOn()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> WEB_FLOOR = block("web_floor", WebFloorBlock::new);

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
