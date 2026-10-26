package com.mansoority.shadow;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Hollow Heart. What was left inside it. It still beats, but only in the dark.
 * Held in your off hand: you see in the dark and the dark can't blind you. Pressed into its skull: it comes back.
 */
public class HollowHeartItem extends Item {
    public HollowHeartItem() { super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); }

    @Override public boolean isFoil(ItemStack stack) { return false; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || !(e instanceof Player p) || p.getOffhandItem() != stack || level.getGameTime() % 20 != 0) return;
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
        p.removeEffect(MobEffects.DARKNESS);
        p.removeEffect(MobEffects.BLINDNESS);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("It still beats. Only in the dark.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tip.add(Component.empty());
        tip.add(Component.literal("Off hand: see in the dark; darkness and blindness can't touch you").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Press it into Mansoority's skull to bring it back").withStyle(ChatFormatting.DARK_RED));
    }
}
