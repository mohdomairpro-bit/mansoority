package com.mansoority.shadow.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** thick saliva: hangs for a moment, stretches, then falls and splats */
public class DroolParticle extends TextureSheetParticle {
    private int hang;

    DroolParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean bile) {
        super(level, x, y, z, 0, 0, 0);
        this.xd = vx; this.yd = vy; this.zd = vz;
        this.hang = vy > 0.001 ? 0 : 4 + random.nextInt(10);
        this.lifetime = 60 + random.nextInt(30);
        this.quadSize = 0.06F + random.nextFloat() * 0.07F;
        this.gravity = 0.9F;
        this.alpha = 0.9F;
        this.hasPhysics = true;
        float g = 0.85F + random.nextFloat() * 0.15F;
        if (bile) { this.setColor(0.55F * g, 0.62F * g, 0.18F * g); this.quadSize *= 1.6F; this.hang = 0; this.lifetime = 40 + random.nextInt(20); }
        else this.setColor(g, g * 0.97F, g * 0.85F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        if (hang > 0) { hang--; this.xo = x; this.yo = y; this.zo = z; this.quadSize *= 1.03F; this.age++; return; }
        super.tick();
        if (this.onGround) { this.xd = this.zd = 0; this.quadSize = Math.min(this.quadSize * 1.05F, 0.22F); this.alpha *= 0.94F; }
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites; private final boolean bile;
        public Provider(SpriteSet sprites) { this(sprites, false); }
        public Provider(SpriteSet sprites, boolean bile) { this.sprites = sprites; this.bile = bile; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new DroolParticle(level, x, y, z, vx, vy, vz, sprites, bile);
        }
    }
}
