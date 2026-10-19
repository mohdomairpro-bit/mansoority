package com.mansoority.shadow.client;

import com.mansoority.shadow.TheShadow;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** the high-poly creature exported from Blender (shadow-blender/build_shadow.py) */
public final class ShadowMesh {
    public static final ResourceLocation LOCATION = new ResourceLocation(TheShadow.MODID, "models/shadow.mesh");

    public static final class Part {
        public final String name;
        public final int parent;
        public final float px, py, pz;
        public final float[] pos;      // xyz per vertex, 3 vertices per triangle
        public final float[] nrm;
        public final int[] rgb;        // packed 0xRRGGBB per vertex
        public final boolean[] glow;   // per triangle
        public final List<Part> children = new ArrayList<>();
        public float xRot, yRot, zRot; // animated each frame

        Part(String name, int parent, float px, float py, float pz, int tris) {
            this.name = name; this.parent = parent; this.px = px; this.py = py; this.pz = pz;
            this.pos = new float[tris * 9]; this.nrm = new float[tris * 9];
            this.rgb = new int[tris * 3]; this.glow = new boolean[tris];
        }
        public int triangles() { return glow.length; }
        public void reset() { xRot = yRot = zRot = 0F; }
    }

    public final List<Part> parts = new ArrayList<>();
    public final List<Part> roots = new ArrayList<>();

    public Part get(String name) {
        for (Part p : parts) if (p.name.equals(name)) return p;
        return null;
    }

    private static ShadowMesh cached;
    private static boolean failed;

    public static ShadowMesh get() {
        if (cached == null && !failed) {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(LOCATION)) {
                cached = read(in.readAllBytes());
            } catch (Exception e) {
                failed = true;
                TheShadow.LOGGER.error("Could not load the shadow mesh", e);
            }
        }
        return cached;
    }

    public static void invalidate() { cached = null; failed = false; }

    static ShadowMesh read(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4]; b.get(magic);
        if (!"SHDW".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("bad mesh header");
        ShadowMesh mesh = new ShadowMesh();
        int count = b.getInt();
        for (int i = 0; i < count; i++) {
            byte[] nb = new byte[b.getShort()]; b.get(nb);
            int parent = b.getInt();
            float px = b.getFloat(), py = b.getFloat(), pz = b.getFloat();
            int tris = b.getInt();
            Part p = new Part(new String(nb, StandardCharsets.UTF_8), parent, px, py, pz, tris);
            for (int v = 0; v < tris * 3; v++) {
                p.pos[v * 3] = b.getFloat(); p.pos[v * 3 + 1] = b.getFloat(); p.pos[v * 3 + 2] = b.getFloat();
                p.nrm[v * 3] = b.get() / 127F; p.nrm[v * 3 + 1] = b.get() / 127F; p.nrm[v * 3 + 2] = b.get() / 127F;
                int r = b.get() & 255, g = b.get() & 255, bl = b.get() & 255, e = b.get() & 255;
                p.rgb[v] = (r << 16) | (g << 8) | bl;
                if (e != 0) p.glow[v / 3] = true;
            }
            mesh.parts.add(p);
        }
        for (Part p : mesh.parts) {
            if (p.parent < 0) mesh.roots.add(p); else mesh.parts.get(p.parent).children.add(p);
        }
        return mesh;
    }
}
