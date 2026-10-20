package com.mansoority.shadow.client;

import com.mansoority.shadow.FxPacket;
import com.mansoority.shadow.ShadowCinematic;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** the /shadow cinematic camera: a free camera that frames the player who ran it, with letterbox and title cards */
public final class CinematicCam {
    private static boolean active;
    private static int shot, focusId = -1, chapter = -1, chapterT, shotT, endT = -1;
    private static double pa, pb, pc, orbit;
    private static float bars;
    private static ArmorStand cam;
    private static Vec3 pos;
    private static float yaw, pitch;

    private static final String[][] CARDS = {
            {"M A N S O O R I T Y", "starring %s"},
            {"I.  IT WATCHES", "you'll only ever catch a glimpse"},
            {"II.  IT WEARS YOUR FRIENDS", "don't walk up to them"},
            {"III.  DON'T LOOK AT IT", "the longer you stare, the closer it gets"},
            {"IV.  RUN", ""},
            {"V.  YOU SHOULD SLEEP", ""},
            {"M A N S O O R I T Y", "it could be anyone"},
            {"VI.  IT WANTS A TASTE", "it always puts you back. mostly"}};

    public static boolean active() { return active; }

    public static void handle(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.sound == ShadowCinematic.OFF) {
            endT = 0;      // fade the bars out, then hand the camera back
            return;
        }
        if (!active && mc.player != null && mc.level != null) {
            cam = EntityType.ARMOR_STAND.create(mc.level);
            pos = mc.player.getEyePosition();
            yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
            active = true; endT = -1; orbit = Math.toRadians(mc.player.getYRot() + 90);
        }
        shot = p.sound; pa = p.x; pb = p.y; pc = p.z; focusId = (int) p.volume; shotT = 0;
        if (p.pitch >= 0) { chapter = (int) p.pitch; chapterT = 0; }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        if (mc.player == null || mc.level == null || cam == null) { reset(mc); return; }
        bars += ((endT >= 0 ? 0F : 1F) - bars) * 0.08F;
        if (endT >= 0 && ++endT > 30) { reset(mc); return; }
        shotT++; chapterT++;
        Vec3 P = mc.player.position();
        Vec3 eye = mc.player.getEyePosition();
        Entity f = focusId >= 0 ? mc.level.getEntity(focusId) : null;
        Vec3 F = f != null ? f.position().add(0, f.getBbHeight() * 0.62, 0) : null;
        Vec3 want, look;
        Vec3 dirPF = F != null ? F.subtract(eye).multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, mc.player.getYRot());
        Vec3 side = new Vec3(-dirPF.z, 0, dirPF.x);
        switch (shot) {
            case ShadowCinematic.ORBIT -> {
                orbit += pc;
                want = P.add(Math.cos(orbit) * pa, pb, Math.sin(orbit) * pa);
                look = eye;
            }
            case ShadowCinematic.SHOULDER -> {
                want = eye.subtract(dirPF.scale(pa)).add(0, pb - 1.0, 0).add(side.scale(pc));
                look = F != null ? F : eye.add(dirPF.scale(10));
            }
            case ShadowCinematic.WIDE -> {
                Vec3 mid = F != null ? eye.lerp(F, 0.5) : eye;
                double span = F != null ? F.distanceTo(eye) : 4;
                want = mid.add(side.scale(pa + span * 0.45)).add(0, pb, 0);
                look = mid;
            }
            case ShadowCinematic.FACE -> {
                want = eye.add(dirPF.scale(-pa)).add(0, pb, 0).add(side.scale(0.8));
                look = F != null ? eye.lerp(F, 0.35) : eye;
            }
            case ShadowCinematic.CRANE -> {
                double k = Math.min(1.0, shotT / 180.0);
                k = k * k * (3 - 2 * k);
                orbit += 0.004;
                want = P.add(Math.cos(orbit) * pa * (1 + k), pa + (pb - pa) * k, Math.sin(orbit) * pa * (1 + k));
                look = eye;
            }
            default -> { want = eye; look = eye.add(dirPF); }
        }
        // a slow, heavy camera: eases into each new position
        double ease = shotT < 3 ? 0.35 : 0.075;
        Vec3 np = pos.lerp(want, ease);
        Vec3 d = look.subtract(np);
        float wy = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90F;
        float wp = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180F / Math.PI));
        float ny = yaw + Mth.wrapDegrees(wy - yaw) * 0.2F;
        float npi = pitch + (wp - pitch) * 0.2F;
        double eh = cam.getEyeHeight();
        cam.xo = pos.x; cam.yo = pos.y - eh; cam.zo = pos.z;
        cam.xOld = cam.xo; cam.yOld = cam.yo; cam.zOld = cam.zo;
        cam.yRotO = yaw; cam.xRotO = pitch;
        cam.setPos(np.x, np.y - eh, np.z);
        cam.setYRot(ny); cam.setXRot(npi); cam.setYHeadRot(ny); cam.yHeadRotO = yaw;
        pos = np; yaw = ny; pitch = npi;
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
    }

    private static void reset(Minecraft mc) {
        active = false; chapter = -1; endT = -1; bars = 0F;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        cam = null;
    }

    public static void onLogout() { active = false; chapter = -1; cam = null; bars = 0F; }

    /** hide the hand and the whole HUD while the film is running */
    public static void onHand(RenderHandEvent e) { if (active) e.setCanceled(true); }

    public static void onGui(RenderGuiOverlayEvent.Pre e) {
        if (active && !e.getOverlay().id().getNamespace().equals("theshadow")) e.setCanceled(true);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (!active && bars <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        int bar = (int) (h * 0.12F * bars);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (chapter >= 0 && chapter < CARDS.length) {
            int t = chapterT;
            float a = t < 20 ? t / 20F : t < 90 ? 1F : Math.max(0F, 1F - (t - 90) / 30F);
            if (chapter == 6 && t > 60) {     // end card: fade to black
                int fade = (int) (Math.min(1F, (t - 60) / 80F) * 255);
                g.fill(0, 0, w, h, fade << 24);
                a = Math.min(1F, t / 20F);
            }
            if (a > 0.02F) {
                RenderSystem.enableBlend();
                int alpha = Math.max(4, (int) (a * 255)) << 24;
                String title = CARDS[chapter][0];
                String sub = String.format(CARDS[chapter][1], mc.player != null ? mc.player.getGameProfile().getName() : "");
                g.pose().pushPose();
                float sc = chapter == 0 || chapter == 6 ? 3.2F : 2.2F;
                g.pose().translate(w / 2F, h * 0.42F, 0);
                g.pose().scale(sc, sc, 1F);
                g.drawCenteredString(mc.font, Component.literal(title), 0, 0, alpha | 0xE8E0D8);
                g.pose().popPose();
                if (!sub.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(w / 2F, h * 0.42F + 34, 0);
                    g.pose().scale(1.2F, 1.2F, 1F);
                    g.drawCenteredString(mc.font, Component.literal(sub), 0, 0, alpha | 0xB0332E);
                    g.pose().popPose();
                }
            }
        }
    }

    private CinematicCam() {}
}
