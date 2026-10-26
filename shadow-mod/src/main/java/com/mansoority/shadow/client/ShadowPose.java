package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import net.minecraft.util.Mth;

/**
 * Animation for the mesh. Model space: y up, the creature faces +z.
 * x-rotation: + tips the top of a part forward / swings a hanging limb backward.
 * z-rotation: + swings a hanging limb toward +x.  The 'L' limbs are on the -x side.
 */
final class ShadowPose {
    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    /** the spider crouch: k = how far folded down, sc = scuttle amount */
    static void spider(ShadowMesh m, float k, float sc, float age, int id) {
        ShadowMesh.Part hips = m.get("hips"), chest = m.get("chest"), neck = m.get("neck"), head = m.get("head");
        ShadowMesh.Part armL = m.get("armL"), armR = m.get("armR"), foreL = m.get("foreL"), foreR = m.get("foreR");
        ShadowMesh.Part legL = m.get("legL"), legR = m.get("legR"), shinL = m.get("shinL"), shinR = m.get("shinR");
        hips.ty -= 0.55F * k;
        hips.xRot += 0.85F * k; chest.xRot += 0.45F * k;
        neck.xRot -= 1.05F * k; head.xRot -= 0.85F * k;
        legL.xRot -= 0.85F * k; legR.xRot -= 0.85F * k;
        legL.zRot -= 0.95F * k; legR.zRot += 0.95F * k;
        shinL.zRot += 0.9F * k; shinR.zRot -= 0.9F * k;
        armL.zRot -= 0.75F * k; armR.zRot += 0.75F * k;
        armL.xRot += 0.35F * k; armR.xRot += 0.35F * k;
        foreL.zRot += 0.5F * k; foreR.zRot -= 0.5F * k;
        if (sc > 0F) {
            float run = age * 2.6F;
            legL.xRot += Mth.sin(run) * 0.45F * sc;  armR.xRot += Mth.sin(run) * 0.45F * sc;
            legR.xRot += Mth.sin(run + Mth.PI) * 0.45F * sc; armL.xRot += Mth.sin(run + Mth.PI) * 0.45F * sc;
            shinL.xRot += Math.max(0F, Mth.cos(run)) * 0.5F * sc; shinR.xRot += Math.max(0F, Mth.cos(run + Mth.PI)) * 0.5F * sc;
            hips.zRot += Mth.sin(run * 2F) * 0.05F * sc;
            head.zRot += (hash((int) age, id) - 0.5F) * 0.2F * sc;
        }
    }

    /** Blender clips when they're available; the hand-coded poses below are the fallback */
    static void apply(ShadowMesh m, ShadowEntity e, float age, float pt, net.minecraft.world.phys.Vec3 cam) {
        for (ShadowMesh.Part p : m.parts) p.reset();
        ShadowAnim A = ShadowAnim.get();
        if (A != null && A.clips.containsKey("idle")) { animated(A, m, e, age, pt, cam); return; }
        procedural(m, e, age, pt, cam);
    }

    private static void animated(ShadowAnim A, ShadowMesh m, ShadowEntity e, float age, float pt, net.minecraft.world.phys.Vec3 cam) {
        if (!(e.animState instanceof ShadowAnim.State)) e.animState = A.newState();
        ShadowAnim.State st = (ShadowAnim.State) e.animState;
        int id = e.getId();
        float stare = e.smoothStare;
        int rush = e.getRush(), gr = e.getGrab();
        double moved = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        String clip = "idle"; float drive = -1F, speed = 1F;
        if (e.getVanish() > 0) { clip = st.cur; speed = 0.4F; }
        else if (gr > 0) {
            if (gr < 24) { clip = "grab"; drive = gr + pt; }
            else if (e.getGrabMode() == ShadowEntity.GRAB_LICK) { clip = "lick"; speed = 1F; }
            else { clip = "scuttle"; speed = stride(A, "scuttle", moved, 1.0F); }
        } else if (rush > 0) {
            float r = rush + pt;
            float g = r - ShadowEntity.RUSH_GO;
            if (r < ShadowEntity.RUSH_STILL) { clip = "idle"; speed = 0.2F; }
            else if (g < 0) { clip = "fold"; drive = (r - ShadowEntity.RUSH_STILL) * 63F / ShadowEntity.RUSH_FOLD; }
            else { clip = "scuttle"; speed = moved < 0.01 ? 0F : stride(A, "scuttle", moved, 1.0F); }
        } else if (e.getScuttle() > 0) { clip = "scuttle"; speed = stride(A, "scuttle", moved, 1.0F); }
        else if (stare > 0.03F) {
            switch (e.getVariant()) {
                case 1 -> { clip = "crawl"; speed = moved > 0.005 ? stride(A, "crawl", moved, 0.8F) : 0.12F; }
                case 2 -> { clip = moved > 0.005 ? "walk" : "idle"; speed = moved > 0.005 ? stride(A, "walk", moved, 1F) : 0.1F; }
                case 3 -> { clip = "neck"; drive = stare * 79F; }
                default -> { clip = "stareA"; drive = stare * 79F; }
            }
        } else if (e.getStage() == 0) { clip = "peek"; }
        else if (moved > 0.02) { clip = "walk"; speed = stride(A, "walk", moved, 1F); }
        A.play(st, clip, drive, speed, age);
        A.apply(st, m);

        ShadowMesh.Part head = m.get("head"), jaw = m.get("jaw"), jawR = m.get("jawR"), neck = m.get("neck");
        // it always looks AT you
        double headY = e.getY() + 1.905 * ShadowEntity.MODEL_SCALE;
        double horiz = Math.max(1.0, Math.sqrt((cam.x - e.getX()) * (cam.x - e.getX()) + (cam.z - e.getZ()) * (cam.z - e.getZ())));
        float look = Mth.clamp((float) Math.atan2(headY - cam.y, horiz), -0.5F, 1.25F);
        boolean crouched = clip.equals("peek") || clip.equals("crawl") || clip.equals("scuttle") || clip.equals("fold");
        neck.xRot += look * (crouched ? 0.2F : 0.45F);
        head.xRot += look * (crouched ? 0.25F : 0.55F);
        // the face: barely parts when it breathes, splits wide as you stare / when it comes for you
        float mouth = (Mth.sin(age * 0.06F) * 0.5F + 0.5F) * 0.035F;
        mouth += stare * stare * 1.6F;
        if (stare > 0.6F) {
            float j = (stare - 0.6F) * 2.5F;
            head.zRot += (hash((int) age, id) - 0.5F) * 0.35F * j;
            head.xRot += (hash((int) age, id + 9) - 0.5F) * 0.25F * j;
            mouth += hash((int) age, id + 11) * 0.3F * j;
        }
        if (rush > 0) mouth += Mth.clamp((rush - ShadowEntity.RUSH_STILL * 0.5F) / 40F, 0F, 1.5F);
        if (gr > 0 && e.getGrabMode() == ShadowEntity.GRAB_LICK && gr > 50) mouth += 0.7F + 0.6F * Math.abs(Mth.sin((gr + pt) * 0.21F));
        if (e.getScuttle() > 0) mouth += 1.0F;
        // rare little twitches so it's never perfectly smooth
        int win = (int) (age / 6F);
        float local = age - win * 6F;
        if (stare < 0.05F && rush <= 0 && hash(win, id) < 0.06F && local < 2.5F) {
            float tw = Mth.sin(local / 2.5F * Mth.PI);
            head.zRot += (hash(win, id + 1) - 0.5F) * 0.8F * tw;
            mouth += hash(win, id + 3) * 0.25F * tw;
        }
        mouth = Mth.clamp(mouth, 0F, 1.75F);
        jaw.yRot -= mouth; jawR.yRot += mouth;
        jaw.xRot += mouth * 0.12F; jawR.xRot += mouth * 0.12F;
        int vanish = e.getVanish();
        if (vanish > 0) {
            float v = Math.min(1F, vanish / 12F);
            for (ShadowMesh.Part p : m.parts) {
                int i = p.name.hashCode();
                p.xRot += (hash((int) age, i * 31 + id) - 0.5F) * 0.9F * v;
                p.zRot += (hash((int) age + 7, i * 17 + id) - 0.5F) * 0.9F * v;
            }
        }
    }

    /** playback rate that makes the feet keep pace with how fast it's really moving (no skating) */
    private static float stride(ShadowAnim A, String clip, double movedBlocksPerTick, float fallback) {
        ShadowAnim.Clip c = A.clips.get(clip);
        if (c == null || c.speed <= 0F) return fallback;
        return (float) Math.max(0.25, Math.min(3.0, movedBlocksPerTick / (c.speed * ShadowEntity.MODEL_SCALE)));
    }

    private static void procedural(ShadowMesh m, ShadowEntity e, float age, float pt, net.minecraft.world.phys.Vec3 cam) {
        ShadowMesh.Part head = m.get("head"), jaw = m.get("jaw"), jawR = m.get("jawR"), neck = m.get("neck"), chest = m.get("chest");
        ShadowMesh.Part armL = m.get("armL"), armR = m.get("armR"), foreL = m.get("foreL"), foreR = m.get("foreR");
        ShadowMesh.Part handL = m.get("handL"), handR = m.get("handR");
        ShadowMesh.Part legL = m.get("legL"), legR = m.get("legR"), shinL = m.get("shinL"), shinR = m.get("shinR");
        int id = e.getId();
        float mouth = 0F;   // how far the face is split open
        float stare = e.smoothStare;

        // it always looks AT you: the head tips down/up toward the camera
        double headY = e.getY() + 1.905 * ShadowEntity.MODEL_SCALE;
        double horiz = Math.max(1.0, Math.sqrt((cam.x - e.getX()) * (cam.x - e.getX()) + (cam.z - e.getZ()) * (cam.z - e.getZ())));
        float look = (float) Math.atan2(headY - cam.y, horiz);           // + = you are below it
        look = Mth.clamp(look, -0.5F, 1.25F);
        neck.xRot += look * 0.45F;
        head.xRot += look * 0.55F;

        // slow, laboured breathing; the jaw hangs slightly open
        float breathe = Mth.sin(age * 0.06F);
        chest.xRot += breathe * 0.025F;
        mouth += (breathe * 0.5F + 0.5F) * 0.035F;   // barely parts as it breathes
        armL.zRot += Mth.sin(age * 0.045F) * 0.03F;
        armR.zRot -= Mth.sin(age * 0.045F + 1.3F) * 0.03F;
        // never quite still: weight shifts, the head drifts like it's listening, fingers curl and uncurl one hand at a time
        ShadowMesh.Part hips = m.get("hips");
        float sway = Mth.sin(age * 0.021F + id);
        hips.zRot += sway * 0.035F; legL.zRot -= sway * 0.03F; legR.zRot -= sway * 0.03F;
        head.zRot += Mth.sin(age * 0.013F + id * 0.7F) * 0.22F;
        head.yRot += Mth.sin(age * 0.009F + id) * 0.15F;
        handL.xRot += (Mth.sin(age * 0.05F) * 0.5F + 0.5F) * 0.35F;
        handR.xRot += (Mth.sin(age * 0.037F + 2F) * 0.5F + 0.5F) * 0.35F;

        // glimpse: half behind cover, leaning out, head tipped all the way over sideways to look at you
        if (e.getStage() == 0 && e.getRush() <= 0) {
            // lurking: folded low to the ground like it's trying to hide, only the head lifted to watch you
            float side = (id & 1) == 0 ? 1F : -1F;
            float w = 0.65F * (1F - stare * 0.6F);
            spider(m, w, 0F, age, id);
            head.zRot += 0.45F * side * (1F - stare);
        }
        // flee / drag: down on all fours, scuttling
        if (e.getScuttle() > 0) spider(m, Math.min(1F, e.getScuttle() / 8F), 1F, age, id);
        // the grab: one long arm reaching forward with you in its fingers; licking = head bent down to you, face splitting
        int gr = e.getGrab();
        if (gr > 0) {
            float reach = Math.min(1F, (gr + pt) / 20F);
            armR.xRot -= 1.35F * reach; foreR.xRot -= 0.35F * reach; handR.xRot += 0.9F * reach;
            armR.zRot += 0.2F * reach;
            if (e.getGrabMode() == ShadowEntity.GRAB_LICK) {
                float lick = gr < 55 ? 0F : gr < 125 ? 1F : Math.max(0F, 1F - (gr - 125) / 20F);
                armR.xRot -= 0.9F * Mth.clamp((gr - 22) / 33F, 0F, 1F) * (gr < 125 ? 1F : lick);
                neck.xRot += 0.35F * lick; head.xRot += 0.35F * lick;
                mouth += lick * (0.7F + 0.6F * Math.abs(Mth.sin((gr + pt) * 0.21F)));
                head.zRot += Mth.sin((gr + pt) * 0.21F) * 0.25F * lick;
            }
        }

        // twitches: head snaps sideways, fingers spasm, jaw clacks
        int win = (int) (age / 6F);
        float local = age - win * 6F;
        if (stare < 0.05F && e.getStage() != 0 && hash(win, id) < 0.12F && local < 2.5F) {
            float tw = Mth.sin(local / 2.5F * Mth.PI);        // eases in and out instead of snapping
            head.zRot += (hash(win, id + 1) - 0.5F) * 0.9F * tw;
            head.yRot += (hash(win, id + 2) - 0.5F) * 0.5F * tw;
            mouth += hash(win, id + 3) * 0.35F;
            handL.xRot += (hash(win, id + 4) - 0.5F) * 0.9F;
            handR.xRot += (hash(win, id + 5) - 0.5F) * 0.9F;
        }

        // being watched: the head locks on, then slowly tips over sideways, the jaw unhinges, arms open wide
        if (stare > 0F) {
            float s = stare;
            // first it goes completely still and leans in to look at you, then the head slowly rolls over
            float lean = Math.min(1F, s * 2.5F);
            chest.xRot += lean * 0.15F;
            neck.xRot += lean * 0.15F;
            head.zRot += Math.max(0F, s - 0.25F) * 1.1F;
            mouth += s * s * 1.6F;   // unhinges far past what a jaw should do
            // arms creep up and forward toward you, fingers opening
            armL.xRot -= s * 1.1F; armR.xRot -= s * 1.1F;
            armL.zRot -= s * 0.45F; armR.zRot += s * 0.45F;
            foreL.xRot -= s * 0.5F; foreR.xRot -= s * 0.5F;
            handL.xRot -= s * 0.7F; handR.xRot -= s * 0.7F;
            if (s > 0.6F) {
                float j = (s - 0.6F) * 2.5F;
                head.zRot += (hash((int) age, id) - 0.5F) * 0.5F * j;
                head.xRot += (hash((int) age, id + 9) - 0.5F) * 0.35F * j;
                mouth += hash((int) age, id + 11) * 0.3F * j;
            }
        }

        // the chase: arms thrown forward, head down, jaw wide, legs pumping
        int rush = e.getRush();
        if (rush > 0) {
            float r = e.smoothRush + pt;
            ShadowMesh.Part hips2 = m.get("hips");
            // 1) standing far off, completely still. the head slowly tips over and the face starts to part
            float pre = Math.min(1F, r / ShadowEntity.RUSH_STILL);
            head.zRot += pre * 0.7F;
            mouth += pre * 0.35F;
            // 2) the turn: snaps down into a spider crouch in jerks, bones cracking
            float kRaw = Mth.clamp((r - ShadowEntity.RUSH_STILL) / ShadowEntity.RUSH_FOLD, 0F, 1F);
            float k = (float) Math.floor(kRaw * 6F) / 6F + (kRaw * 6F - (float) Math.floor(kRaw * 6F)) * 0.25F / 6F;   // stepwise
            float jolt = kRaw > 0F && kRaw < 1F ? (hash((int) r / 3, id) - 0.5F) * 0.35F : 0F;
            head.zRot -= k * 0.7F;
            hips2.ty -= 0.55F * k;
            hips2.xRot += 0.85F * k + jolt;
            chest.xRot += 0.45F * k - jolt;
            neck.xRot -= 1.05F * k;
            head.xRot -= 0.85F * k;
            head.zRot += jolt * 1.2F;
            mouth += 1.2F * k;
            legL.xRot -= 0.85F * k; legR.xRot -= 0.85F * k;
            legL.zRot -= 0.95F * k; legR.zRot += 0.95F * k;
            shinL.zRot += 0.9F * k; shinR.zRot -= 0.9F * k;
            armL.zRot -= 0.75F * k; armR.zRot += 0.75F * k;
            armL.xRot += 0.35F * k + jolt; armR.xRot += 0.35F * k - jolt;
            foreL.zRot += 0.5F * k; foreR.zRot -= 0.5F * k;
            // 3) the scuttle: diagonal pairs, fast and twitchy (holds still during the mid-way freeze)
            float g = r - ShadowEntity.RUSH_GO;
            float sc = g <= 0F || (g > 18F && g < 30F) ? 0F : Math.min(1F, g / 6F);
            float run = age * 2.6F;
            legL.xRot += Mth.sin(run) * 0.45F * sc;  armR.xRot += Mth.sin(run) * 0.45F * sc;
            legR.xRot += Mth.sin(run + Mth.PI) * 0.45F * sc; armL.xRot += Mth.sin(run + Mth.PI) * 0.45F * sc;
            shinL.xRot += Math.max(0F, Mth.cos(run)) * 0.5F * sc; shinR.xRot += Math.max(0F, Mth.cos(run + Mth.PI)) * 0.5F * sc;
            hips2.zRot += Mth.sin(run * 2F) * 0.05F * sc;
            head.zRot += (hash((int) age, id) - 0.5F) * 0.2F * sc;
        }

        // the lower face splits down the middle and both halves swing out sideways
        mouth = Mth.clamp(mouth, 0F, 1.75F);
        jaw.yRot -= mouth; jawR.yRot += mouth;
        jaw.xRot += mouth * 0.12F; jawR.xRot += mouth * 0.12F;

        // vanishing: everything spasms
        int vanish = e.getVanish();
        if (vanish > 0) {
            float v = Math.min(1F, vanish / 12F);
            ShadowMesh.Part[] all = {head, jaw, armL, armR, foreL, foreR, chest, neck};
            for (int i = 0; i < all.length; i++) {
                all[i].xRot += (hash((int) age, i * 31 + id) - 0.5F) * 1.1F * v;
                all[i].zRot += (hash((int) age + 7, i * 17 + id) - 0.5F) * 1.1F * v;
            }
        }
    }

    private ShadowPose() {}
}
