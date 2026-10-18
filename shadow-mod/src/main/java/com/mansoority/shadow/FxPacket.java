package com.mansoority.shadow;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** server -> client: everything scary happens on the client so only the victim sees/hears it */
public class FxPacket {
    public static final int JUMPSCARE = 1;       // full build-up then the face
    public static final int JUMPSCARE_NOW = 2;   // skip the build-up
    public static final int SOUND_AT = 3;        // positional sound at x,y,z
    public static final int SOUND_UI = 4;        // in-your-head sound
    public static final int SEQUENCE = 5;        // scripted sound event, id in 'sound'

    public final int kind;
    public final int sound;
    public final double x, y, z;
    public final float volume, pitch;

    public FxPacket(int kind, int sound, double x, double y, double z, float volume, float pitch) {
        this.kind = kind; this.sound = sound; this.x = x; this.y = y; this.z = z; this.volume = volume; this.pitch = pitch;
    }

    public static FxPacket of(int kind) { return new FxPacket(kind, 0, 0, 0, 0, 1, 1); }

    public FxPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(kind); buf.writeVarInt(sound);
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(volume); buf.writeFloat(pitch);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mansoority.shadow.client.ClientFx.handle(this));
        ctx.get().setPacketHandled(true);
    }
}
