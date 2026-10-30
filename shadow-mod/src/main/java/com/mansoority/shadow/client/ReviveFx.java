package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowRegistry;
import com.mansoority.shadow.SkullBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** the revival: the heart drifts down into the skull's crown, then beats in there, veins of black crawling out */
public final class ReviveFx {
    private static ItemStack heart;

    static void render(SkullBlockEntity be, PoseStack pose, MultiBufferSource buf, float pt, float k) {
        if (heart == null) heart = new ItemStack(ShadowRegistry.HOLLOW_HEART.get());
        float t = be.revive + pt;
        float drop = Math.min(1F, t / 50F);
        drop = drop * drop * (3 - 2 * drop);
        float beat = t > 50 ? 1F + 0.25F * (float) Math.pow(Math.max(0F, Math.sin(t * (0.35F + k * 0.4F))), 8) : 1F;
        pose.pushPose();
        pose.translate(0, 9.5 - drop * 4.6, -0.3);                        // from above into the top of the skull
        pose.mulPose(Axis.YP.rotationDegrees(t * (6F - drop * 5F)));
        float s = 2.2F * beat;
        pose.scale(s, s, s);
        Minecraft.getInstance().getItemRenderer().renderStatic(heart, ItemDisplayContext.FIXED, 0xF000F0,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buf, Minecraft.getInstance().level, 0);
        pose.popPose();
    }

    public static void tick(SkullBlockEntity be) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var p = be.getBlockPos();
        var r = mc.level.random;
        float k = be.revive / (float) SkullBlockEntity.REVIVE_LEN;
        int n = (int) (2 + k * 10);
        for (int i = 0; i < n; i++) {             // black veins crawling outward, wisps rising, the body pulling together
            double a = r.nextDouble() * Math.PI * 2, d = 1 + r.nextDouble() * (2 + k * 6);
            mc.level.addParticle(ShadowRegistry.WISP.get(), p.getX() + 0.5 + Math.cos(a) * d, p.getY() + 0.2 + r.nextDouble() * k * 8, p.getZ() + 0.5 + Math.sin(a) * d,
                    -Math.cos(a) * 0.08, 0.03, -Math.sin(a) * 0.08);
        }
        if (be.revive > 50 && be.revive % 3 == 0)
            mc.level.addParticle(ShadowRegistry.DROOL.get(), p.getX() + 0.5 + (r.nextDouble() - 0.5) * 3, p.getY() + 5 + r.nextDouble() * 3, p.getZ() + 0.5 + (r.nextDouble() - 0.5) * 3, 0, 0, 0);
        if (be.revive > 90) ClientFx.kick(0.4F + k);
    }

    private ReviveFx() {}
}
