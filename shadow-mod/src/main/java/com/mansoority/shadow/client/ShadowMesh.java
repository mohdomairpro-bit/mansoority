package com.mansoority.shadow.client;

import com.mansoority.shadow.TheShadow;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * the creature's skinned mesh (SHD3, written by shadow-blender/tools/skin.py): one shared vertex buffer where every
 * vertex follows up to two bones, so joints bend like skin. bones keep the per-frame pose fields the animation writes.
 */
public final class ShadowMesh {
    public static final ResourceLocation LOCATION = new ResourceLocation(TheShadow.MODID, "models/shadow.mesh");
    public static final ResourceLocation LOD = new ResourceLocation(TheShadow.MODID, "models/shadow_lod.mesh");

    /** a bone. the pose fields are filled every frame (keyframes + procedural extras) */
    public static final class Part {
        public final String name;
        public final int index, parent;
        public final float px, py, pz;
        public final List<Part> children = new ArrayList<>();
        public float xRot, yRot, zRot;       // procedural extras, applied after the keyframed rotation
        public float tx, ty, tz;             // offset (model metres)
        public final org.joml.Quaternionf anim = new org.joml.Quaternionf();

        Part(String name, int index, int parent, float px, float py, float pz) {
            this.name = name; this.index = index; this.parent = parent; this.px = px; this.py = py; this.pz = pz;
        }
        public void reset() { xRot = yRot = zRot = 0F; tx = ty = tz = 0F; anim.identity(); }
    }

    public final List<Part> parts = new ArrayList<>();
    public final List<Part> roots = new ArrayList<>();
    private final Map<String, Part> byName = new HashMap<>();
    // vertices
    public float[] pos, nrm, uv, w0;
    public byte[] b0, b1;
    public int[] solid, glow;            // triangle corner indices
    /** skinned output, rewritten every time an entity is drawn (render thread only) */
    public float[] outPos, outNrm;

    public Part get(String name) { return byName.get(name); }

    private final Map<String, int[]> subsets = new HashMap<>();
    /** the triangles that belong entirely to these bones (e.g. the two halves of the face, for the weak-point glow) */
    public int[] trianglesOf(String... bones) {
        String key = String.join(",", bones);
        return subsets.computeIfAbsent(key, k -> {
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (String b : bones) { Part p = get(b); if (p != null) ids.add(p.index); }
            int[] tmp = new int[solid.length]; int n = 0;
            for (int t = 0; t < solid.length; t += 3) {
                boolean all = true;
                for (int c = 0; c < 3; c++) { int v = solid[t + c]; if (!ids.contains(b0[v] & 255) || w0[v] < 0.9F) { all = false; break; } }
                if (all) { tmp[n++] = solid[t]; tmp[n++] = solid[t + 1]; tmp[n++] = solid[t + 2]; }
            }
            return java.util.Arrays.copyOf(tmp, n);
        });
    }
    public int vertexCount() { return w0.length; }

    /** three detail levels: 0 = close up, 1 = mid range (keeps every hair and spine), 2 = far away / shadow pass */
    private static final ResourceLocation[] LEVELS = {LOCATION, new ResourceLocation(TheShadow.MODID, "models/shadow_mid.mesh"), LOD};
    private static final ShadowMesh[] CACHE = new ShadowMesh[3];
    private static final boolean[] FAILED = new boolean[3];

    public static ShadowMesh level(int lvl) {
        lvl = Math.max(0, Math.min(2, lvl));
        if (CACHE[lvl] == null && !FAILED[lvl]) {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(LEVELS[lvl])) {
                CACHE[lvl] = read(in.readAllBytes());
            } catch (Exception e) {
                FAILED[lvl] = true;
                if (lvl == 0) TheShadow.LOGGER.error("Could not load the shadow mesh", e);
            }
        }
        if (CACHE[lvl] != null) return CACHE[lvl];
        return lvl > 0 ? level(lvl - 1) : null;
    }

    /** the far-away version */
    public static ShadowMesh lod() { return level(2); }
    public static ShadowMesh get() { return level(0); }

    public static void invalidate() { for (int i = 0; i < 3; i++) { CACHE[i] = null; FAILED[i] = false; } }

    static ShadowMesh read(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4]; b.get(magic);
        if (!"SHD3".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("bad mesh header (need SHD3)");
        ShadowMesh mesh = new ShadowMesh();
        int count = b.getInt();
        for (int i = 0; i < count; i++) {
            byte[] nb = new byte[b.getShort()]; b.get(nb);
            int parent = b.getInt();
            float px = b.getFloat(), py = b.getFloat(), pz = b.getFloat();
            Part p = new Part(new String(nb, StandardCharsets.UTF_8), i, parent, px, py, pz);
            mesh.parts.add(p); mesh.byName.put(p.name, p);
        }
        for (Part p : mesh.parts) { if (p.parent < 0) mesh.roots.add(p); else mesh.parts.get(p.parent).children.add(p); }
        int nv = b.getInt();
        mesh.pos = new float[nv * 3]; mesh.nrm = new float[nv * 3]; mesh.uv = new float[nv * 2];
        mesh.b0 = new byte[nv]; mesh.b1 = new byte[nv]; mesh.w0 = new float[nv];
        for (int v = 0; v < nv; v++) {
            mesh.pos[v * 3] = b.getFloat(); mesh.pos[v * 3 + 1] = b.getFloat(); mesh.pos[v * 3 + 2] = b.getFloat();
            mesh.nrm[v * 3] = b.get() / 127F; mesh.nrm[v * 3 + 1] = b.get() / 127F; mesh.nrm[v * 3 + 2] = b.get() / 127F;
            mesh.uv[v * 2] = b.getFloat(); mesh.uv[v * 2 + 1] = b.getFloat();
            mesh.b0[v] = b.get(); mesh.b1[v] = b.get(); mesh.w0[v] = (b.get() & 255) / 255F;
        }
        int ns = b.getInt(); mesh.solid = new int[ns * 3]; for (int i = 0; i < ns * 3; i++) mesh.solid[i] = b.getInt();
        int ng = b.getInt(); mesh.glow = new int[ng * 3]; for (int i = 0; i < ng * 3; i++) mesh.glow[i] = b.getInt();
        mesh.outPos = new float[nv * 3]; mesh.outNrm = new float[nv * 3];
        return mesh;
    }
}
