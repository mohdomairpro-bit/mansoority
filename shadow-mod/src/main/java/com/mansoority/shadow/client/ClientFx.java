package com.mansoority.shadow.client;

import com.mansoority.shadow.FxPacket;
import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.ShadowRegistry;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;

/** everything the victim sees and hears. Nothing here touches the world. */
public final class ClientFx {
    private static final int JS_FRAMES = 26;
    private static final ResourceLocation[] JS_FRAME = new ResourceLocation[JS_FRAMES];
    static {
        for (int i = 0; i < JS_FRAMES; i++) JS_FRAME[i] = new ResourceLocation(TheShadow.MODID, String.format("textures/gui/js/f%02d.png", i));
    }
    private static final ResourceLocation VIGNETTE = new ResourceLocation(TheShadow.MODID, "textures/gui/vignette.png");
    private static final ResourceLocation STATIC = new ResourceLocation(TheShadow.MODID, "textures/gui/static.png");
    private static final Random R = new Random();

    public static final int SCARE_AT = 80;      // tick the face hits you
    private static final int SCARE_LEN = 34;
    private static final int END = 175;

    private static int js = -1;                 // jumpscare timeline tick, -1 = off
    private static int seq = -1, seqTick;       // scripted sound event
    private static boolean preloaded;
    private static float stare;                 // 0..1 how hard you're staring at it right now
    private static boolean rushing;             // it's running at you
    private static float lightsOut;             // 0..1 extra darkness for the 'lights out' event

    // ------------------------------------------------------------------ packets
    public static void handle(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        switch (p.kind) {
            case FxPacket.JUMPSCARE -> js = 0;
            case FxPacket.JUMPSCARE_NOW -> js = SCARE_AT - 10;
            case FxPacket.SOUND_UI -> ui(sound(p.sound), p.volume, p.pitch);
            case FxPacket.SOUND_AT -> at(sound(p.sound), p.x, p.y, p.z, p.volume, p.pitch);
            case FxPacket.SEQUENCE -> { seq = p.sound; seqTick = 0; }
            default -> {}
        }
        if (p.kind == FxPacket.JUMPSCARE || p.kind == FxPacket.JUMPSCARE_NOW) mc.getSoundManager().stop();
    }

    private static SoundEvent sound(int index) {
        if (index < 0 || index >= ShadowRegistry.SOUND_LIST.size()) index = 0;
        return ShadowRegistry.SOUND_LIST.get(index).get();
    }

    private static void ui(SoundEvent s, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private static void at(SoundEvent s, double x, double y, double z, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                new SimpleSoundInstance(s, SoundSource.MASTER, volume, pitch, RandomSource.create(), x, y, z));
    }

    /** a sound 'dist' blocks away at 'angle' degrees from where you look (180 = right behind you) */
    private static void around(SoundEvent s, double angle, double dist, double up, float volume, float pitch) {
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl == null) return;
        double a = Math.toRadians(pl.getYRot() + angle);
        at(s, pl.getX() - Math.sin(a) * dist, pl.getEyeY() + up, pl.getZ() + Math.cos(a) * dist, volume, pitch);
    }

    // ------------------------------------------------------------------ ticking
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { js = -1; seq = -1; stare = 0; lightsOut = 0; return; }
        if (mc.isPaused()) return;
        if (!preloaded) {   // load the jumpscare frames up front so the first playback doesn't stutter
            for (ResourceLocation f : JS_FRAME) mc.getTextureManager().getTexture(f);
            preloaded = true;
        }

        // how hard is it being stared at (only the shadow that is after *me*)
        float target = 0F;
        rushing = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ShadowEntity s && !s.isHidden() && (s.getTargetEntityId() == mc.player.getId() || inZone(mc, s))) {
                target = Math.max(target, Math.min(1F, s.getStare() / (float) ShadowEntity.STARE_LIMIT));
                if (s.getRush() > 0) { target = 1F; rushing = true; }
            }
        }
        stare += (target - stare) * 0.25F;

        if (js >= 0) tickJumpscare(mc);
        if (seq >= 0) tickSequence(mc);
    }

    private static void tickJumpscare(Minecraft mc) {
        int t = js;
        // build-up: silence, heart racing, something breathing on your neck
        int[] beats = {10, 22, 32, 40, 47, 52, 56, 59, 61};
        for (int i = 0; i < beats.length; i++) {
            if (t == beats[i]) {
                float pitch = 0.8F + i * 0.06F;
                ui(ShadowRegistry.HEARTBEAT.get(), 1F, pitch);
                ui(ShadowRegistry.HEARTBEAT.get(), 1F, pitch);
            }
        }
        if (t == 4) ui(ShadowRegistry.DRONE.get(), 0.8F, 0.7F);
        if (t == 20) around(ShadowRegistry.BREATH.get(), 180, 0.7, 0, 1F, 0.8F);
        if (t == 42) around(ShadowRegistry.WHISPER.get(), 210, 0.6, 0, 1F, 0.7F);
        if (t == 63) mc.getSoundManager().stop();
        // THE SCARE
        if (t == SCARE_AT) {
            for (float p : new float[]{1F, 0.94F, 1.06F, 1F, 0.97F}) ui(ShadowRegistry.SCREAM.get(), 1F, p);
            for (float p : new float[]{1F, 0.8F, 1F}) ui(ShadowRegistry.HIT.get(), 1F, p);
            ui(ShadowRegistry.STINGER.get(), 1F, 1F);
            ui(ShadowRegistry.STATIC.get(), 1F, 1F);
        }
        if (t == SCARE_AT + 12) for (float p : new float[]{0.75F, 0.7F, 0.72F}) ui(ShadowRegistry.SCREAM.get(), 1F, p);
        if (t == SCARE_AT + 24) ui(ShadowRegistry.SNAP.get(), 1F, 0.6F);
        if (t == SCARE_AT + SCARE_LEN) mc.getSoundManager().stop();
        if (t == SCARE_AT + 62) around(ShadowRegistry.WHISPER.get(), 180, 0.6, 0, 1F, 0.8F);
        if (t == SCARE_AT + 75 && mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal("it's still here").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
        }
        js++;
        if (js > END) js = -1;
    }

    private static void tickSequence(Minecraft mc) {
        int t = seqTick++;
        switch (seq) {
            case 0 -> { // footsteps walking up behind you, then a breath on your neck
                int[] steps = {0, 16, 30, 43, 55, 66, 75, 82, 87};
                for (int i = 0; i < steps.length; i++)
                    if (t == steps[i]) around(SoundEvents.GRAVEL_STEP, 180, 9 - i, -1.4, 0.5F + i * 0.08F, 0.75F);
                if (t == 92) around(ShadowRegistry.BREATH.get(), 180, 0.6, 0, 1F, 0.75F);
                if (t > 100) seq = -1;
            }
            case 1 -> { // your heart starts racing for no reason, then something screams far away
                for (int i = 0; i < 22; i++) {
                    int at = (int) (i * (14 - i * 0.45F));
                    if (t == at) ui(ShadowRegistry.HEARTBEAT.get(), 0.6F + i * 0.02F, 0.75F + i * 0.02F);
                }
                if (t == 200) around(SoundEvents.SCULK_SHRIEKER_SHRIEK, 160, 20, 0, 1F, 0.5F);
                if (t > 210) seq = -1;
            }
            case 2 -> { // whispers circling you
                if (t == 0) around(ShadowRegistry.WHISPER.get(), 120, 2, 0, 1F, 0.65F);
                if (t == 50) around(ShadowRegistry.WHISPER.get(), 240, 1.5, 0, 1F, 0.6F);
                if (t == 95) around(ShadowRegistry.BREATH.get(), 180, 0.5, 0, 1F, 0.8F);
                if (t > 110) seq = -1;
            }
            case 3 -> { // knocking. three times. closer each time
                if (t == 0 || t == 40 || t == 70) around(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 150 + t, 8 - t / 14.0, 0, 0.6F, 0.6F);
                if (t == 110) around(ShadowRegistry.STATIC.get(), 180, 1, 0, 0.6F, 0.7F);
                if (t > 120) seq = -1;
            }
            case 4 -> { // you 'join the game'... while you're already in it
                String me = mc.player.getGameProfile().getName();
                if (t == 0) mc.gui.getChat().addMessage(Component.translatable("multiplayer.player.joined", me).withStyle(ChatFormatting.YELLOW));
                if (t == 60) around(ShadowRegistry.WHISPER.get(), 180, 1.2, 0, 0.9F, 0.7F);
                if (t == 140) mc.gui.getChat().addMessage(Component.translatable("multiplayer.player.left", me).withStyle(ChatFormatting.YELLOW));
                if (t > 150) seq = -1;
            }
            case 5 -> { // something says your name
                String me = mc.player.getGameProfile().getName();
                if (t == 0) around(ShadowRegistry.WHISPER.get(), 200, 0.8, 0, 1F, 0.55F);
                if (t == 10) mc.gui.setOverlayMessage(Component.literal(me.toLowerCase() + "...").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
                if (t == 70) mc.gui.setOverlayMessage(Component.literal("turn around").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
                if (t == 72) around(ShadowRegistry.BREATH.get(), 180, 0.5, 0, 1F, 0.7F);
                if (t > 120) seq = -1;
            }
            case 6 -> { // the lights go out for a moment
                if (t == 0) ui(ShadowRegistry.STATIC.get(), 0.7F, 0.6F);
                if (t < 20) lightsOut = Math.min(1F, t / 20F);
                else if (t < 90) lightsOut = 1F;
                else lightsOut = Math.max(0F, 1F - (t - 90) / 25F);
                if (t == 30 || t == 52 || t == 70) ui(ShadowRegistry.HEARTBEAT.get(), 1F, 0.8F + t * 0.003F);
                if (t == 60) around(ShadowRegistry.BREATH.get(), 170, 1.0, 0, 1F, 0.75F);
                if (t == 88) around(ShadowRegistry.SNAP.get(), 190, 2.0, 0, 1F, 0.8F);
                if (t > 116) { lightsOut = 0F; seq = -1; }
            }
            default -> seq = -1;
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        js = -1; seq = -1; stare = 0; lightsOut = 0; rushing = false;
    }

    /** staring pulls your view into it (narrowing FOV) - you can't look away from the face */
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (stare > 0.02F) event.setFOV(event.getFOV() * (1.0 - 0.45 * stare));
    }

    // ------------------------------------------------------------------ camera shake
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float amp = 0F;
        if (js >= SCARE_AT && js < SCARE_AT + 26) amp = 9F * (1F - (js - SCARE_AT) / 26F) + 1.5F;
        if (rushing) amp = Math.max(amp, 3.5F);
        if (stare > 0.05F) {
            float t = (float) (System.currentTimeMillis() % 100000L) / 1000F;
            event.setRoll(event.getRoll() + (float) Math.sin(t * 1.3F) * 4F * stare);
            if (stare > 0.6F) amp = Math.max(amp, (stare - 0.6F) * 3F);
        }
        if (amp > 0F) {
            event.setYaw(event.getYaw() + (R.nextFloat() - 0.5F) * amp);
            event.setPitch(event.getPitch() + (R.nextFloat() - 0.5F) * amp * 0.7F);
            event.setRoll(event.getRoll() + (R.nextFloat() - 0.5F) * amp);
        }
    }

    // ------------------------------------------------------------------ screen overlay
    public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // staring: the edges close in and the screen fills with static
        if (stare > 0.02F && js < 0) {
            g.setColor(1F, 1F, 1F, Math.min(0.9F, stare * 0.9F));
            blend(); g.blit(VIGNETTE, 0, 0, w, h, 0F, 0F, 256, 256, 256, 256);
            if (stare > 0.35F) {
                g.setColor(1F, 1F, 1F, Math.min(1F, (stare - 0.35F) * 1.5F));
                blend(); g.blit(STATIC, 0, 0, w, h, R.nextInt(1024 - w / 2 > 0 ? 1024 - w / 2 : 1), R.nextInt(1024 - h / 2 > 0 ? 1024 - h / 2 : 1), w / 2, h / 2, 1024, 1024);
            }
            g.setColor(1F, 1F, 1F, 1F);
        }

        if (lightsOut > 0F) {
            g.setColor(1F, 1F, 1F, 1F);
            g.fill(0, 0, w, h, ((int) (lightsOut * 0.93F * 255) << 24));
        }

        if (js >= 0) {
            int t = js;
            // darkness creeping in during the build-up, black afterwards
            float dark = 0F;
            if (t < SCARE_AT) dark = Math.min(0.75F, t / 60F * 0.75F);
            else if (t >= SCARE_AT + SCARE_LEN) dark = Math.max(0F, 1F - (t - SCARE_AT - SCARE_LEN) / 50F);
            if (dark > 0F) g.fill(0, 0, w, h, ((int) (dark * 255) << 24));

            if (t >= SCARE_AT && t < SCARE_AT + SCARE_LEN) {
                float k = (t - SCARE_AT) + partialTick;
                g.fill(0, 0, w, h, 0xFF000000);
                // rendered frames of its face lunging at you (Blender), then it thrashes on the last frames
                int frame = (int) k;
                if (frame >= JS_FRAMES) frame = JS_FRAMES - 4 + ((t + R.nextInt(2)) % 4);
                float zoom = 1.04F + R.nextFloat() * (k > 6 ? 0.08F : 0.03F);
                int dw = (int) (Math.max(w, h * 16F / 9F) * zoom), dh = (int) (dw * 9F / 16F);
                int ox = (w - dw) / 2 + (int) ((R.nextFloat() - 0.5F) * 24F);
                int oy = (h - dh) / 2 + (int) ((R.nextFloat() - 0.5F) * 24F);
                blend(); g.blit(JS_FRAME[Math.max(0, Math.min(JS_FRAMES - 1, frame))], ox, oy, dw, dh, 0F, 0F, 768, 432, 768, 432);
                g.setColor(1F, 1F, 1F, 0.22F);
                blend(); g.blit(STATIC, 0, 0, w, h, R.nextInt(1024 - w / 2 > 0 ? 1024 - w / 2 : 1), R.nextInt(1024 - h / 2 > 0 ? 1024 - h / 2 : 1), w / 2, h / 2, 1024, 1024);
                g.setColor(1F, 1F, 1F, 1F);
                if (t == SCARE_AT + 1 || t == SCARE_AT + 2) g.fill(0, 0, w, h, 0x50FFFFFF);   // flash
            }
        }
        RenderSystem.disableBlend();
    }

    public static int jumpscareTick() { return js; }

    /** am I standing inside the black ring around its victim? */
    private static boolean inZone(Minecraft mc, ShadowEntity s) {
        Entity t = mc.level.getEntity(s.getTargetEntityId());
        return t != null && t.distanceToSqr(mc.player) <= ShadowEntity.ZONE_RADIUS * ShadowEntity.ZONE_RADIUS;
    }

    /** GuiGraphics.fill() switches blending off when it flushes, so turn it back on before every textured draw */
    private static void blend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private ClientFx() {}
}
