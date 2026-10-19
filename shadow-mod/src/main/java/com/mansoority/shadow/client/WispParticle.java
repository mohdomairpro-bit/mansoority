package com.mansoority.shadow.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** a slow, soft black wisp that swells and fades - used for the ring and when it vanishes */
public class WispParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float maxSize;

    WispParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.xd = vx * 0.3 + (random.nextDouble() - 0.5) * 0.01;
        this.yd = 0.006 + random.nextDouble() * 0.012 + vy * 0.2;
        this.zd = vz * 0.3 + (random.nextDouble() - 0.5) * 0.01;
        this.lifetime = 50 + random.nextInt(40);
        this.maxSize = 0.9F + random.nextFloat() * 1.3F;
        this.quadSize = maxSize * 0.4F;
        this.roll = random.nextFloat() * 6.28F; this.oRoll = roll;
        float g = 0.6F + random.nextFloat() * 0.4F;
        this.setColor(g, g * 0.9F, g * 0.95F);
        this.alpha = 0F;
        this.hasPhysics = false;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = this.age / (float) this.lifetime;
        this.alpha = t < 0.2F ? t / 0.2F * 0.85F : 0.85F * (1F - (t - 0.2F) / 0.8F);
        this.quadSize = maxSize * (0.4F + 0.6F * t);
        this.oRoll = this.roll; this.roll += 0.01F;
        this.xd *= 0.98; this.zd *= 0.98;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new WispParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
