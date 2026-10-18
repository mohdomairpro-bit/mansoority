package com.mansoority.shadow;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ShadowConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MIN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue CHANCE;
    public static final ForgeConfigSpec.IntValue MAX_WITNESSES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("How often The Shadow shows up. It never hurts anyone.").push("encounters");
        ENABLED = b.comment("Turn random encounters on/off (commands still work)").define("enabled", true);
        MIN_MINUTES = b.comment("Minimum real minutes between encounters for one player").defineInRange("minMinutesBetweenEncounters", 50, 5, 10000);
        CHANCE = b.comment("Chance per eligible minute (night/underground, calm, no boss) once the cooldown is over").defineInRange("chancePerEligibleMinute", 0.025D, 0.0D, 1.0D);
        MAX_WITNESSES = b.comment("Only happens if at most this many players are within 32 blocks").defineInRange("maxPlayersNearby", 2, 1, 100);
        b.pop();
        SPEC = b.build();
    }

    private ShadowConfig() {}
}
