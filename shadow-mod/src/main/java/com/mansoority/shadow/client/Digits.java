package com.mansoority.shadow.client;

/**
 * fingers and toes: on the ground they spread and press down, in the air they curl up.
 * (bones: fingerL0..3 / fingerR0..3 / toeL0..3 / toeR0..3; the outer ones splay more)
 */
final class Digits {
    private static final String[][] NAMES = {
            {"toeL0", "toeL1", "toeL2", "toeL3"}, {"toeR0", "toeR1", "toeR2", "toeR3"},
            {"fingerL0", "fingerL1", "fingerL2", "fingerL3"}, {"fingerR0", "fingerR1", "fingerR2", "fingerR3"}};

    static void flex(ShadowMesh m, float[] air, boolean[] free) {
        boolean any = false;
        for (int limb = 0; limb < 4; limb++) {
            float a = free[limb] ? 0.6F : air[limb];
            float side = limb % 2 == 0 ? -1F : 1F;
            for (int k = 0; k < 4; k++) {
                ShadowMesh.Part p = m.get(NAMES[limb][k]);
                if (p == null) continue;
                any = true;
                float spread = (k - 1.5F) / 1.5F;                         // -1 .. 1 across the hand
                if (limb >= 2) {
                    p.xRot += 0.9F * a - 0.15F * (1 - a);                    // curl in the air, press flat on the ground
                    p.zRot += side * spread * 0.28F * (1 - a);              // spread out when planted
                } else {
                    p.xRot += (k == 3 ? -0.8F : 0.75F) * a - 0.1F * (1 - a);  // talons hook (the back one the other way)
                    p.yRot += spread * 0.22F * (1 - a) * side;
                }
            }
        }
        if (any) ShadowSkeleton.fk(m);
    }

    private Digits() {}
}
