package com.mansoority.shadow;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/** the shades' black glowing sword: whoever it cuts sees the dark close in */
public class ShadowBladeItem extends SwordItem {
    public ShadowBladeItem() {
        super(Tiers.NETHERITE, 4, -2.4F, new Properties().rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return com.mansoority.shadow.client.GearItemRenderer.get(); }

            /** a heavy diagonal cut: wound up over the shoulder, swept down and across, with follow-through */
            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
                int i = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
                pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
                if (swing > 0F) {
                    float up = net.minecraft.util.Mth.sin(Math.min(1F, swing * 3F) * (float) Math.PI * 0.5F) * (1F - swing);
                    float cut = swing < 0.25F ? 0F : (swing - 0.25F) / 0.75F;
                    cut = 1F - (1F - cut) * (1F - cut);
                    float arc = net.minecraft.util.Mth.sin(swing * (float) Math.PI);
                    pose.translate(i * (0.12F * up - 0.32F * cut * (1F - cut * 0.5F)), 0.22F * up - 0.18F * cut, -0.12F * arc);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(i * (25F * up - 70F * cut)));
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(i * (35F * up - 55F * cut)));
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-50F * up + 35F * cut));
                }
                return true;
            }
        });
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
        return super.hurtEnemy(stack, target, attacker);
    }
}
