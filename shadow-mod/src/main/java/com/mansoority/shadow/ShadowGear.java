package com.mansoority.shadow;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shade armour (between diamond and netherite, made for the dark) and the Pale Ward (nothing shot at you lands) */
public final class ShadowGear {

    public enum Shade implements ArmorMaterial {
        INSTANCE;
        private static final int[] DUR = {13, 15, 16, 11};                     // boots, legs, chest, helmet (vanilla order)
        @Override public int getDurabilityForType(ArmorItem.Type t) { return DUR[t.getSlot().getIndex()] * 35; }   // diamond 33, netherite 37
        @Override public int getDefenseForType(ArmorItem.Type t) {
            return switch (t) { case HELMET -> 3; case CHESTPLATE -> 8; case LEGGINGS -> 6; case BOOTS -> 3; };
        }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(ShadowRegistry.SHADE_ESSENCE.get()); }
        @Override public String getName() { return TheShadow.MODID + ":shade"; }
        @Override public float getToughness() { return 2.5F; }                 // diamond 2, netherite 3
        @Override public float getKnockbackResistance() { return 0.05F; }      // diamond 0, netherite 0.1
    }

    public static class ShadeArmor extends ArmorItem {
        public ShadeArmor(Type t) { super(Shade.INSTANCE, t, new Properties().rarity(Rarity.RARE)); }

        /** the 3D pieces are coloured from the shared palette */
        @Override
        public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, EquipmentSlot slot, String type) {
            return TheShadow.MODID + ":textures/entity/minion_palette.png";
        }

        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(LivingEntity e, ItemStack stack, EquipmentSlot slot,
                                                                                          net.minecraft.client.model.HumanoidModel<?> original) {
                    return com.mansoority.shadow.client.ShadeArmorModel.of(slot);
                }
            });
        }
        @Override
        public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.translatable("item.theshadow.shade_armor.desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            tip.add(Component.translatable("item.theshadow.shade_armor.set").withStyle(ChatFormatting.GRAY));
        }
    }

    public static class PaleWard extends net.minecraft.world.item.ShieldItem {
        public PaleWard() { super(new Properties().durability(520).rarity(Rarity.RARE)); }

        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return com.mansoority.shadow.client.GearItemRenderer.get(); }
            });
        }
        @Override
        public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.translatable("item.theshadow.pale_ward.desc").withStyle(ChatFormatting.GRAY));
        }
        @Override public boolean isValidRepairItem(ItemStack s, ItemStack r) { return r.is(ShadowRegistry.PALE_SILK.get()); }
    }

    public static int pieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (e.getItemBySlot(s).getItem() instanceof ShadeArmor) n++;
        return n;
    }

    static ItemStack ward(LivingEntity e) {
        if (e.getOffhandItem().getItem() instanceof PaleWard) return e.getOffhandItem();
        if (e.getMainHandItem().getItem() instanceof PaleWard) return e.getMainHandItem();
        return ItemStack.EMPTY;
    }

    /** raised: turns any projectile aside, from any direction (plain shields only stop what's in front). true = it doesn't land */
    public static boolean warded(Player p, Entity projectile) {
        ItemStack w = p.isUsingItem() && p.getUseItem().getItem() instanceof PaleWard ? p.getUseItem() : ItemStack.EMPTY;
        if (w.isEmpty() || p.level().isClientSide) return !w.isEmpty();
        InteractionHand hand = p.getUsedItemHand();
        w.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(hand));
        p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1F, 0.8F + p.getRandom().nextFloat() * 0.3F);
        if (projectile != null && projectile.isAlive()) {
            Vec3 v = projectile.getDeltaMovement();
            projectile.setDeltaMovement(v.scale(-0.35).add(0, 0.15, 0));       // knocked back the way it came
            if (projectile instanceof Projectile pr) pr.setOwner(p);
        }
        return true;
    }

    /** server, once a second: the full set keeps the dark off you */
    static void tick(ServerPlayer p) {
        int n = pieces(p);
        if (n == 4) {
            p.removeEffect(MobEffects.DARKNESS);
            p.removeEffect(MobEffects.BLINDNESS);
            if (p.level().getMaxLocalRawBrightness(p.blockPosition()) < 6)
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
    }

    /** each piece takes 6% off what its creatures do to you */
    static float reduce(LivingEntity victim, Entity attacker, float amount) {
        if (attacker instanceof ShadowEntity || attacker instanceof ShadeEntity || attacker instanceof PaleSpider)
            return amount * (1F - 0.06F * pieces(victim));
        return amount;
    }

    private ShadowGear() {}
}
