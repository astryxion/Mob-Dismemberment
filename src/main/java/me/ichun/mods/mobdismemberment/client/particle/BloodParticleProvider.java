package me.ichun.mods.mobdismemberment.client.particle;

import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.BasicParticleType;

public class BloodParticleProvider implements IParticleFactory<BasicParticleType> {
    private final IAnimatedSprite sprites;

    public BloodParticleProvider(IAnimatedSprite sprites) {
        this.sprites = sprites;
    }

    @Override
    public Particle createParticle(BasicParticleType type, ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        return new ParticleBlood(level, x, y, z, xSpeed, ySpeed, zSpeed, false, sprites);
    }
}
