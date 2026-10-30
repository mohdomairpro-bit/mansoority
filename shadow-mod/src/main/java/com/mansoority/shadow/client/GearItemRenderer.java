package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** the blade and the shield drawn from their 3D models wherever an item is drawn (hand, inventory, ground, frame) */
public class GearItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static GearItemRenderer INSTANCE;

    public static GearItemRenderer get() {
        if (INSTANCE == null) { Minecraft mc = Minecraft.getInstance(); INSTANCE = new GearItemRenderer(mc); }
        return INSTANCE;
    }

    private GearItemRenderer(Minecraft mc) { super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        pose.pushPose();
        if (stack.is(ShadowRegistry.SHADOW_BLADE.get())) {
            // laid out like a sword sprite (grip bottom-left, tip top-right) so the usual sword holds fit it; a lot bigger
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.ZP.rotationDegrees(-45F));
            float s = ctx == ItemDisplayContext.GUI ? 0.6F : 0.74F;
            pose.scale(s, s, s);
            pose.translate(0F, -0.66F, 0F);
            Gear.draw("shadow_blade", pose, buf, light, overlay);
        } else if (stack.is(ShadowRegistry.PALE_WARD.get())) {
            // same space as the vanilla shield model: centred on the origin, the face toward +z
            float s = 1.12F;
            pose.scale(s, s, s);
            pose.translate(0F, -0.07F, 0.05F);
            Gear.draw("pale_ward", pose, buf, light, overlay);
        }
        pose.popPose();
    }
}
