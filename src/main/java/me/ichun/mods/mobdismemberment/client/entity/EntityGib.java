package me.ichun.mods.mobdismemberment.client.entity;

import me.ichun.mods.mobdismemberment.client.helper.ModelHelper;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

public class EntityGib extends Entity {
    // Counter for generating unique negative IDs for client-side entities
    private static final AtomicInteger CLIENT_ID_COUNTER = new AtomicInteger(-1);

    // Model part data - stores the actual bone to render
    public ModelPart modelPart;
    public ResourceLocation texture;
    public String partName;

    // Position offset from entity center where this part was
    public float partOffsetX;
    public float partOffsetY;
    public float partOffsetZ;

    // Geometric center offset for proper rotation pivot
    public float centerX;
    public float centerY;
    public float centerZ;

    // Physics
    public float pitchSpin;
    public float yawSpin;
    public int groundTime;
    public int liveTime;
    public boolean explosion;

    // Store motion locally since client-side entities may not track deltaMovement properly
    public double motionX;
    public double motionY;
    public double motionZ;

    public EntityGib(EntityType<? extends EntityGib> entityType, Level level) {
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
    public EntityGib(Level level, LivingEntity parent, ModelHelper.PartData partData, Entity explo) {
        this(MobDismemberment.GIB_ENTITY.get(), level);

        // Store the model part and texture
        this.modelPart = partData.part;
        this.partName = partData.name;
        this.texture = ModelHelper.getEntityTexture(parent);

        // Store part offsets
        this.partOffsetX = partData.offsetX;
        this.partOffsetY = partData.offsetY;
        this.partOffsetZ = partData.offsetZ;

        // Store geometric center for proper rotation pivot
        this.centerX = partData.centerX;
        this.centerY = partData.centerY;
        this.centerZ = partData.centerZ;

        liveTime = MobDismemberment.clientTicks;

        // Calculate spawn position based on part offset
        double posX = parent.getX() + partOffsetX;
        double posY = parent.getBoundingBox().minY + parent.getBbHeight() / 2.0 + partOffsetY;
        double posZ = parent.getZ() + partOffsetZ;

        setPos(posX, posY, posZ);
        setYRot(parent.yBodyRot);
        setXRot(parent.getXRot());
        yRotO = parent.getYRot();
        xRotO = parent.getXRot();

        // Initial velocity with some randomness
        double motionX = parent.getDeltaMovement().x + (random.nextDouble() - random.nextDouble()) * 0.25D;
        double motionY = parent.getDeltaMovement().y + random.nextDouble() * 0.2D;
        double motionZ = parent.getDeltaMovement().z + (random.nextDouble() - random.nextDouble()) * 0.25D;

        // Random spin
        float i = random.nextInt(45) + 5F + random.nextFloat();
        float j = random.nextInt(45) + 5F + random.nextFloat();
        if (random.nextInt(2) == 0) i *= -1;
        if (random.nextInt(2) == 0) j *= -1;
        pitchSpin = i * (float) (motionY + 0.3D);
        yawSpin = j * (float) (Math.sqrt(Math.abs(motionX * motionZ)) + 0.3D);

        // Handle explosion force
        if (explo != null) {
            double dist = Math.max(explo.distanceTo(parent) / 2D, 0.1D);
            dist = Math.pow(dist, 2);

            double mag;
            if (explo instanceof PrimedTnt || explo instanceof MinecartTNT) {
                mag = 1.0D * (4.0 / dist);
            } else if (explo instanceof Creeper creep) {
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
        // Store old position for interpolation
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        if (explosion) {
            motionX *= 1D / 0.92D;
            motionY *= 1D / 0.95D;
            motionZ *= 1D / 0.92D;
            explosion = false;
        }

        // Apply gravity
        motionY -= 0.08D;

        // Move the entity
        move(MoverType.SELF, new Vec3(motionX, motionY, motionZ));

        // Apply drag
        motionY *= 0.98D;
        motionX *= 0.91D;
        motionZ *= 0.91D;

        if (isInWater()) {
            motionY = 0.3D;
            pitchSpin = 0.0F;
            yawSpin = 0.0F;
        }

        if (onGround() || isInWater()) {
            setXRot(getXRot() + (-90F - (getXRot() % 360F)) / 2);
            motionY *= 0.8D;
            motionX *= 0.8D;
            motionZ *= 0.8D;
        } else {
            setXRot(getXRot() + pitchSpin);
            setYRot(getYRot() + yawSpin);
            pitchSpin *= 0.98F;
            yawSpin *= 0.98F;
        }

        // Check for player collision and push gib away from player
        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
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

        // Lifetime management
        if (onGround() || isInWater()) {
            groundTime++;
            if (groundTime > Config.GIB_GROUND_TIME.get() + 20) {
                discard();
            }
        } else if (groundTime > Config.GIB_GROUND_TIME.get()) {
            groundTime--;
        } else {
            groundTime = 0;
        }

        if (liveTime + Config.GIB_TIME.get() < MobDismemberment.clientTicks) {
            discard();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved();
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean saveAsPassenger(CompoundTag tag) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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
        return Mth.lerp(partialTicks, this.yRotO, this.getYRot());
    }

    public float getInterpolatedPitch(float partialTicks) {
        return Mth.lerp(partialTicks, this.xRotO, this.getXRot());
    }
}
