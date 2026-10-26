package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.ShadowRegistry;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * strings of spit across its mouth: they stretch as the face splits open, sag, sway, and snap - then the pieces fall.
 * drawn in model space with the creature, plus drops falling off the tongue.
 */
final class Saliva {
    private static final ResourceLocation WHITE = new ResourceLocation(TheShadow.MODID, "textures/entity/white.png");
    // anchor points (model metres, bone-local rest positions): left/right petal tips, upper lip, tongue tip
    private record Anchor(String bone, float x, float y, float z) {}
    private static final Anchor PETAL_L = new Anchor("jaw", -0.030F, 1.76F, 0.655F), PETAL_R = new Anchor("jawR", 0.030F, 1.76F, 0.655F);
    private static final Anchor PETAL_L2 = new Anchor("jaw", -0.012F, 1.80F, 0.668F), PETAL_R2 = new Anchor("jawR", 0.012F, 1.80F, 0.668F);
    private static final Anchor LIP_L = new Anchor("head", -0.045F, 1.87F, 0.650F), LIP_R = new Anchor("head", 0.045F, 1.87F, 0.650F), LIP_M = new Anchor("head", 0F, 1.875F, 0.664F);
    private static final Anchor[][] PAIRS = {{PETAL_L, PETAL_R}, {PETAL_L2, PETAL_R2}, {LIP_L, PETAL_L}, {LIP_R, PETAL_R}, {LIP_M, null}, {PETAL_L, null}};

    static final class State {
        final float[] rest = new float[PAIRS.length];       // relaxed length, 0 = not formed
        final float[] limit = new float[PAIRS.length];      // how far it can stretch before it snaps
        int lastDrip;
    }

    private static Vector3f at(ShadowMesh m, Anchor a, Vector3f out) {
        ShadowMesh.Part p = m.get(a.bone);
        if (p == null) return null;
        return ShadowSkeleton.matrices(m)[p.index].transformPosition(a.x, a.y, a.z, out);
    }

    /** the tip of the tongue (end of its last segment) */
    static Vector3f tongueTip(ShadowMesh m, Vector3f out) {
        ShadowMesh.Part t5 = m.get("tongue5"), t4 = m.get("tongue4");
        if (t5 == null || t4 == null) return null;
        float dx = t5.px - t4.px, dy = t5.py - t4.py, dz = t5.pz - t4.pz;
        return ShadowSkeleton.matrices(m)[t5.index].transformPosition(t5.px + dx * 0.9F, t5.py + dy * 0.9F, t5.pz + dz * 0.9F, out);
    }

    /** how far out the tongue is (0..1), from where the tip sits relative to the lips */
    static float tongueOut(ShadowMesh m) {
        Vector3f tip = tongueTip(m, new Vector3f()), lip = at(m, LIP_M, new Vector3f());
        if (tip == null || lip == null) return 0F;
        return Math.max(0F, Math.min(1F, (tip.distance(lip) - 0.06F) / 0.3F));
    }

    static void render(ShadowEntity e, ShadowMesh m, PoseStack pose, MultiBufferSource buf, int light, float age,
                       double ex, double ey, double ez, float yaw, float scale) {
        if (m.get("jaw") == null) return;
        if (!(e.salivaState instanceof State)) e.salivaState = new State();
        State st = (State) e.salivaState;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        Matrix4f mat = pose.last().pose(); Matrix3f nm = pose.last().normal();
        Vector3f a = new Vector3f(), b = new Vector3f(), tip = tongueTip(m, new Vector3f());
        float out = tongueOut(m);
        var rnd = e.getRandom();
        for (int i = 0; i < PAIRS.length; i++) {
            if (at(m, PAIRS[i][0], a) == null) continue;
            if (PAIRS[i][1] == null) { if (tip == null || out < 0.15F) { st.rest[i] = 0; continue; } b.set(tip); }
            else if (at(m, PAIRS[i][1], b) == null) continue;
            float d = a.distance(b);
            if (st.rest[i] <= 0F) {                                       // forms again once the mouth is nearly closed
                if (d < 0.03F || (PAIRS[i][1] == null && out > 0.15F && out < 0.35F)) { st.rest[i] = Math.max(0.012F, d); st.limit[i] = 0.10F + rnd.nextFloat() * 0.16F; }
                continue;
            }
            if (d > st.rest[i] + st.limit[i]) {                            // snapped: the pieces fall
                st.rest[i] = 0F;
                Vector3f mid = new Vector3f(a).lerp(b, 0.5F);
                drip(mid, ex, ey, ez, yaw, scale, 3);
                continue;
            }
            float stretch = (d - st.rest[i]) / st.limit[i];                 // thins as it stretches
            float rope = st.rest[i] + st.limit[i] * 1.1F;
            float sag = 0.5F * (float) Math.sqrt(Math.max(0F, rope * rope - d * d)) + 0.004F;
            float sway = (float) Math.sin(age * 0.11F + i * 1.7F) * 0.006F;
            strand(vc, mat, nm, a, b, sag, sway, 0.0028F * (1.15F - stretch * 0.7F), light);
            if (d > 0.04F && rnd.nextFloat() < 0.02F) drip(new Vector3f(a).lerp(b, 0.5F).sub(0, sag, 0), ex, ey, ez, yaw, scale, 1);
        }
        if (tip != null && out > 0.3F && (int) age != st.lastDrip && rnd.nextFloat() < 0.18F) {    // dripping off the tongue
            st.lastDrip = (int) age;
            drip(tip, ex, ey, ez, yaw, scale, 1);
        }
    }

    private static void strand(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f a, Vector3f b, float sag, float sway, float w, int light) {
        int n = 10;
        Vector3f prev = null;
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f side = new Vector3f(dir).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-8F) side.set(1, 0, 0);
        side.normalize();
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n;
            float bulge = 4 * t * (1 - t);
            Vector3f p = new Vector3f(a).lerp(b, t).add(side.x * sway * bulge, -sag * bulge, side.z * sway * bulge);
            if (prev != null) {
                float wt = w * (1F + 1.4F * bulge);                       // a fat bead hangs in the middle
                quad(vc, mat, nm, prev, p, wt, 0, 1, 0, light);           // two crossed ribbons so it reads from any side
                quad(vc, mat, nm, prev, p, wt, side.x, 0, side.z, light);
            }
            prev = p;
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f nm, Vector3f p0, Vector3f p1, float w, float ox, float oy, float oz, int light) {
        float hx = ox * w, hy = oy * w, hz = oz * w;
        v(vc, mat, nm, p0.x - hx, p0.y - hy, p0.z - hz, light);
        v(vc, mat, nm, p0.x + hx, p0.y + hy, p0.z + hz, light);
        v(vc, mat, nm, p1.x + hx, p1.y + hy, p1.z + hz, light);
        v(vc, mat, nm, p1.x - hx, p1.y - hy, p1.z - hz, light);
    }

    private static void v(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float x, float y, float z, int light) {
        vc.vertex(mat, x, y, z).color(226, 222, 200, 190).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, 0, 1, 0).endVertex();
    }

    private static void drip(Vector3f model, double ex, double ey, double ez, float yaw, float scale, int n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double[] w = ShadowSkeleton.toWorld(model, ex, ey, ez, yaw, scale);
        for (int i = 0; i < n; i++)
            mc.level.addParticle(ShadowRegistry.DROOL.get(), w[0] + (mc.level.random.nextDouble() - 0.5) * 0.1, w[1], w[2] + (mc.level.random.nextDouble() - 0.5) * 0.1, 0, 0, 0);
    }

    private Saliva() {}
}
