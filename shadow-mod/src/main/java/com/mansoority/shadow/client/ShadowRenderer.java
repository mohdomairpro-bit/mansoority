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

public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation(TheShadow.MODID, "textures/entity/white.png");
    private static final ResourceLocation ATLAS = new ResourceLocation(TheShadow.MODID, "textures/entity/shadow_atlas.png");
    /** blender metres -> blocks. the model is ~2.6 m tall, so this makes it ~8 blocks */
    public static final float SCALE = ShadowEntity.MODEL_SCALE;

    private final Mimic wide, slim;
    static int tint = 255;

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
        protected boolean isBodyVisible(ShadowEntity e) { return true; }

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

    /** noCulling keeps EntityCulling's occlusion test off it; we still skip it when it's outside the view */
    @Override
    public boolean shouldRender(ShadowEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (!e.shouldRender(x, y, z)) return false;
        return frustum.isVisible(e.getBoundingBoxForCulling().inflate(1.0));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        return ATLAS;
    }

    /** triangles as triangles (the stock entity types want quads: a quarter more vertices for nothing) */
    static final class Types extends RenderType {
        private Types(String a, com.mojang.blaze3d.vertex.VertexFormat b, com.mojang.blaze3d.vertex.VertexFormat.Mode c, int d, boolean e, boolean f, Runnable g, Runnable h) { super(a, b, c, d, e, f, g, h); }
        static final RenderType SKIN = create("theshadow_skin", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, 1 << 18, true, false,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new TextureStateShard(ATLAS, false, false)).setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true));
        static final RenderType GLOW = create("theshadow_glow", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, 1 << 12, false, true,
                CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(WHITE, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }

    @Override
    public void render(ShadowEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.isMimic()) { renderMimic(e, partialTick, pose, buffers, light); return; }
        RingRenderer.renderWall(e, pose, buffers, partialTick);
        PortalRenderer.renderPortal(e, pose, buffers, partialTick);
        if (e.isHidden()) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        double ex = Mth.lerp(partialTick, e.xo, e.getX()), ey = Mth.lerp(partialTick, e.yo, e.getY()), ez = Mth.lerp(partialTick, e.zo, e.getZ());
        double dist2 = camPos.distanceToSqr(ex, ey, ez);
        boolean far = dist2 > 30 * 30 && e.getGrab() <= 0;
        ShadowMesh mesh = far ? ShadowMesh.lod() : ShadowMesh.get();
        if (mesh == null) return;
        float age = e.tickCount + partialTick;
        ShadowPose.apply(mesh, e, age, partialTick, camPos);
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        float grow = 1F + Math.min(1F, e.getStare() / (float) ShadowEntity.STARE_LIMIT) * 0.12F;
        // pulled into the portal: shrinks and twists away
        float vt = e.getVanish() > 0 ? Mth.clamp((e.getVanish() + partialTick - 8F) / 18F, 0F, 1F) : 0F;
        grow *= 1F - vt * vt * (3F - 2F * vt) * 0.97F;
        // while it's lurking it's a pale shape in the dark, not a lit-up white statue
        float wantTint = e.getStage() == 0 && e.getRush() <= 0 ? 0.38F + e.smoothStare * 0.62F : 1F;
        e.tint += (wantTint - e.tint) * 0.1F;
        tint = (int) (255 * e.tint);
        com.mansoority.shadow.MansoorityBoss boss = e instanceof com.mansoority.shadow.MansoorityBoss b ? b : null;
        boolean black = boss != null && boss.has(com.mansoority.shadow.MansoorityBoss.F_BLACK);
        if (black) tint = 34;                                   // the thing that tore out of it: black, with red eyes
        glowR = 255; glowG = black ? 40 : 255; glowB = black ? 30 : 255;
        // feet on the real ground (close enough to see it, standing on something)
        ShadowSkeleton.fk(mesh);
        if (e.animState instanceof ShadowAnim.State st && ShadowAnim.get() != null && dist2 < 48 * 48 && vt <= 0F && mc.level != null) {
            int contact = st.contact(ShadowAnim.get());
            int touched = ShadowSkeleton.ground(mesh, st, contact, mc.level, ex, ey, ez, bodyYaw, SCALE * grow, Math.max(0.2F, frameDt(e)), e.getGrab() <= 0);
            if (touched != 0 && age - st.lastStepAge > 2F) { st.lastStepAge = age; Footsteps.step(e, mesh, touched, ex, ey, ez, bodyYaw, SCALE * grow, dist2); }
        }
        ShadowSkeleton.skin(mesh);
        pose.pushPose();
        // two friends together never see it in the same place: while it's lurking, everyone else sees it somewhere else
        if (e.getStage() == 0 && mc.player != null && mc.player.getId() != e.getTargetEntityId() && mc.level != null) {
            net.minecraft.world.entity.Entity t = mc.level.getEntity(e.getTargetEntityId());
            if (t != null) {
                double rx = e.getX() - t.getX(), rz = e.getZ() - t.getZ();
                double a = Math.toRadians(70 + Math.floorMod(mc.player.getId() * 37, 140));
                double nx = rx * Math.cos(a) - rz * Math.sin(a), nz = rx * Math.sin(a) + rz * Math.cos(a);
                pose.translate(nx - rx, 0, nz - rz);
            }
        }
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw + vt * vt * 540F));
        if (vt > 0F) pose.translate(0F, e.getBbHeight() * 0.5F * vt, 0F);
        if (boss != null && boss.has(com.mansoority.shadow.MansoorityBoss.F_WALL)) {     // on the side of a pillar, belly to the stone
            pose.translate(0F, 3.5F, -1.2F);
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.translate(0F, -3.5F, 0F);
        }
        pose.scale(SCALE * grow, SCALE * grow, SCALE * grow);
        emit(mesh, mesh.solid, pose, buffers.getBuffer(Types.SKIN), light, false);
        if (mesh.glow.length > 0) emit(mesh, mesh.glow, pose, buffers.getBuffer(Types.GLOW), 0xF000F0, true);
        if (!far && dist2 < 40 * 40) Saliva.render(e, mesh, pose, buffers, light, age, ex, ey, ez, bodyYaw, SCALE * grow);
        if (boss != null) BossLook.inModel(boss, mesh, pose, buffers, light, age);
        pose.popPose();
        if (boss != null) BossLook.healthBar(boss, pose, buffers, this.entityRenderDispatcher.cameraOrientation(), this.getFont(), dist2);
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** render frames between ticks (ground smoothing runs per frame, not per tick) */
    private static float frameDt(ShadowEntity e) {
        long now = net.minecraft.Util.getMillis();
        float d = e.lastFrameMs == 0 ? 1F : Math.min(5F, (now - e.lastFrameMs) / 50F);
        e.lastFrameMs = now;
        return d;
    }

    static int glowR = 255, glowG = 255, glowB = 255;

    static void emit(ShadowMesh m, int[] tris, PoseStack pose, VertexConsumer vc, int light, boolean glowPass) {
        Matrix4f mat = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float[] P = m.outPos, N = m.outNrm, UV = m.uv;
        int r = glowPass ? glowR : tint, g = glowPass ? glowG : tint, b = glowPass ? glowB : tint;
        for (int v : tris) {
            vc.vertex(mat, P[v * 3], P[v * 3 + 1], P[v * 3 + 2])
                    .color(r, g, b, 255)
                    .uv(glowPass ? 0.5F : UV[v * 2], glowPass ? 0.5F : UV[v * 2 + 1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(nm, N[v * 3], N[v * 3 + 1], N[v * 3 + 2])
                    .endVertex();
        }
    }
}
