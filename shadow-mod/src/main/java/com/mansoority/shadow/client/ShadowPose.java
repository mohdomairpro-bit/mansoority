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

    static void apply(ShadowMesh m, ShadowEntity e, float age, float pt, net.minecraft.world.phys.Vec3 cam) {
        for (ShadowMesh.Part p : m.parts) p.reset();
        ShadowMesh.Part head = m.get("head"), jaw = m.get("jaw"), jawR = m.get("jawR"), neck = m.get("neck"), chest = m.get("chest");
        ShadowMesh.Part armL = m.get("armL"), armR = m.get("armR"), foreL = m.get("foreL"), foreR = m.get("foreR");
        ShadowMesh.Part handL = m.get("handL"), handR = m.get("handR");
        ShadowMesh.Part legL = m.get("legL"), legR = m.get("legR"), shinL = m.get("shinL"), shinR = m.get("shinR");
        int id = e.getId();
        float mouth = 0F;   // how far the face is split open
        float stare = Math.min(1F, e.getStare() / (float) ShadowEntity.STARE_LIMIT);

        // it always looks AT you: the head tips down/up toward the camera
        double headY = e.getY() + 2.15 * ShadowEntity.MODEL_SCALE;
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

        // twitches: head snaps sideways, fingers spasm, jaw clacks
        int win = (int) (age / 6F);
        float local = age - win * 6F;
        if (stare <= 0F && hash(win, id) < 0.24F && local < 2.5F) {
            head.zRot += (hash(win, id + 1) - 0.5F) * 1.4F;
            head.yRot += (hash(win, id + 2) - 0.5F) * 0.8F;
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
            float k = Math.min(1F, rush / 5F);
            head.xRot += 0.3F * k;
            mouth += 1.4F * k;
            armL.xRot -= 1.3F * k; armR.xRot -= 1.3F * k;
            handL.xRot -= 0.6F * k; handR.xRot -= 0.6F * k;
            float run = age * 1.5F;
            legL.xRot += Mth.cos(run) * 0.9F * k;
            legR.xRot += Mth.cos(run + Mth.PI) * 0.9F * k;
            shinL.xRot += Math.max(0F, Mth.sin(run)) * 0.7F * k;
            shinR.xRot += Math.max(0F, Mth.sin(run + Mth.PI)) * 0.7F * k;
            head.zRot += (hash((int) age, id) - 0.5F) * 0.35F;
        }

        // the lower face splits down the middle and both halves swing out sideways
        mouth = Mth.clamp(mouth, 0F, 1.75F);
        jaw.yRot -= mouth; jawR.yRot += mouth;
        jaw.xRot += mouth * 0.12F; jawR.xRot += mouth * 0.12F;

        // vanishing: everything spasms
        int vanish = e.getVanish();
        if (vanish > 0) {
            float v = vanish / 12F;
            ShadowMesh.Part[] all = {head, jaw, armL, armR, foreL, foreR, chest, neck};
            for (int i = 0; i < all.length; i++) {
                all[i].xRot += (hash((int) age, i * 31 + id) - 0.5F) * 1.1F * v;
                all[i].zRot += (hash((int) age + 7, i * 17 + id) - 0.5F) * 1.1F * v;
            }
        }
    }

    private ShadowPose() {}
}
