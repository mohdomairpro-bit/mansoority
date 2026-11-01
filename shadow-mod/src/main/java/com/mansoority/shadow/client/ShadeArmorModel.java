package com.mansoority.shadow.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mansoority.shadow.TheShadow;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * the Shade armour: cubes on the vanilla humanoid parts, from armor/shade_armor.json (written by
 * shadow-gear/armor.py together with its texture). baked like any vanilla model, so it costs nothing to draw
 */
public class ShadeArmorModel extends HumanoidModel<LivingEntity> {
    private static final Map<EquipmentSlot, ShadeArmorModel> MODELS = new EnumMap<>(EquipmentSlot.class);
    private static JsonObject def;

    private ShadeArmorModel(ModelPart root) { super(root); }

    public static ShadeArmorModel of(EquipmentSlot slot) {
        return MODELS.computeIfAbsent(slot, s -> new ShadeArmorModel(build(s)));
    }

    public static void invalidate() { MODELS.clear(); def = null; }

    private static JsonObject def() {
        if (def != null) return def;
        try (var r = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(new ResourceLocation(TheShadow.MODID, "armor/shade_armor.json")).openAsReader()) {
            def = JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            TheShadow.LOGGER.error("Could not load the Shade armour model", e);
            def = new JsonObject();
        }
        return def;
    }

    private static float[] f(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        float[] o = new float[a.size()];
        for (int i = 0; i < o.length; i++) o[i] = a.get(i).getAsFloat();
        return o;
    }

    private static ModelPart build(EquipmentSlot slot) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("head", root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO));
        parts.put("right_arm", root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F)));
        parts.put("left_arm", root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F)));
        parts.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F)));
        parts.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F)));
        JsonObject d = def();
        int tw = 128, th = 128;
        if (d.has("texture")) { float[] t = f(d.get("texture")); tw = (int) t[0]; th = (int) t[1]; }
        String key = switch (slot) { case HEAD -> "head"; case CHEST -> "chest"; case LEGS -> "legs"; default -> "feet"; };
        if (d.has("slots") && d.getAsJsonObject("slots").has(key)) {
            int i = 0;
            for (JsonElement el : d.getAsJsonObject("slots").getAsJsonArray(key)) {
                JsonObject c = el.getAsJsonObject();
                PartDefinition p = parts.get(c.get("part").getAsString());
                if (p == null) continue;
                float[] b = f(c.get("box")), uv = f(c.get("uv")), pv = f(c.get("pivot")), rt = f(c.get("rot"));
                float grow = c.has("grow") ? c.get("grow").getAsFloat() : 0F;
                p.addOrReplaceChild("c" + i++, CubeListBuilder.create().texOffs((int) uv[0], (int) uv[1])
                                .addBox(b[0], b[1], b[2], b[3], b[4], b[5], new CubeDeformation(grow)),
                        PartPose.offsetAndRotation(pv[0], pv[1], pv[2], rt[0], rt[1], rt[2]));
            }
        }
        return LayerDefinition.create(mesh, tw, th).bakeRoot();
    }
}
