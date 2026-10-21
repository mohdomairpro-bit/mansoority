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
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
        return super.hurtEnemy(stack, target, attacker);
    }
}
