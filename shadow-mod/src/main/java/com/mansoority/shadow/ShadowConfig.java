package com.mansoority.shadow;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ShadowConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MIN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue CHANCE;
    public static final ForgeConfigSpec.IntValue MAX_WITNESSES;
    public static final ForgeConfigSpec.DoubleValue CATCH_DAMAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("How often The Shadow shows up. It never hurts anyone.").push("encounters");
        ENABLED = b.comment("Turn random encounters on/off (commands still work)").define("enabled", true);
        MIN_MINUTES = b.comment("Minimum real minutes between encounters for one player").defineInRange("minutesBetweenEncounters", 150, 5, 10000);
        CHANCE = b.comment("Chance per eligible minute once the cooldown is over (x0.35 in daylight). Never while AFK or near a boss.").defineInRange("chanceEachMinute", 0.012D, 0.0D, 1.0D);
        MAX_WITNESSES = b.comment("Only happens if at most this many players are within 32 blocks").defineInRange("maxPlayersNearby", 2, 1, 100);
        b.pop();
        b.push("damage");
        CATCH_DAMAGE = b.comment("Damage when it catches you at the end of the chase (2 = one heart). It can never kill you: you're always left with at least 1 heart. 0 = harmless.")
                .defineInRange("catchDamage", 4.0D, 0.0D, 10.0D);
        b.pop();
        SPEC = b.build();
    }

    private ShadowConfig() {}
}
