package com.mansoority.shadow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** the Shade armour as real 3D pieces (skull mask and horns, ribcage, spiked pauldrons, clawed boots) on the player */
public class ShadeArmorModel extends HumanoidModel<LivingEntity> {
    private static final java.util.Map<EquipmentSlot, ShadeArmorModel> MODELS = new java.util.EnumMap<>(EquipmentSlot.class);
    private final String mesh;

    private ShadeArmorModel(ModelPart root, String mesh) { super(root); this.mesh = mesh; }

    public static ShadeArmorModel of(EquipmentSlot slot) {
        return MODELS.computeIfAbsent(slot, s -> new ShadeArmorModel(
                LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 32).bakeRoot(),
                switch (s) { case HEAD -> "shade_helmet"; case CHEST -> "shade_chestplate"; case LEGS -> "shade_leggings"; default -> "shade_boots"; }));
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        RigidMesh m = RigidMesh.load(mesh);
        if (m == null) return;
        piece(m, "head", head, pose, vc, light, overlay, a);
        piece(m, "body", body, pose, vc, light, overlay, a);
        piece(m, "right_arm", rightArm, pose, vc, light, overlay, a);
        piece(m, "left_arm", leftArm, pose, vc, light, overlay, a);
        piece(m, "right_leg", rightLeg, pose, vc, light, overlay, a);
        piece(m, "left_leg", leftLeg, pose, vc, light, overlay, a);
    }

    private static void piece(RigidMesh m, String name, ModelPart part, PoseStack pose, VertexConsumer vc, int light, int overlay, float a) {
        RigidMesh.Part p = m.get(name);
        if (p == null || !part.visible) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        m.drawPartAll(pose, p, vc, light, overlay, 1F, 1F, 1F, a);
        pose.popPose();
    }
}
