package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.ShadowRegistry;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * the small things: lights going out, a door you shut standing open, a chest creaking, it standing right behind you
 * for an instant, your own death in chat, its mark on you. all client-side and put back afterwards - the world itself
 * is never changed.
 */
public final class ClientScares {
    private record Fake(BlockPos pos, BlockState orig, int until) {}
    private static final List<Fake> FAKES = new ArrayList<>();
    private static int tick;
    private static int mirrorArmed = -1, mirrorShow = -1;
    private static ShadowEntity mirror;
    private static int deathT = -1;
    private static int markLeft;
    private static final ResourceLocation HAND = new ResourceLocation(TheShadow.MODID, "textures/gui/hand.png");

    // ---------------------------------------------------------------- lights out, doors, chests
    public static void polter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        BlockPos c = mc.player.blockPosition();
        List<BlockPos> lights = new ArrayList<>(), doors = new ArrayList<>(), chests = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 5, 12))) {
            BlockState s = mc.level.getBlockState(q);
            if (s.getBlock() instanceof TorchBlock || s.getBlock() instanceof LanternBlock
                    || (s.getBlock() instanceof AbstractCandleBlock && s.getValue(BlockStateProperties.LIT))) lights.add(q.immutable());
            else if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    && !s.getValue(DoorBlock.OPEN)) doors.add(q.immutable());
            else if (s.getBlock() instanceof ChestBlock) chests.add(q.immutable());
        }
        var r = mc.level.random;
        boolean did = false;
        // the nearest few lights go out one after another
        lights.sort((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c)));
        for (int i = 0; i < Math.min(4, lights.size()); i++) {
            BlockPos q = lights.get(i);
            BlockState s = mc.level.getBlockState(q);
            BlockState out = s.getBlock() instanceof AbstractCandleBlock ? s.setValue(BlockStateProperties.LIT, false) : Blocks.AIR.defaultBlockState();
            fake(q, s, out, 20 * 14 + i * 6, i * 6);
            did = true;
        }
        if (!doors.isEmpty()) {
            BlockPos q = doors.get(r.nextInt(doors.size()));
            BlockState s = mc.level.getBlockState(q), up = mc.level.getBlockState(q.above());
            fake(q, s, s.setValue(DoorBlock.OPEN, true), 20 * 12, 30);
            if (up.getBlock() instanceof DoorBlock) fake(q.above(), up, up.setValue(DoorBlock.OPEN, true), 20 * 12, 30);
            did = true;
        }
        if (!chests.isEmpty()) {
            BlockPos q = chests.get(r.nextInt(chests.size()));
            pendingChest = q; chestAt = tick + 50;
            did = true;
        }
        if (!did) ClientFx.handle(com.mansoority.shadow.FxPacket.of(com.mansoority.shadow.FxPacket.SEQUENCE));
    }

    private static BlockPos pendingChest; private static int chestAt = -1;
    private record Pending(BlockPos pos, BlockState orig, BlockState to, int at, int until) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    private static void fake(BlockPos q, BlockState orig, BlockState to, int dur, int delay) {
        PENDING.add(new Pending(q, orig, to, tick + delay, tick + delay + dur));
    }

    // ---------------------------------------------------------------- the lair, risen out of the ground on your screen only
    public static void fakeLair(BlockPos origin, int dur) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try (java.io.InputStream in = TheShadow.class.getResourceAsStream("/data/theshadow/structures/lair.nbt")) {
            if (in == null) return;
            net.minecraft.nbt.CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(in);
            var blocks = mc.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
            net.minecraft.nbt.ListTag pal = root.getList("palette", 10);
            BlockState[] states = new BlockState[pal.size()];
            for (int i = 0; i < pal.size(); i++) states[i] = net.minecraft.nbt.NbtUtils.readBlockState(blocks, pal.getCompound(i));
            net.minecraft.nbt.ListTag list = root.getList("blocks", 10);
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.nbt.CompoundTag b = list.getCompound(i);
                net.minecraft.nbt.ListTag ps = b.getList("pos", 3);
                BlockPos q = origin.offset(ps.getInt(0), ps.getInt(1), ps.getInt(2));
                BlockState to = states[b.getInt("state")];
                fake(q, mc.level.getBlockState(q), to, dur - ps.getInt(1) * 8, ps.getInt(1) * 8);   // rises up, layer by layer
            }
        } catch (Exception ex) { TheShadow.LOGGER.warn("fake lair failed", ex); }
    }

    // ---------------------------------------------------------------- mirror: one frame, right behind you
    public static void mirror() {
        mirrorArmed = tick + 20 * 60;          // if you're in first person, it waits until you look
    }

    // ---------------------------------------------------------------- your own death, in your chat
    public static void deathMsg() { deathT = 0; }

    public static void mark(int seconds) { markLeft = seconds * 20; }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { FAKES.clear(); PENDING.clear(); mirror = null; return; }
        tick++;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (tick >= p.at) {
                mc.level.setBlock(p.pos, p.to, 0);
                if (FAKES.size() > 64) { FAKES.add(new Fake(p.pos, p.orig, p.until)); PENDING.remove(i); continue; }   // the lair: no per-block sounds
                if (p.to.isAir() || p.to.getBlock() instanceof AbstractCandleBlock) {
                    mc.level.playLocalSound(p.pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1F, 0.6F, false);
                    mc.level.addParticle(ParticleTypes.SMOKE, p.pos.getX() + 0.5, p.pos.getY() + 0.6, p.pos.getZ() + 0.5, 0, 0.03, 0);
                } else mc.level.playLocalSound(p.pos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1F, 0.5F, false);
                FAKES.add(new Fake(p.pos, p.orig, p.until));
                PENDING.remove(i);
            }
        }
        for (int i = FAKES.size() - 1; i >= 0; i--) {
            Fake f = FAKES.get(i);
            if (tick >= f.until) {
                // put it back exactly as the server has it
                mc.level.setBlock(f.pos, f.orig, 0);
                FAKES.remove(i);
            }
        }
        if (chestAt >= 0 && tick >= chestAt && pendingChest != null) {
            BlockState s = mc.level.getBlockState(pendingChest);
            mc.level.blockEvent(pendingChest, s.getBlock(), 1, 1);
            mc.level.playLocalSound(pendingChest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8F, 0.55F, false);
            if (tick >= chestAt + 50) {
                mc.level.blockEvent(pendingChest, s.getBlock(), 1, 0);
                mc.level.playLocalSound(pendingChest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.8F, 0.5F, false);
                chestAt = -1; pendingChest = null;
            }
        }
        if (mirrorArmed >= 0) {
            if (tick > mirrorArmed) mirrorArmed = -1;
            else if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK || CinematicCam.active()) {
                mirrorArmed = -1; mirrorShow = tick + 3;
                mirror = ShadowRegistry.SHADOW.get().create(mc.level);
                if (mirror != null) {
                    mirror.clientStage(1);
                    float yaw = mc.player.getYRot();
                    Vec3 back = Vec3.directionFromRotation(0, yaw).scale(-2.2);
                    mirror.setPos(mc.player.getX() + back.x, mc.player.getY(), mc.player.getZ() + back.z);
                    mirror.setYRot(yaw); mirror.yRotO = yaw; mirror.yBodyRot = yaw; mirror.yBodyRotO = yaw;
                    mirror.xo = mirror.getX(); mirror.yo = mirror.getY(); mirror.zo = mirror.getZ();
                }
            }
        }
        if (mirrorShow >= 0 && tick > mirrorShow) {
            mirrorShow = -1; mirror = null;
            mc.level.playLocalSound(mc.player.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.5F, 0.4F, false);
        }
        if (deathT >= 0 && ++deathT > 20 * 7) deathT = -1;
        if (markLeft > 0) markLeft--;
    }

    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mirror == null) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        var buf = mc.renderBuffers().bufferSource();
        mc.getEntityRenderDispatcher().render(mirror, mirror.getX() - cam.x, mirror.getY() - cam.y, mirror.getZ() - cam.z,
                mirror.getYRot(), e.getPartialTick(), ps, buf, 0x00A000A0);
        buf.endBatch();
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (deathT >= 0 && mc.player != null) {
            // looks exactly like a chat line (bottom-left), then it's just gone
            float a = deathT < 120 ? 1F : 1F - (deathT - 120) / 20F;
            if (a > 0.02F) {
                String msg = mc.player.getGameProfile().getName() + " was taken by Mansoority";
                int y = h - 40 - 9, tw = mc.font.width(msg);
                g.fill(2, y - 1, 4 + tw + 4, y + 9, ((int) (a * 0.5F * 255) << 24));
                g.drawString(mc.font, Component.literal(msg), 4, y, 0xFFFFFF | ((int) (a * 255) << 24), true);
            }
        }
        if (markLeft > 0) {
            // a faint black handprint at the edge of your vision while you carry its mark
            float a = Math.min(1F, markLeft / 200F) * (0.10F + 0.04F * Mth.sin((mc.level != null ? mc.level.getGameTime() : 0) * 0.05F));
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(0.05F, 0.05F, 0.05F, a);
            int s = (int) (h * 0.55F);
            g.blit(HAND, w - s + s / 4, h - s + s / 5, s, s, 512F, 0F, -512, 512, 512, 512);
            g.setColor(1F, 1F, 1F, 1F);
        }
    }

    private ClientScares() {}
}
