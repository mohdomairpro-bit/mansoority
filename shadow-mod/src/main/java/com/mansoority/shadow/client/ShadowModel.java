package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.TheShadow;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ShadowModel extends HierarchicalModel<ShadowEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(TheShadow.MODID, "shadow"), "main");

    private final ModelPart root;
    private final ModelPart torso, chest, neck, head, jaw;
    private final ModelPart legL, shinL, legR, shinR;
    private final ModelPart armL, foreL, handL, armR, foreR, handR;

    public ShadowModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        ModelPart hips = body.getChild("hips");
        this.legL = hips.getChild("legL"); this.shinL = legL.getChild("shinL");
        this.legR = hips.getChild("legR"); this.shinR = legR.getChild("shinR");
        this.torso = hips.getChild("torso");
        this.chest = torso.getChild("chest");
        this.neck = chest.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.armL = chest.getChild("armL"); this.foreL = armL.getChild("foreL"); this.handL = foreL.getChild("handL");
        this.armR = chest.getChild("armR"); this.foreR = armR.getChild("foreR"); this.handR = foreR.getChild("handR");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition p_hips = p_body.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(32, 47).addBox(-3F, -1.5F, -2F, 6F, 3F, 4F), PartPose.offsetAndRotation(0F, -25.3F, 0F, 0F, 0F, 0F));
        PartDefinition p_legL = p_hips.addOrReplaceChild("legL", CubeListBuilder.create().texOffs(94, 0).addBox(-1F, 0F, -1F, 2F, 14F, 2F).texOffs(48, 55).addBox(-1.3F, 12.7F, -1.3F, 2.6F, 2.6F, 2.6F), PartPose.offsetAndRotation(-2.2F, 0F, 0F, 0.35F, 0F, 0F));
        PartDefinition p_shinL = p_legL.addOrReplaceChild("shinL", CubeListBuilder.create().texOffs(110, 0).addBox(-1F, 0F, -1F, 2F, 13F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, -0.7F, 0F, 0F));
        PartDefinition p_footL = p_shinL.addOrReplaceChild("footL", CubeListBuilder.create().texOffs(96, 55).addBox(-1.2F, -0.6F, -1.2F, 2.4F, 1.2F, 2.4F).texOffs(52, 47).addBox(-1.7F, -0.4F, -6F, 0.7F, 0.7F, 6F).texOffs(66, 47).addBox(-0.35F, -0.4F, -6F, 0.7F, 0.7F, 6F).texOffs(80, 47).addBox(1F, -0.4F, -6F, 0.7F, 0.7F, 6F), PartPose.offsetAndRotation(0F, 13F, 0F, 0.35F, 0F, 0F));
        PartDefinition p_legR = p_hips.addOrReplaceChild("legR", CubeListBuilder.create().texOffs(102, 0).addBox(-1F, 0F, -1F, 2F, 14F, 2F).texOffs(60, 55).addBox(-1.3F, 12.7F, -1.3F, 2.6F, 2.6F, 2.6F), PartPose.offsetAndRotation(2.2F, 0F, 0F, 0.35F, 0F, 0F));
        PartDefinition p_shinR = p_legR.addOrReplaceChild("shinR", CubeListBuilder.create().texOffs(118, 0).addBox(-1F, 0F, -1F, 2F, 13F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, -0.7F, 0F, 0F));
        PartDefinition p_footR = p_shinR.addOrReplaceChild("footR", CubeListBuilder.create().texOffs(108, 55).addBox(-1.2F, -0.6F, -1.2F, 2.4F, 1.2F, 2.4F).texOffs(94, 47).addBox(-1.7F, -0.4F, -6F, 0.7F, 0.7F, 6F).texOffs(108, 47).addBox(-0.35F, -0.4F, -6F, 0.7F, 0.7F, 6F).texOffs(0, 55).addBox(1F, -0.4F, -6F, 0.7F, 0.7F, 6F), PartPose.offsetAndRotation(0F, 13F, 0F, 0.35F, 0F, 0F));
        PartDefinition p_torso = p_hips.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(72, 0).addBox(-3.5F, -17F, -2F, 7F, 17F, 4F).texOffs(24, 62).addBox(-0.6F, -16F, 1.8F, 1.2F, 1.4F, 1.2F).texOffs(32, 62).addBox(-0.6F, -13.2F, 1.8F, 1.2F, 1.4F, 1.2F).texOffs(40, 62).addBox(-0.6F, -10.4F, 1.8F, 1.2F, 1.4F, 1.2F).texOffs(48, 62).addBox(-0.6F, -7.6F, 1.8F, 1.2F, 1.4F, 1.2F).texOffs(56, 62).addBox(-0.6F, -4.8F, 1.8F, 1.2F, 1.4F, 1.2F).texOffs(64, 62).addBox(-0.6F, -2F, 1.8F, 1.2F, 1.4F, 1.2F), PartPose.offsetAndRotation(0F, -1F, 0F, 0.45F, 0F, 0F));
        PartDefinition p_chest = p_torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(36, 24).addBox(-4.5F, -7F, -2.5F, 9F, 7F, 5F).texOffs(14, 55).addBox(-6.5F, -8F, -2F, 13F, 2.5F, 4F), PartPose.offsetAndRotation(0F, -17F, 0F, 0.4F, 0F, 0F));
        PartDefinition p_neck = p_chest.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(64, 24).addBox(-1.2F, -7F, -1.2F, 2.4F, 7F, 2.4F), PartPose.offsetAndRotation(0F, -7F, -1F, 0.55F, 0F, 0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 0).addBox(-5F, -12F, -5F, 10F, 12F, 10F).texOffs(120, 55).addBox(-4.4F, 0F, -4.9F, 0.5F, 3.1089F, 0.5F).texOffs(72, 62).addBox(-3.42F, 0F, -4.9F, 0.5F, 2.4181F, 0.5F).texOffs(124, 55).addBox(-2.44F, 0F, -4.9F, 0.5F, 3.162F, 0.5F).texOffs(4, 67).addBox(-1.46F, 0F, -4.9F, 0.5F, 1.7293F, 0.5F).texOffs(76, 62).addBox(-0.48F, 0F, -4.9F, 0.5F, 2.5718F, 0.5F).texOffs(80, 62).addBox(0.5F, 0F, -4.9F, 0.5F, 2.2024F, 0.5F).texOffs(84, 62).addBox(1.48F, 0F, -4.9F, 0.5F, 2.883F, 0.5F).texOffs(8, 67).addBox(2.46F, 0F, -4.9F, 0.5F, 1.8792F, 0.5F).texOffs(88, 62).addBox(3.44F, 0F, -4.9F, 0.5F, 2.9946F, 0.5F).texOffs(92, 62).addBox(4.42F, 0F, -4.9F, 0.5F, 2.4703F, 0.5F), PartPose.offsetAndRotation(0F, -7F, 0F, -1.4F, 0F, 0F));
        PartDefinition p_jaw = p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 24).addBox(-4.5F, 0F, -9F, 9F, 4F, 9F).texOffs(96, 62).addBox(-4F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(100, 62).addBox(-3.02F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(104, 62).addBox(-2.04F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(108, 62).addBox(-1.06F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(112, 62).addBox(-0.08F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(116, 62).addBox(0.9F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(120, 62).addBox(1.88F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(124, 62).addBox(2.86F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F).texOffs(0, 67).addBox(3.84F, -2.3F, -8.5F, 0.5F, 2.3F, 0.5F), PartPose.offsetAndRotation(0F, 0F, 4F, 0.45F, 0F, 0F));
        PartDefinition p_armL = p_chest.addOrReplaceChild("armL", CubeListBuilder.create().texOffs(56, 0).addBox(-1F, 0F, -1F, 2F, 20F, 2F).texOffs(0, 47).addBox(-1.6F, -1.6F, -1.6F, 3.2F, 3.2F, 3.2F), PartPose.offsetAndRotation(-6.2F, -6.5F, 0F, -0.85F, 0F, 0.3F));
        PartDefinition p_foreL = p_armL.addOrReplaceChild("foreL", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, 0F, -1F, 2F, 22F, 2F).texOffs(72, 55).addBox(-1.3F, -1.3F, -1.3F, 2.6F, 2.6F, 2.6F), PartPose.offsetAndRotation(0F, 20F, 0F, -0.35F, 0F, -0.3F));
        PartDefinition p_handL = p_foreL.addOrReplaceChild("handL", CubeListBuilder.create().texOffs(0, 62).addBox(-1.3F, 0F, -1.3F, 2.6F, 1.6F, 2.6F).texOffs(76, 24).addBox(-1.6F, 0.6F, -8.5F, 0.5F, 0.5F, 9F).texOffs(96, 24).addBox(-0.65F, 0.6F, -8.5F, 0.5F, 0.5F, 9F).texOffs(0, 37).addBox(0.3F, 0.6F, -8.5F, 0.5F, 0.5F, 9F).texOffs(20, 37).addBox(1.25F, 0.6F, -8.5F, 0.5F, 0.5F, 9F), PartPose.offsetAndRotation(0F, 22F, 0F, 0.6F, 0F, 0F));
        PartDefinition p_armR = p_chest.addOrReplaceChild("armR", CubeListBuilder.create().texOffs(64, 0).addBox(-1F, 0F, -1F, 2F, 20F, 2F).texOffs(16, 47).addBox(-1.6F, -1.6F, -1.6F, 3.2F, 3.2F, 3.2F), PartPose.offsetAndRotation(6.2F, -6.5F, 0F, -0.85F, 0F, -0.3F));
        PartDefinition p_foreR = p_armR.addOrReplaceChild("foreR", CubeListBuilder.create().texOffs(8, 0).addBox(-1F, 0F, -1F, 2F, 22F, 2F).texOffs(84, 55).addBox(-1.3F, -1.3F, -1.3F, 2.6F, 2.6F, 2.6F), PartPose.offsetAndRotation(0F, 20F, 0F, -0.35F, 0F, 0.3F));
        PartDefinition p_handR = p_foreR.addOrReplaceChild("handR", CubeListBuilder.create().texOffs(12, 62).addBox(-1.3F, 0F, -1.3F, 2.6F, 1.6F, 2.6F).texOffs(40, 37).addBox(-1.6F, 0.6F, -8.5F, 0.5F, 0.5F, 9F).texOffs(60, 37).addBox(-0.65F, 0.6F, -8.5F, 0.5F, 0.5F, 9F).texOffs(80, 37).addBox(0.3F, 0.6F, -8.5F, 0.5F, 0.5F, 9F).texOffs(100, 37).addBox(1.25F, 0.6F, -8.5F, 0.5F, 0.5F, 9F), PartPose.offsetAndRotation(0F, 22F, 0F, 0.6F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** cheap deterministic noise so twitches look random but don't flicker */
    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    @Override
    public void setupAnim(ShadowEntity e, float limbSwing, float limbAmount, float age, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        int id = e.getId();
        float stare = Math.min(1F, e.getStare() / (float) ShadowEntity.STARE_LIMIT);
        int vanish = e.getVanish();

        // breathing / swaying
        float breathe = Mth.sin(age * 0.07F);
        chest.xRot += breathe * 0.03F;
        torso.zRot += Mth.sin(age * 0.031F) * 0.03F;
        jaw.xRot += 0.06F + breathe * 0.05F;
        armL.zRot += Mth.sin(age * 0.05F) * 0.04F;
        armR.zRot -= Mth.sin(age * 0.05F + 1F) * 0.04F;

        // random twitches: the head snaps sideways, fingers jerk, the jaw clacks
        int win = (int) (age / 7F);
        float r = hash(win, id);
        float local = age - win * 7F;
        if (stare <= 0F && r < 0.22F && local < 3F) {
            head.zRot += (hash(win, id + 1) - 0.5F) * 1.6F;
            head.yRot += (hash(win, id + 2) - 0.5F) * 0.9F;
            jaw.xRot += hash(win, id + 3) * 0.5F;
            handL.xRot += (hash(win, id + 4) - 0.5F) * 0.8F;
            handR.xRot += (hash(win, id + 5) - 0.5F) * 0.8F;
            foreL.zRot += (hash(win, id + 6) - 0.5F) * 0.5F;
        }

        // being stared at: head locks onto you, neck bends over, jaw unhinges, arms rise
        if (stare > 0F) {
            float s = stare;
            head.zRot += s * 1.45F;
            head.xRot -= s * 0.25F;
            jaw.xRot += s * 0.75F;
            armL.zRot += s * 2.5F;
            armR.zRot -= s * 2.5F;
            foreL.zRot -= s * 0.6F;
            foreR.zRot += s * 0.6F;
            handL.xRot -= s * 0.8F;
            handR.xRot -= s * 0.8F;
            chest.xRot -= s * 0.35F;
            if (s > 0.6F) {  // violent shaking just before it moves
                float j = (s - 0.6F) * 2.5F;
                head.zRot += (hash((int) age, id) - 0.5F) * 0.5F * j;
                head.xRot += (hash((int) age, id + 9) - 0.5F) * 0.4F * j;
                armL.zRot += (hash((int) age, id + 3) - 0.5F) * 0.4F * j;
                armR.zRot += (hash((int) age, id + 4) - 0.5F) * 0.4F * j;
            }
        }

        // creeping walk (only when it actually moves)
        float walk = Math.min(1F, limbAmount * 1.5F);
        legL.xRot += Mth.cos(limbSwing * 0.45F) * 0.7F * walk;
        legR.xRot += Mth.cos(limbSwing * 0.45F + Mth.PI) * 0.7F * walk;
        armL.xRot += Mth.cos(limbSwing * 0.45F + Mth.PI) * 0.5F * walk;
        armR.xRot += Mth.cos(limbSwing * 0.45F) * 0.5F * walk;

        // the chase: arms thrown forward, head down, jaw wide open, legs pumping
        int rush = e.getRush();
        if (rush > 0) {
            float k = Math.min(1F, rush / 6F);
            head.xRot += 0.35F * k;
            jaw.xRot += 1.0F * k;
            armL.xRot -= 1.5F * k;
            armR.xRot -= 1.5F * k;
            armL.zRot -= 0.15F * k;
            armR.zRot += 0.15F * k;
            handL.xRot -= 0.9F * k;
            handR.xRot -= 0.9F * k;
            chest.xRot += 0.2F * k;
            float run = age * 1.6F;
            legL.xRot += Mth.cos(run) * 1.0F * k;
            legR.xRot += Mth.cos(run + Mth.PI) * 1.0F * k;
            shinL.xRot -= Math.max(0F, Mth.sin(run)) * 0.8F * k;
            shinR.xRot -= Math.max(0F, Mth.sin(run + Mth.PI)) * 0.8F * k;
            head.zRot += (hash((int) age, id) - 0.5F) * 0.4F;
        }

        // vanishing: everything spasms
        if (vanish > 0) {
            float v = vanish / 12F;
            for (ModelPart p : new ModelPart[]{head, jaw, armL, armR, foreL, foreR, torso, chest}) {
                p.xRot += (hash((int) age, p.hashCode()) - 0.5F) * 1.2F * v;
                p.zRot += (hash((int) age + 7, p.hashCode()) - 0.5F) * 1.2F * v;
            }
        }
    }
}
