package com.mansoority.shadow.client;

import com.mansoority.shadow.TheShadow;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** keyframed clips authored in Blender (shadow.anim): per frame, per part, a rotation and an offset */
public final class ShadowAnim {
    public static final ResourceLocation LOCATION = new ResourceLocation(TheShadow.MODID, "models/shadow.anim");
    public static final float FPS = 20F;

    public static final class Clip {
        public final String name; public final boolean loop; public final float[][][] f;   // [frame][part][qx,qy,qz,qw,tx,ty,tz]
        Clip(String n, boolean l, float[][][] f) { this.name = n; this.loop = l; this.f = f; }
        public int frames() { return f.length; }
    }

    public String[] parts;
    public final Map<String, Clip> clips = new HashMap<>();
    private static ShadowAnim cached; private static boolean failed;

    public static ShadowAnim get() {
        if (cached == null && !failed) {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(LOCATION)) {
                cached = read(in.readAllBytes());
            } catch (Exception e) { failed = true; TheShadow.LOGGER.warn("No shadow animations, using procedural poses ({})", e.toString()); }
        }
        return cached;
    }

    static ShadowAnim read(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4]; b.get(magic);
        if (!"SHA1".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IllegalStateException("bad anim header");
        ShadowAnim a = new ShadowAnim();
        int np = b.getInt(); a.parts = new String[np];
        for (int i = 0; i < np; i++) { byte[] nb = new byte[b.getShort()]; b.get(nb); a.parts[i] = new String(nb, StandardCharsets.UTF_8); }
        int nc = b.getInt();
        for (int c = 0; c < nc; c++) {
            byte[] nb = new byte[b.getShort()]; b.get(nb);
            int frames = b.getInt(); boolean loop = b.get() != 0;
            float[][][] f = new float[frames][np][7];
            for (int i = 0; i < frames; i++) for (int p = 0; p < np; p++) for (int k = 0; k < 7; k++) f[i][p][k] = b.getFloat();
            String name = new String(nb, StandardCharsets.UTF_8);
            a.clips.put(name, new Clip(name, loop, f));
        }
        return a;
    }

    /** sample a clip at 'frame' (fractional) into out[part][7] */
    public static void sample(Clip c, float frame, float[][] out) {
        int n = c.frames();
        float fr;
        if (c.loop) { float len = n - 1; fr = ((frame % len) + len) % len; } else fr = Math.max(0F, Math.min(n - 1, frame));
        int i0 = (int) Math.floor(fr), i1 = Math.min(n - 1, i0 + 1);
        float t = fr - i0;
        for (int p = 0; p < out.length; p++) {
            float[] a = c.f[i0][p], b = c.f[i1][p];
            float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
            float s = dot < 0 ? -1F : 1F;
            for (int k = 0; k < 4; k++) out[p][k] = a[k] + (b[k] * s - a[k]) * t;
            for (int k = 4; k < 7; k++) out[p][k] = a[k] + (b[k] - a[k]) * t;
        }
    }

    /** per-entity playback: current clip, the one fading out, and the fade */
    public static final class State {
        public String cur = "idle", prev = null;
        public float curT, prevT, fade = 1F, lastAge = -1F;
        public final float[][] a, b;
        State(int np) { a = new float[np][7]; b = new float[np][7]; }
    }

    public State newState() { return new State(parts.length); }

    /**
     * play 'clip'. if 'drive' >= 0 the time is driven (one-shot scrubbed by game state), otherwise it advances
     * with age at 'speed'. switching clips crossfades over ~8 ticks.
     */
    public void play(State s, String clip, float drive, float speed, float age) {
        float dt = s.lastAge < 0 ? 0 : Math.max(0F, Math.min(5F, age - s.lastAge));
        s.lastAge = age;
        if (!clips.containsKey(clip)) clip = "idle";
        if (!clip.equals(s.cur)) {
            s.prev = s.cur; s.prevT = s.curT; s.cur = clip; s.curT = 0F; s.fade = 0F;
        }
        if (drive >= 0) s.curT = drive; else s.curT += dt * speed;
        if (s.prev != null) s.prevT += dt;
        s.fade = Math.min(1F, s.fade + dt / 8F);
    }

    /** write the blended pose into the mesh parts (rotation as quaternion + offset) */
    public void apply(State s, ShadowMesh mesh) {
        sample(clips.get(s.cur), s.curT, s.a);
        boolean blend = s.prev != null && s.fade < 1F && clips.containsKey(s.prev);
        if (blend) sample(clips.get(s.prev), s.prevT, s.b);
        float w = s.fade * s.fade * (3F - 2F * s.fade);
        Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
        for (int i = 0; i < parts.length; i++) {
            ShadowMesh.Part p = mesh.get(parts[i]);
            if (p == null) continue;
            float[] A = s.a[i];
            qa.set(A[0], A[1], A[2], A[3]).normalize();
            float tx = A[4], ty = A[5], tz = A[6];
            if (blend) {
                float[] B = s.b[i];
                qb.set(B[0], B[1], B[2], B[3]).normalize();
                qb.slerp(qa, w); qa.set(qb);
                tx = B[4] + (tx - B[4]) * w; ty = B[5] + (ty - B[5]) * w; tz = B[6] + (tz - B[6]) * w;
            }
            p.anim.set(qa); p.tx = tx; p.ty += ty; p.tz = tz;
        }
    }

    private ShadowAnim() {}
}
