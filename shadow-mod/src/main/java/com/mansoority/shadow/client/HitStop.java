package com.mansoority.shadow.client;

import com.mansoority.shadow.MansoorityBoss;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * weight on every blow. when you land a hit (or it lands one on you) its body locks for a few frames and snaps on,
 * a flash runs over it, and your view jolts. and before each of its strikes there's a glint over its whole outline -
 * the moment to dodge.
 */
public final class HitStop {
    private static final class S { long until, flashUntil, last; float frozen = Float.NaN, power; }
    private static final Map<Integer, S> BY = new HashMap<>();
    /** red over the screen when it hits you, fading */
    static long hurtUntil; static float hurtPower;

    /** freeze entity 'id' for 'ms' and flash it */
    public static void hit(int id, int ms, float power) {
        S s = BY.computeIfAbsent(id, k -> new S());
        long now = net.minecraft.Util.getMillis();
        if (now - s.last < 140) ms = Math.min(ms, 40);          // a hail of arrows doesn't stop it dead
        s.last = now;
        s.until = Math.max(s.until, now + ms);
        s.flashUntil = now + 150; s.power = Math.max(power, s.flashUntil > now ? s.power * 0.5F : 0F);
    }

    /** the animation clock for this entity: frozen while a hit lands, then it snaps on to where it should be */
    public static float age(int id, float age) {
        S s = BY.get(id);
        if (s == null) return age;
        long now = net.minecraft.Util.getMillis();
        if (now < s.until) { if (Float.isNaN(s.frozen)) s.frozen = age; return s.frozen; }
        s.frozen = Float.NaN;
        if (now > s.flashUntil + 1000) BY.remove(id);
        return age;
    }

    /** 0..1: the white flash over it after a hit */
    public static float flash(int id) {
        S s = BY.get(id);
        if (s == null) return 0F;
        long left = s.flashUntil - net.minecraft.Util.getMillis();
        return left <= 0 ? 0F : Math.min(1F, s.power) * left / 150F;
    }

    /** you took a blow: the screen bleeds red at the edges and the view snaps */
    static void hurtYou(float power) {
        hurtUntil = net.minecraft.Util.getMillis() + 380; hurtPower = Mth.clamp(power, 0.3F, 1F);
        ClientFx.kick(4F + 5F * power);
    }
    static float hurtNow() {
        long left = hurtUntil - net.minecraft.Util.getMillis();
        return left <= 0 ? 0F : hurtPower * left / 380F;
    }

    /**
     * the tell: a sharp glint over its outline that peaks just before the blow lands (0..1). every telegraphed strike
     * gets one, at the same frame on everyone's screen (the action clock is shared)
     */
    public static float tell(MansoorityBoss b, float el) {
        int a = b.getAction();
        float peak, width;
        if (a == MansoorityBoss.A_BITE) { peak = 16F; width = 5F; }
        else if (a == MansoorityBoss.A_BLADE) { peak = 13.5F; width = 4F; }
        else if (a == MansoorityBoss.A_SPIKES) { peak = 18.5F; width = 5F; }
        else if (a == MansoorityBoss.A_THROW) { peak = 16.5F; width = 4F; }
        else if (a == MansoorityBoss.A_SCREAM) { peak = 9F; width = 4F; }
        else return 0F;
        float d = Math.abs(el - peak) / width;
        return d >= 1F ? 0F : (1F - d) * (1F - d);
    }

    private HitStop() {}
}
