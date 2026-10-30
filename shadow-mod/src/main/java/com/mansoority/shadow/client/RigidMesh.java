package com.mansoority.shadow.client;

import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * the smaller models (spiders, husks, the scavenger, the blade, the shield, the armour), written by
 * shadow-blender/build_minions.py. rigid parts in a hierarchy: every triangle belongs to one part, each part turns
 * about its pivot. colours come from the shared palette texture.
 */
public final class RigidMesh {
    public static final ResourceLocation PALETTE = new ResourceLocation(TheShadow.MODID, "textures/entity/minion_palette.png");

    public static final class Part {
        public final String name;
        public final int index, parent;
        public final float px, py, pz;
        final float[] pos, nrm, uv;
        final boolean[] glow;           // per triangle
        public float xRot, yRot, zRot, tx, ty, tz, sx = 1F, sy = 1F, sz = 1F;
        public boolean visible = true;

        Part(String name, int index, int parent, float px, float py, float pz, float[] pos, float[] nrm, float[] uv, boolean[] glow) {
            this.name = name; this.index = index; this.parent = parent; this.px = px; this.py = py; this.pz = pz;
            this.pos = pos; this.nrm = nrm; this.uv = uv; this.glow = glow;
        }
        public void reset() { xRot = yRot = zRot = tx = ty = tz = 0F; sx = sy = sz = 1F; visible = true; }
    }

    public final List<Part> parts = new ArrayList<>();
    private final Map<String, Part> byName = new HashMap<>();
    private Matrix4f[] world = new Matrix4f[0];
    private final Matrix4f tmp = new Matrix4f();
    private final Matrix3f nmat = new Matrix3f();

    public Part get(String n) { return byName.get(n); }
    public void reset() { for (Part p : parts) p.reset(); }

    private static final Map<String, RigidMesh> CACHE = new HashMap<>();

    public static RigidMesh get(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        RigidMesh m = null;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(new ResourceLocation(TheShadow.MODID, "models/" + name + ".mesh"))) {
            m = read(in.readAllBytes());
        } catch (Exception e) {
            TheShadow.LOGGER.error("Could not load model " + name, e);
        }
        CACHE.put(name, m);
        return m;
    }

    public static void invalidate() { CACHE.clear(); }

    static RigidMesh read(byte[] data) throws java.io.IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4]; b.get(magic);
        if (!"SHD2".equals(new String(magic, StandardCharsets.US_ASCII))) throw new java.io.IOException("not an SHD2 mesh");
        RigidMesh m = new RigidMesh();
        int n = b.getInt();
        for (int i = 0; i < n; i++) {
            byte[] nb = new byte[b.getShort()]; b.get(nb);
            int parent = b.getInt();
            float px = b.getFloat(), py = b.getFloat(), pz = b.getFloat();
            int t = b.getInt();
            float[] pos = new float[t * 9], nrm = new float[t * 9], uv = new float[t * 6];
            boolean[] glow = new boolean[t];
            for (int v = 0; v < t * 3; v++) {
                pos[v * 3] = b.getFloat(); pos[v * 3 + 1] = b.getFloat(); pos[v * 3 + 2] = b.getFloat();
                nrm[v * 3] = b.get() / 127F; nrm[v * 3 + 1] = b.get() / 127F; nrm[v * 3 + 2] = b.get() / 127F;
                uv[v * 2] = b.getFloat(); uv[v * 2 + 1] = b.getFloat();
                byte g = b.get();
                if (v % 3 == 0) glow[v / 3] = g != 0;
            }
            Part p = new Part(new String(nb, StandardCharsets.UTF_8), i, parent, px, py, pz, pos, nrm, uv, glow);
            m.parts.add(p); m.byName.put(p.name, p);
        }
        m.world = new Matrix4f[n];
        for (int i = 0; i < n; i++) m.world[i] = new Matrix4f();
        return m;
    }

    /** part matrices (model space): T(offset) T(pivot) R(z y x) S T(-pivot), parent first */
    public void pose() {
        for (Part p : parts) {
            Matrix4f L = tmp.identity().translate(p.tx + p.px, p.ty + p.py, p.tz + p.pz);
            if (p.zRot != 0F || p.yRot != 0F || p.xRot != 0F) L.rotateZYX(p.zRot, p.yRot, p.xRot);
            if (p.sx != 1F || p.sy != 1F || p.sz != 1F) L.scale(p.sx, p.sy, p.sz);
            L.translate(-p.px, -p.py, -p.pz);
            if (p.parent < 0) world[p.index].set(L); else world[p.parent].mul(L, world[p.index]);
        }
    }

    /** a point on a part (model space, after pose()) */
    public org.joml.Vector3f at(String part, float x, float y, float z) {
        Part p = get(part);
        return p == null ? new org.joml.Vector3f(x, y, z) : world[p.index].transformPosition(x, y, z, new org.joml.Vector3f());
    }

    /**
     * draw everything (call pose() first): the solid triangles into 'solid', then the glowing ones (eyes, sacs, edges)
     * into 'glow' full bright. buffers are taken one after the other: two open at once breaks the immediate buffer source
     */
    public void draw(PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buf, net.minecraft.client.renderer.RenderType solid,
                     net.minecraft.client.renderer.RenderType glow, int light, int overlay, float r, float g, float bl, float a) {
        VertexConsumer vc = buf.getBuffer(solid);
        for (Part p : parts) if (p.visible) drawPart(ps, p, world[p.index], vc, false, light, overlay, r, g, bl, a);
        if (glow == null) return;
        VertexConsumer gc = buf.getBuffer(glow);
        for (Part p : parts) if (p.visible) drawPart(ps, p, world[p.index], gc, true, 0xF000F0, overlay, 1F, 1F, 1F, a);
    }

    /** one part's triangles: the glowing ones if glowPass, else the rest. partM null = no part transform (armour) */
    public void drawPart(PoseStack ps, Part p, Matrix4f partM, VertexConsumer vc, boolean glowPass, int light, int overlay, float r, float g, float bl, float a) {
        Matrix4f M = new Matrix4f(ps.last().pose());
        if (partM != null) { M.mul(partM); partM.get3x3(nmat); } else nmat.identity();
        ps.last().normal().mul(nmat, nmat);
        float[] P = p.pos, N = p.nrm, U = p.uv;
        int tris = p.glow.length;
        for (int t = 0; t < tris; t++) {
            if (p.glow[t] != glowPass) continue;
            for (int c = 0; c < 3; c++) {
                int v = t * 3 + c, v3 = v * 3;
                float x = P[v3], y = P[v3 + 1], z = P[v3 + 2];
                float ox = M.m00() * x + M.m10() * y + M.m20() * z + M.m30();
                float oy = M.m01() * x + M.m11() * y + M.m21() * z + M.m31();
                float oz = M.m02() * x + M.m12() * y + M.m22() * z + M.m32();
                float nx = N[v3], ny = N[v3 + 1], nz = N[v3 + 2];
                float qx = nmat.m00 * nx + nmat.m10 * ny + nmat.m20 * nz;
                float qy = nmat.m01 * nx + nmat.m11 * ny + nmat.m21 * nz;
                float qz = nmat.m02 * nx + nmat.m12 * ny + nmat.m22 * nz;
                float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
                if (len > 1e-6F) { qx /= len; qy /= len; qz /= len; }
                vc.vertex(ox, oy, oz, r, g, bl, a, U[v * 2], U[v * 2 + 1], overlay, light, qx, qy, qz);
            }
        }
    }

    /** solid and glow triangles of one part into the same consumer (armour: the layer hands us a single buffer) */
    public void drawPartAll(PoseStack ps, Part p, VertexConsumer vc, int light, int overlay, float r, float g, float bl, float a) {
        drawPart(ps, p, null, vc, false, light, overlay, r, g, bl, a);
        drawPart(ps, p, null, vc, true, 0xF000F0, overlay, 1F, 1F, 1F, a);
    }

    public Matrix4f matrix(Part p) { return world[p.index]; }
}
