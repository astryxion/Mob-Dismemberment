package me.ichun.mods.mobdismemberment.client.entity;

import me.ichun.mods.mobdismemberment.client.MobDismembermentClient;
import me.ichun.mods.mobdismemberment.client.helper.ModelHelper;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.TNTEntity;
import net.minecraft.entity.item.minecart.TNTMinecartEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.concurrent.atomic.AtomicInteger;

public class EntityGib extends Entity {
    private static final AtomicInteger CLIENT_ID_COUNTER = new AtomicInteger(-1);

    public ModelRenderer modelPart;
    public ResourceLocation texture;
    public String partName;

    public float partOffsetX;
    public float partOffsetY;
    public float partOffsetZ;

    public float centerX;
    public float centerY;
    public float centerZ;

    public float pitchSpin;
    public float yawSpin;
    public int groundTime;
    public int liveTime;
    public boolean explosion;

    public double motionX;
    public double motionY;
    public double motionZ;

    public EntityGib(EntityType<? extends EntityGib> entityType, World level) {
        super(entityType, level);
        this.setId(CLIENT_ID_COUNTER.getAndDecrement());
        groundTime = 0;
        liveTime = MobDismemberment.clientTicks;
        noCulling = true;
        noPhysics = false;
    }

    /**
     * Create a gib from a specific model part of a dying entity.
     */
    public EntityGib(World level, LivingEntity parent, ModelHelper.PartData partData, Entity explo) {
        this(MobDismembermentClient.GIB_ENTITY.get(), level);

        this.modelPart = partData.part;
        this.partName = partData.name;
        this.texture = ModelHelper.getEntityTexture(parent);

        this.partOffsetX = partData.offsetX;
        this.partOffsetY = partData.offsetY;
        this.partOffsetZ = partData.offsetZ;

        this.centerX = partData.centerX;
        this.centerY = partData.centerY;
        this.centerZ = partData.centerZ;

        liveTime = MobDismemberment.clientTicks;

        double posX = parent.getX() + partOffsetX;
        double posY = parent.getBoundingBox().minY + parent.getBbHeight() / 2.0 + partOffsetY;
        double posZ = parent.getZ() + partOffsetZ;

        setPos(posX, posY, posZ);
        this.yRot = parent.yBodyRot;
        this.xRot = parent.xRot;
        yRotO = parent.yRot;
        xRotO = parent.xRot;

        double motionX = parent.getDeltaMovement().x + (random.nextDouble() - random.nextDouble()) * 0.25D;
        double motionY = parent.getDeltaMovement().y + random.nextDouble() * 0.2D;
        double motionZ = parent.getDeltaMovement().z + (random.nextDouble() - random.nextDouble()) * 0.25D;

        float i = random.nextInt(45) + 5F + random.nextFloat();
        float j = random.nextInt(45) + 5F + random.nextFloat();
        if (random.nextInt(2) == 0) i *= -1;
        if (random.nextInt(2) == 0) j *= -1;
        pitchSpin = i * (float) (motionY + 0.3D);
        yawSpin = j * (float) (Math.sqrt(Math.abs(motionX * motionZ)) + 0.3D);

        if (explo != null) {
            double dist = Math.max(explo.distanceTo(parent) / 2D, 0.1D);
            dist = Math.pow(dist, 2);

            double mag;
            if (explo instanceof TNTEntity || explo instanceof TNTMinecartEntity) {
                mag = 1.0D * (4.0 / dist);
            } else if (explo instanceof CreeperEntity) {
                CreeperEntity creep = (CreeperEntity) explo;
                mag = creep.isPowered() ? 1.0D * (6.0D / dist) : 1.0D * (3.0D / dist);
            } else {
                mag = 1.0D;
            }
            mag = Math.pow(mag, 2) * 0.2D;
            mag = Math.min(mag, 48.0D);

            double mag2 = posY - explo.getY();
            motionX *= mag;
            motionY = mag2 * 0.4D + 0.22D;
            motionZ *= mag;
            explosion = true;
        }

        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
    }

    @Override
    public void tick() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;

        if (explosion) {
            motionX *= 1D / 0.92D;
            motionY *= 1D / 0.95D;
            motionZ *= 1D / 0.92D;
            explosion = false;
        }

        motionY -= 0.08D;

        move(MoverType.SELF, new Vector3d(motionX, motionY, motionZ));

        motionY *= 0.98D;
        motionX *= 0.91D;
        motionZ *= 0.91D;

        if (isInWater()) {
            motionY = 0.3D;
            pitchSpin = 0.0F;
            yawSpin = 0.0F;
        }

        if (isOnGround() || isInWater()) {
            this.xRot = this.xRot + (-90F - (this.xRot % 360F)) / 2;
            motionY *= 0.8D;
            motionX *= 0.8D;
            motionZ *= 0.8D;
        } else {
            this.xRot = this.xRot + pitchSpin;
            this.yRot = this.yRot + yawSpin;
            pitchSpin *= 0.98F;
            yawSpin *= 0.98F;
        }

        PlayerEntity player = Minecraft.getInstance().player;
        if (player != null && this.getBoundingBox().intersects(player.getBoundingBox())) {
            double dx = this.getX() - player.getX();
            double dz = this.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.01) {
                dx /= dist;
                dz /= dist;
                this.motionX += dx * 0.15;
                this.motionZ += dz * 0.15;
                this.motionY += 0.08;
            }
        }

        if (isOnGround() || isInWater()) {
            groundTime++;
            if (groundTime > Config.GIB_GROUND_TIME.get() + 20) {
                remove();
            }
        } else if (groundTime > Config.GIB_GROUND_TIME.get()) {
            groundTime--;
        } else {
            groundTime = 0;
        }

        if (liveTime + Config.GIB_TIME.get() < MobDismemberment.clientTicks) {
            remove();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        return false;
    }

    @Override
    public boolean isAlive() {
        return !this.removed;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void push(Entity entity) {
        if (entity != this && !(entity instanceof EntityGib)) {
            double dx = entity.getX() - this.getX();
            double dz = entity.getZ() - this.getZ();
            double dist = Math.max(dx * dx + dz * dz, 0.01);
            dx /= dist;
            dz /= dist;
            this.motionX -= dx * 0.1;
            this.motionZ -= dz * 0.1;
            this.motionY += 0.05;
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean saveAsPassenger(CompoundNBT tag) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundNBT tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundNBT tag) {
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    public float getInterpolatedYaw(float partialTicks) {
        return MathHelper.lerp(partialTicks, this.yRotO, this.yRot);
    }

    public float getInterpolatedPitch(float partialTicks) {
        return MathHelper.lerp(partialTicks, this.xRotO, this.xRot);
    }
}
