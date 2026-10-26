package com.mansoority.shadow.client;

import com.mansoority.shadow.FxPacket;
import com.mansoority.shadow.HollowPlace;
import com.mansoority.shadow.MansoorityBoss;
import com.mansoority.shadow.ShadowRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Vector3f;

/** the End fight on your screen: shockwaves, warning circles, its lines, the dark, being knocked flat, its music */
public final class BossFx {
    static final String[] LINES = {"I'LL DESTROY YOU...", "...THIS WORLD IS MINE", "KNEEL.", "YOUR WORLD IS MINE NOW", "AGAIN.",
            "I CAN TASTE YOU", "TOO SLOW", "something is watching the dragon", "STAY WITH ME", "ONE MORE"};
    private record Ring(Vec3 c, float speed, float max, int start) {}
    private record Mark(Vec3 c, float r, int until) {}
    private record Eyes(Vec3 at, float yaw, int until) {}
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Eyes> EYES = new ArrayList<>();
    private static int tick, line = -1, lineT, knock, flood, sprayT, sprayId = -1, hollow;

    static void handle(FxPacket p) {
        switch (p.kind) {
            case FxPacket.RING_WAVE -> RINGS.add(new Ring(new Vec3(p.x, p.y, p.z), p.volume, p.pitch, tick));
            case FxPacket.TELEGRAPH -> MARKS.add(new Mark(new Vec3(p.x, p.y, p.z), p.volume, tick + p.sound));
            case FxPacket.PILLAR_EYES -> EYES.add(new Eyes(new Vec3(p.x, p.y, p.z), p.volume, tick + p.sound));
            case FxPacket.LINE -> { line = p.sound; lineT = 0; }
            case FxPacket.KNOCKDOWN -> knock = p.sound;
            case FxPacket.FLOOD -> flood = p.sound;
            case FxPacket.SHAKE -> ClientFx.kick(p.volume * 3F);
            case FxPacket.SPRAY -> { sprayT = p.sound; sprayId = (int) p.volume; }
            case FxPacket.HOLLOW -> hollow = p.sound;
            default -> {}
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { RINGS.clear(); MARKS.clear(); EYES.clear(); stopMusic(); return; }
        tick++;
        if (line >= 0 && ++lineT > 80) line = -1;
        if (knock > 0) knock--;
        if (flood > 0) flood--;
        if (hollow > 0) { hollow--; if (hollow % 20 == 0) mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ShadowRegistry.HEARTBEAT.get(), 1F + (600 - hollow) / 900F, 1F)); }
        var r = mc.level.random;
        // the shockwave: a wall of dust rolling outward along the ground
        for (int i = RINGS.size() - 1; i >= 0; i--) {
            Ring g = RINGS.get(i);
            float rad = 2F + (tick - g.start) * g.speed;
            if (rad > g.max) { RINGS.remove(i); continue; }
            int n = (int) Math.min(90, rad * 2.2F);
            for (int k = 0; k < n; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                double x = g.c.x + Math.cos(a) * rad, z = g.c.z + Math.sin(a) * rad;
                BlockPos below = BlockPos.containing(x, g.c.y - 0.5, z);
                var st = mc.level.getBlockState(below);
                if (st.isAir()) continue;
                mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), x, g.c.y + 0.1, z, Math.cos(a) * 0.15, 0.25 + r.nextDouble() * 0.2, Math.sin(a) * 0.15);
                if (k % 4 == 0) mc.level.addParticle(ParticleTypes.LARGE_SMOKE, x, g.c.y + 0.3, z, 0, 0.05, 0);
            }
        }
        // where it's about to land: a red circle, pulsing faster as it comes
        for (int i = MARKS.size() - 1; i >= 0; i--) {
            Mark m = MARKS.get(i);
            if (tick > m.until) { MARKS.remove(i); continue; }
            int left = m.until - tick;
            if (left > 10 && tick % (left > 30 ? 4 : 2) != 0) continue;
            for (int k = 0; k < 40; k++) {
                double a = k / 40.0 * Math.PI * 2;
                mc.level.addParticle(new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.05F), 2.0F), m.c.x + Math.cos(a) * m.r, m.c.y + 0.15, m.c.z + Math.sin(a) * m.r, 0, 0, 0);
            }
        }
        for (int i = EYES.size() - 1; i >= 0; i--) if (tick > EYES.get(i).until) EYES.remove(i);
        // the stomach emptying itself
        if (sprayT > 0) {
            sprayT--;
            Entity b = mc.level.getEntity(sprayId);
            if (b != null) {
                Vec3 dir = Vec3.directionFromRotation(0, b.getYRot());
                Vec3 from = b.position().add(dir.scale(2.2)).add(0, 6.5, 0);
                for (int k = 0; k < 10; k++) {
                    Vec3 v = new Vec3(dir.x, -0.3, dir.z).normalize().scale(0.7 + r.nextDouble() * 0.5).add((r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.3) * 0.25, (r.nextDouble() - 0.5) * 0.3);
                    mc.level.addParticle(ShadowRegistry.BILE.get(), from.x, from.y, from.z, v.x, v.y, v.z);
                }
            }
        }
        music(mc);
    }

    // ---------------------------------------------------------------- music: one loop per phase, silence when it "dies"
    private static Loop playing; private static int playingPhase;

    private static final class Loop extends AbstractTickableSoundInstance {
        float target = 1F;
        Loop(SoundEvent s) {
            super(s, SoundSource.MUSIC, net.minecraft.util.RandomSource.create());
            this.looping = true; this.delay = 0; this.volume = 0.01F; this.relative = true;
            this.attenuation = Attenuation.NONE;
        }
        @Override public void tick() {
            this.volume += (target - this.volume) * 0.05F;
            if (target <= 0F && this.volume < 0.02F) this.stop();
        }
        void fadeOut() { target = 0F; }
    }

    private static void music(Minecraft mc) {
        MansoorityBoss boss = null;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof MansoorityBoss b && b.distanceToSqr(mc.player) < 150 * 150 && !b.showcase) { boss = b; break; }
        int want = boss == null || boss.has(MansoorityBoss.F_INTRO) || boss.has(MansoorityBoss.F_FAKEDEAD) ? 0 : boss.getPhase();
        if (want == playingPhase && (playing == null) == (want == 0)) {
            if (playing != null) mc.getMusicManager().stopPlaying();
            return;
        }
        if (playing != null) { playing.fadeOut(); playing = null; }
        playingPhase = want;
        if (want > 0) {
            playing = new Loop((want == 1 ? ShadowRegistry.MUSIC_1 : want == 2 ? ShadowRegistry.MUSIC_2 : ShadowRegistry.MUSIC_3).get());
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(playing);
        }
    }

    private static void stopMusic() {
        if (playing != null) { Minecraft.getInstance().getSoundManager().stop(playing); playing = null; playingPhase = 0; }
    }

    // ---------------------------------------------------------------- its eyes, open in the pillars (drawn glowing, through the dark)
    private static final net.minecraft.resources.ResourceLocation EYE_TEX = new net.minecraft.resources.ResourceLocation(com.mansoority.shadow.TheShadow.MODID, "textures/entity/pillar_eyes.png");

    static void renderLevel(net.minecraftforge.client.event.RenderLevelStageEvent e) {
        if (e.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES || EYES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        var ps = e.getPoseStack();
        var buf = mc.renderBuffers().bufferSource();
        var vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.eyes(EYE_TEX));
        for (Eyes ey : EYES) {
            int left = ey.until - tick, age = 400 - left;
            float open = Mth.clamp(Math.min(age, left) / 20F, 0F, 1F);
            int blinkWin = (tick / 50) % 4, local = tick % 50;
            float lid = blinkWin == 2 && local < 6 ? Mth.sin(local / 6F * (float) Math.PI) : 0F;   // now and then it blinks
            float hgt = 1.2F * open * (1F - lid * 0.95F);
            if (hgt < 0.02F) continue;
            ps.pushPose();
            ps.translate(ey.at.x - cam.x, ey.at.y - cam.y, ey.at.z - cam.z);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-ey.yaw));
            var m = ps.last().pose();
            float w = 3.2F;
            int a = (int) (255 * open);
            vc.vertex(m, -w, -hgt, 0).color(255, 255, 255, a).uv(0F, 1F).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 0, 1).endVertex();
            vc.vertex(m, w, -hgt, 0).color(255, 255, 255, a).uv(1F, 1F).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 0, 1).endVertex();
            vc.vertex(m, w, hgt, 0).color(255, 255, 255, a).uv(1F, 0F).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 0, 1).endVertex();
            vc.vertex(m, -w, hgt, 0).color(255, 255, 255, a).uv(0F, 0F).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 0, 1).endVertex();
            ps.popPose();
        }
        buf.endBatch(net.minecraft.client.renderer.RenderType.eyes(EYE_TEX));
    }

    // ---------------------------------------------------------------- camera: thrown to the ground
    static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (knock > 0) {
            float k = Math.min(1F, knock / 15F);
            e.setRoll(e.getRoll() + 72F * k);
            e.setPitch(Mth.clamp(e.getPitch() + 25F * k, -90F, 90F));
        }
    }

    static void fog(ViewportEvent.RenderFog e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && HollowPlace.in(mc.player)) {
            e.setNearPlaneDistance(0F); e.setFarPlaneDistance(26F);
            e.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE); e.setCanceled(true);
        }
    }

    static void fogColor(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && HollowPlace.in(mc.player)) { e.setRed(0.01F); e.setGreen(0.0F); e.setBlue(0.0F); }
    }

    // ---------------------------------------------------------------- overlay: the dark, its words
    static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (flood > 0) {
            float a = Math.min(1F, flood / 15F) * Math.min(1F, (120 - flood + 4) / 10F);
            g.fill(0, 0, w, h, ((int) (a * 0.96F * 255) << 24));
            // only its eyes: two red points somewhere in the black
            int ex = w / 2 + (int) (Mth.sin(tick * 0.05F) * w * 0.25F), ey = (int) (h * 0.35F);
            int ga = (int) (a * 255);
            for (int s = -1; s <= 1; s += 2) {
                g.fill(ex + s * 14 - 4, ey - 2, ex + s * 14 + 4, ey + 2, (ga << 24) | 0xFF2A1A);
                g.fill(ex + s * 14 - 1, ey - 1, ex + s * 14 + 1, ey + 1, (ga << 24) | 0xFFE0C0);
            }
        }
        if (knock > 0) g.fill(0, 0, w, h, ((int) (Math.min(1F, knock / 20F) * 0.35F * 255) << 24));
        if (line >= 0 && line < LINES.length) {
            float a = lineT < 6 ? lineT / 6F : lineT > 64 ? Math.max(0F, (80 - lineT) / 16F) : 1F;
            if (a > 0.02F) {
                String s = LINES[line];
                float sc = line == 7 ? 1.4F : 2.6F;
                var r = mc.level != null ? mc.level.random : net.minecraft.util.RandomSource.create();
                int alpha = Math.max(4, (int) (a * 255)) << 24;
                for (int pass = 0; pass < 3; pass++) {         // a broken signal: red and pale copies jittering apart
                    g.pose().pushPose();
                    g.pose().translate(w / 2F + (pass == 0 ? 0 : (r.nextFloat() - 0.5F) * 6F), h * 0.68F + (pass == 0 ? 0 : (r.nextFloat() - 0.5F) * 3F), 0);
                    g.pose().scale(sc, sc, 1F);
                    int col = pass == 0 ? 0xE8E0D8 : pass == 1 ? 0xC0201A : 0x5A1010;
                    g.drawCenteredString(mc.font, Component.literal(s), 0, 0, (pass == 0 ? alpha : (alpha >>> 1 & 0xFF000000)) | col);
                    g.pose().popPose();
                }
            }
        }
    }

    private BossFx() {}
}
