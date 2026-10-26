package com.mansoority.shadow.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * the skeleton at runtime: bone matrices from the pose, two-bone IK so the hands and feet land on the real terrain
 * under them (slopes, steps, the edge of a cliff), and skinning of the mesh. same maths as shadow-blender/tools/rig.py.
 */
final class ShadowSkeleton {
    /** the four limbs as two-bone chains: upper bone, lower bone, contact point at rest (model metres) */
    static final String[][] LIMB = {{"legL", "shinL"}, {"legR", "shinR"}, {"foreL", "handL"}, {"foreR", "handR"}};
    static final float[][] END = {{-0.27F, 0.05F, 0.10F}, {0.27F, 0.05F, 0.10F}, {-0.67F, 0.15F, 0.68F}, {0.67F, 0.15F, 0.68F}};   // toes and fingertips touch down

    private static Matrix4f[] W = new Matrix4f[0];
    private static final Matrix4f TMP = new Matrix4f(), INV = new Matrix4f();
    private static final Vector3f V1 = new Vector3f(), V2 = new Vector3f(), V3 = new Vector3f();

    static Matrix4f[] matrices(ShadowMesh m) {
        if (W.length < m.parts.size()) { W = new Matrix4f[m.parts.size()]; for (int i = 0; i < W.length; i++) W[i] = new Matrix4f(); }
        return W;
    }

    /** local = T(offset) T(pivot) R(keyframe) R(extra zyx) T(-pivot); world = parent * local */
    static void fk(ShadowMesh m) {
        Matrix4f[] w = matrices(m);
        for (ShadowMesh.Part p : m.parts) {
            Matrix4f L = TMP.identity().translate(p.tx + p.px, p.ty + p.py, p.tz + p.pz).rotate(p.anim);
            if (p.xRot != 0F || p.yRot != 0F || p.zRot != 0F) L.rotateZYX(p.zRot, p.yRot, p.xRot);
            L.translate(-p.px, -p.py, -p.pz);
            if (p.parent < 0) w[p.index].set(L); else w[p.parent].mul(L, w[p.index]);
        }
    }

    static Vector3f endPos(ShadowMesh m, int limb, Vector3f out) {
        ShadowMesh.Part lo = m.get(LIMB[limb][1]);
        return W[lo.index].transformPosition(END[limb][0], END[limb][1], END[limb][2], out);
    }

    static Vector3f jointPos(ShadowMesh.Part p, Vector3f out) {
        return W[p.index].transformPosition(p.px, p.py, p.pz, out);
    }

    /**
     * bend the chain so its end lands on 'target' (model space), keeping its knee/elbow on the side it already bends to.
     * rewrites both bones' rotations, then refreshes the matrices.
     */
    static void twoBone(ShadowMesh m, int limb, Vector3f target) {
        ShadowMesh.Part up = m.get(LIMB[limb][0]), lo = m.get(LIMB[limb][1]);
        if (up == null || lo == null) return;
        Vector3f pole = jointPos(lo, new Vector3f());                      // where the knee is now
        Matrix4f parent = up.parent < 0 ? new Matrix4f() : W[up.parent];
        parent.invert(INV);
        Vector3f h = new Vector3f(up.px, up.py, up.pz), k0 = new Vector3f(lo.px, lo.py, lo.pz);
        Vector3f e0 = new Vector3f(END[limb][0], END[limb][1], END[limb][2]);
        Vector3f T = INV.transformPosition(target, new Vector3f());
        Vector3f P = INV.transformPosition(pole, new Vector3f()).sub(h);
        float l1 = k0.distance(h), l2 = e0.distance(k0);
        Vector3f dv = new Vector3f(T).sub(h);
        float d = Math.min(Math.max(dv.length(), Math.abs(l1 - l2) + 1e-4F), l1 + l2 - 1e-4F);
        Vector3f u = new Vector3f(dv).normalize();
        float a = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
        float hgt = (float) Math.sqrt(Math.max(l1 * l1 - a * a, 0F));
        Vector3f pp = new Vector3f(P).sub(new Vector3f(u).mul(P.dot(u)));
        if (pp.lengthSquared() < 1e-10F) pp.set(0, 1, 0).cross(u);
        pp.normalize();
        Vector3f K = new Vector3f(h).add(new Vector3f(u).mul(a)).add(new Vector3f(pp).mul(hgt));
        Vector3f Te = new Vector3f(h).add(new Vector3f(u).mul(d));
        Vector3f x0 = new Vector3f(k0).sub(h).normalize();
        Vector3f n0 = new Vector3f(k0).sub(h).cross(new Vector3f(e0).sub(k0));
        if (n0.lengthSquared() < 1e-10F) n0.set(x0).cross(0, 0, 1);
        n0.normalize();
        Vector3f x1 = new Vector3f(K).sub(h).normalize();
        Vector3f n1 = new Vector3f(K).sub(h).cross(new Vector3f(Te).sub(K));
        if (n1.lengthSquared() < 1e-12F) return;
        n1.normalize();
        Matrix3f B0 = new Matrix3f().setColumn(0, x0).setColumn(1, n0).setColumn(2, new Vector3f(x0).cross(n0));
        Matrix3f B1 = new Matrix3f().setColumn(0, x1).setColumn(1, n1).setColumn(2, new Vector3f(x1).cross(n1));
        Matrix3f RA = new Matrix3f(B1).mul(B0.transpose(new Matrix3f()));
        Vector3f want = RA.transpose(new Matrix3f()).transform(new Vector3f(Te).sub(K));
        up.anim.setFromNormalized(RA); up.xRot = up.yRot = up.zRot = 0F;
        lo.anim.rotationTo(new Vector3f(e0).sub(k0).normalize(), want.normalize()); lo.xRot = lo.yRot = lo.zRot = 0F;
        fk(m);
    }

    /** top of the ground under (x, z) near height y, or NaN if there's nothing within reach */
    static float groundAt(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int bx = net.minecraft.util.Mth.floor(x), bz = net.minecraft.util.Mth.floor(z);
        for (int yy = net.minecraft.util.Mth.floor(y + 2.0); yy >= net.minecraft.util.Mth.floor(y - 4.0); yy--) {
            q.set(bx, yy, bz);
            BlockState s = level.getBlockState(q);
            if (s.isAir()) continue;
            VoxelShape sh = s.getCollisionShape(level, q);
            if (!sh.isEmpty()) return yy + (float) sh.max(Direction.Axis.Y);
        }
        return Float.NaN;
    }

    /** model -> world for this entity (rotated by its body yaw, scaled) */
    static double[] toWorld(Vector3f p, double ex, double ey, double ez, float yawDeg, float scale) {
        double a = Math.toRadians(-yawDeg), c = Math.cos(a), s = Math.sin(a);
        double x = p.x * scale, z = p.z * scale;
        return new double[]{ex + x * c + z * s, ey + p.y * scale, ez - x * s + z * c};
    }

    /**
     * plant hands and feet on the real ground: the body rises or sinks to the lowest foothold, each limb reaches its own.
     * returns a bitmask of limbs that touched down this frame (for footsteps), or 0.
     */
    static int ground(ShadowMesh m, ShadowAnim.State st, int clipContact, Level level, double ex, double ey, double ez,
                      float yaw, float scale, float dt, boolean allowBody) {
        fk(m);
        float[] want = new float[4];
        boolean[] active = new boolean[4];
        float lowest = Float.MAX_VALUE;
        Vector3f f = new Vector3f();
        for (int i = 0; i < 4; i++) {
            if (m.get(LIMB[i][1]) == null) continue;
            endPos(m, i, f);
            boolean planted = (clipContact >> i & 1) != 0;
            if (!planted && f.y > END[i][1] + 0.45F) continue;            // lifted high (grabbing, arms open): leave it alone
            double[] w = toWorld(f, ex, ey, ez, yaw, scale);
            float g = groundAt(level, w[0], ey + (f.y - END[i][1]) * scale, w[2]);
            if (Float.isNaN(g)) continue;
            want[i] = net.minecraft.util.Mth.clamp((float) ((g - ey) / scale), -0.45F, 0.35F);
            active[i] = true;
            if (planted) lowest = Math.min(lowest, want[i]);
        }
        float k = Math.min(1F, dt * 0.35F);
        float body = allowBody && lowest != Float.MAX_VALUE ? lowest : 0F;
        st.bodyDy += (body - st.bodyDy) * k;
        for (int i = 0; i < 4; i++) st.footDy[i] += ((active[i] ? want[i] : st.bodyDy) - st.footDy[i]) * Math.min(1F, dt * 0.5F);
        ShadowMesh.Part hips = m.get("hips");
        if (hips != null && Math.abs(st.bodyDy) > 1e-4F) { hips.ty += st.bodyDy; fk(m); }
        for (int i = 0; i < 4; i++) {
            if (!active[i]) continue;
            float lift = st.footDy[i] - st.bodyDy;
            if (Math.abs(lift) < 0.004F) continue;
            endPos(m, i, f);
            twoBone(m, i, f.add(0, lift, 0));
        }
        int touched = 0;
        if (st.lastContact >= 0) touched = clipContact & ~st.lastContact;
        st.lastContact = clipContact;
        return touched;
    }

    /** final vertex positions/normals into mesh.outPos / outNrm (model space) */
    static void skin(ShadowMesh m) {
        Matrix4f[] w = W;
        float[] P = m.pos, N = m.nrm, OP = m.outPos, ON = m.outNrm, WT = m.w0;
        byte[] B0 = m.b0, B1 = m.b1;
        int n = WT.length;
        for (int i = 0; i < n; i++) {
            Matrix4f a = w[B0[i] & 255], b = w[B1[i] & 255];
            float w0 = WT[i], w1 = 1F - w0;
            float x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            float nx = N[i * 3], ny = N[i * 3 + 1], nz = N[i * 3 + 2];
            if (w1 < 0.004F || a == b) {
                OP[i * 3] = a.m00() * x + a.m10() * y + a.m20() * z + a.m30();
                OP[i * 3 + 1] = a.m01() * x + a.m11() * y + a.m21() * z + a.m31();
                OP[i * 3 + 2] = a.m02() * x + a.m12() * y + a.m22() * z + a.m32();
                ON[i * 3] = a.m00() * nx + a.m10() * ny + a.m20() * nz;
                ON[i * 3 + 1] = a.m01() * nx + a.m11() * ny + a.m21() * nz;
                ON[i * 3 + 2] = a.m02() * nx + a.m12() * ny + a.m22() * nz;
            } else {
                OP[i * 3] = w0 * (a.m00() * x + a.m10() * y + a.m20() * z + a.m30()) + w1 * (b.m00() * x + b.m10() * y + b.m20() * z + b.m30());
                OP[i * 3 + 1] = w0 * (a.m01() * x + a.m11() * y + a.m21() * z + a.m31()) + w1 * (b.m01() * x + b.m11() * y + b.m21() * z + b.m31());
                OP[i * 3 + 2] = w0 * (a.m02() * x + a.m12() * y + a.m22() * z + a.m32()) + w1 * (b.m02() * x + b.m12() * y + b.m22() * z + b.m32());
                ON[i * 3] = w0 * (a.m00() * nx + a.m10() * ny + a.m20() * nz) + w1 * (b.m00() * nx + b.m10() * ny + b.m20() * nz);
                ON[i * 3 + 1] = w0 * (a.m01() * nx + a.m11() * ny + a.m21() * nz) + w1 * (b.m01() * nx + b.m11() * ny + b.m21() * nz);
                ON[i * 3 + 2] = w0 * (a.m02() * nx + a.m12() * ny + a.m22() * nz) + w1 * (b.m02() * nx + b.m12() * ny + b.m22() * nz);
            }
        }
    }

    private ShadowSkeleton() {}
}
