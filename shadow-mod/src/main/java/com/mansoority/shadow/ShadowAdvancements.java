package com.mansoority.shadow;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** the Mansoority advancement tab: every entry is awarded from code (criterion "done") */
public final class ShadowAdvancements {
    private static final String[] ALL = {"looked", "run", "mostly", "dragged", "friends", "sleep", "lair", "boss"};

    public static void award(ServerPlayer p, String name) {
        give(p, "root");
        give(p, name);
        for (String n : ALL) if (!has(p, n)) return;
        give(p, "name");                                // saw everything it can do
    }

    private static boolean has(ServerPlayer p, String name) {
        Advancement a = p.server.getAdvancements().getAdvancement(new ResourceLocation(TheShadow.MODID, name));
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
    }

    private static void give(ServerPlayer p, String name) {
        Advancement a = p.server.getAdvancements().getAdvancement(new ResourceLocation(TheShadow.MODID, name));
        if (a == null) return;
        AdvancementProgress pr = p.getAdvancements().getOrStartProgress(a);
        if (!pr.isDone()) for (String c : pr.getRemainingCriteria()) p.getAdvancements().award(a, c);
    }

    private ShadowAdvancements() {}
}
