package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation(TheShadow.MODID, "textures/entity/white.png");
    private static final ResourceLocation ATLAS = new ResourceLocation(TheShadow.MODID, "textures/entity/shadow_atlas.png");
    /** blender metres -> blocks. the model is ~2.6 m tall, so this makes it ~8 blocks */
    public static final float SCALE = ShadowEntity.MODEL_SCALE;

    private final Mimic wide, slim;
    private static int tint = 255;

    public ShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
        this.wide = new Mimic(ctx, false);
        this.slim = new Mimic(ctx, true);
    }

    /** someone you know: their skin, their armour, whatever they're holding, their name */
    static final class Mimic extends net.minecraft.client.renderer.entity.LivingEntityRenderer<ShadowEntity, PlayerModel<ShadowEntity>> {
        Mimic(EntityRendererProvider.Context ctx, boolean slim) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
            this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                    new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    ctx.getModelManager()));
            this.addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(ShadowEntity e) {
            java.util.UUID id = e.getMimicId();
            PlayerInfo info = Minecraft.getInstance().getConnection() != null ? Minecraft.getInstance().getConnection().getPlayerInfo(id) : null;
            return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
        }

        @Override
        protected void scale(ShadowEntity e, PoseStack pose, float pt) { pose.scale(0.9375F, 0.9375F, 0.9375F); }

        @Override
        protected boolean shouldShowName(ShadowEntity e) { return !e.getMimicName().isEmpty(); }

        @Override
        protected void renderNameTag(ShadowEntity e, Component name, PoseStack pose, MultiBufferSource buf, int light) {
            super.renderNameTag(e, Component.literal(e.getMimicName()), pose, buf, light);
        }

        @Override
        public void render(ShadowEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
            PlayerModel<ShadowEntity> m = this.getModel();
            m.setAllVisible(true);
            m.rightArmPose = e.getMainHandItem().isEmpty() ? net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY : net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
            m.leftArmPose = e.getOffhandItem().isEmpty() ? net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY : net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
            super.render(e, yaw, pt, pose, buf, light);
        }
    }

    private void renderMimic(ShadowEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        java.util.UUID id = e.getMimicId();
        PlayerInfo info = Minecraft.getInstance().getConnection() != null ? Minecraft.getInstance().getConnection().getPlayerInfo(id) : null;
        boolean isSlim = info != null ? "slim".equals(info.getModelName()) : "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        (isSlim ? slim : wide).render(e, e.getYRot(), pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        return ATLAS;
    }

    @Override
    public void render(ShadowEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.isMimic()) { renderMimic(e, partialTick, pose, buffers, light); return; }
        ShadowMesh mesh = ShadowMesh.get();
        if (mesh == null || e.isHidden()) return;
        float age = e.tickCount + partialTick;
        ShadowPose.apply(mesh, e, age, partialTick, net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        float grow = 1F + Math.min(1F, e.getStare() / (float) ShadowEntity.STARE_LIMIT) * 0.12F;
        // pulled into the portal: shrinks and twists away
        float vt = e.getVanish() > 0 ? Mth.clamp((e.getVanish() + partialTick - 8F) / 18F, 0F, 1F) : 0F;
        grow *= 1F - vt * vt * (3F - 2F * vt) * 0.97F;
        // while it's lurking it's a pale shape in the dark, not a lit-up white statue
        float wantTint = e.getStage() == 0 && e.getRush() <= 0 ? 0.38F + e.smoothStare * 0.62F : 1F;
        e.tint += (wantTint - e.tint) * 0.1F;
        tint = (int) (255 * e.tint);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw + vt * vt * 540F));
        if (vt > 0F) pose.translate(0F, e.getBbHeight() * 0.5F * vt, 0F);
        pose.scale(SCALE * grow, SCALE * grow, SCALE * grow);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS));
        for (ShadowMesh.Part root : mesh.roots) draw(root, pose, solid, light, false);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
        for (ShadowMesh.Part root : mesh.roots) draw(root, pose, glow, 0xF000F0, true);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    private static void draw(ShadowMesh.Part p, PoseStack pose, VertexConsumer vc, int light, boolean glowPass) {
        pose.pushPose();
        if (p.ty != 0F) pose.translate(0F, p.ty, 0F);
        if (p.xRot != 0F || p.yRot != 0F || p.zRot != 0F) {
            pose.translate(p.px, p.py, p.pz);
            pose.mulPose(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot));
            pose.translate(-p.px, -p.py, -p.pz);
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int[] list = glowPass ? p.glowTris : p.solidTris;
        for (int t : list) {
            for (int k = 0; k < 4; k++) {
                int v = t * 3 + Math.min(k, 2);      // triangle sent as a quad with the last corner repeated
                vc.vertex(m, p.pos[v * 3], p.pos[v * 3 + 1], p.pos[v * 3 + 2])
                        .color(glowPass ? 255 : tint, glowPass ? 255 : tint, glowPass ? 255 : tint, 255)
                        .uv(glowPass ? 0.5F : p.uv[v * 2], glowPass ? 0.5F : p.uv[v * 2 + 1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(n, p.nrm[v * 3], p.nrm[v * 3 + 1], p.nrm[v * 3 + 2])
                        .endVertex();
            }
        }
        for (ShadowMesh.Part c : p.children) draw(c, pose, vc, light, glowPass);
        pose.popPose();
    }
}
