package me.ichun.mods.mobdismemberment.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class BloodParticleProvider implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet sprites;

    public BloodParticleProvider(SpriteSet sprites) {
        this.sprites = sprites;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
        return new ParticleBlood(level, x, y, z, xSpeed, ySpeed, zSpeed, false, sprites, random);
    }
}
