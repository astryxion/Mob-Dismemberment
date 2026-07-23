package me.ichun.mods.mobdismemberment.client.particle;

import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

public class ParticleBlood extends TextureSheetParticle {

    public ParticleBlood(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, boolean isPlayer, SpriteSet sprites) {
        super(level, x, y, z);

        // Matches original 1.12.2 constructor exactly
        this.gravity = 0.06F;
        this.rCol = 1.0F;
        this.gCol = Config.getGreenBlood() && !isPlayer ? 1.0F : 0.0F;
        this.bCol = 0.0F;
        this.quadSize *= 1.2F;

        // multiplyVelocity(1.2F) equivalent
        this.xd = xSpeed * 1.2D;
        this.yd = ySpeed * 1.2D;
        this.zd = zSpeed * 1.2D;

        this.yd += random.nextFloat() * 0.15F;
        this.zd *= 0.4F / (random.nextFloat() * 0.9F + 0.1F);
        this.xd *= 0.4F / (random.nextFloat() * 0.9F + 0.1F);

        this.lifetime = (int) (200F + (20F / (random.nextFloat() * 0.9F + 0.1F)));
        this.hasPhysics = true;

        // Pick sprite from the SpriteSet (uses our custom blood_0 through blood_3 textures)
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        // Matches original 1.12.2 onUpdate() exactly
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.xd != 0.0D && this.zd != 0.0D && !this.onGround) {
            this.yd -= (double) this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.98000001907348633D;
            this.yd *= 0.98000001907348633D;
            this.zd *= 0.98000001907348633D;

            if (this.onGround) {
                this.xd *= 0.69999998807907104D;
                this.zd *= 0.69999998807907104D;
                this.y += 0.2D;
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
