package me.ichun.mods.mobdismemberment.client.core;

import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.helper.ModelHelper;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.*;
import java.util.Map.Entry;

public class EventHandlerClient {
    public HashMap<LivingEntity, Integer> dismemberTimeout = new HashMap<>();
    public HashMap<Entity, Integer> exploTime = new HashMap<>();
    public ArrayList<Entity> explosionSources = new ArrayList<>();
    public ArrayList<EntityGib> activeGibs = new ArrayList<>();

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // Dismember any LivingEntity except players and babies
        if (entity.level() instanceof ClientLevel &&
            !(entity instanceof Player) &&
            !entity.isBaby()) {
            dismemberTimeout.put(entity, 2);
        }
    }

    @SubscribeEvent
    public void onClientConnection(ClientPlayerNetworkEvent.LoggingIn event) {
        exploTime.clear();
        dismemberTimeout.clear();
        explosionSources.clear();
        activeGibs.clear();
    }

    @SubscribeEvent
    public void worldTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level != null) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel world = mc.level;

            MobDismemberment.clientTicks++;

            if (!mc.isPaused()) {
                // Clean up removed gibs from our tracking list
                // Note: We don't manually tick - ClientLevel.addEntity adds them to the world's tick list
                activeGibs.removeIf(Entity::isRemoved);

                for (Entity ent : world.entitiesForRendering()) {
                    if (ent instanceof Creeper || ent instanceof PrimedTnt || ent.getType() == EntityType.TNT_MINECART || ent instanceof MinecartTNT) {
                        if (!explosionSources.contains(ent)) {
                            explosionSources.add(ent);
                        }
                    }
                    // Detect death for any LivingEntity except players
                    if (ent instanceof LivingEntity living &&
                        !(ent instanceof Player) &&
                        !living.isBaby() &&
                        !ent.isAlive() && !dismemberTimeout.containsKey(ent)) {
                        dismemberTimeout.put(living, 2);
                    }
                }

                for (int i = explosionSources.size() - 1; i >= 0; i--) {
                    Entity ent = explosionSources.get(i);
                    if (ent.isRemoved()) {
                        if (ent instanceof Creeper creeper) {
                            // Use getSwelling to check if creeper is about to explode
                            // getSwelling returns a value from 0 to 1 representing the explosion progress
                            float swellProgress = creeper.getSwelling(0);
                            if (swellProgress >= 0.95f) { // About to explode
                                if (!exploTime.containsKey(ent)) {
                                    int time = MobDismemberment.clientTicks % 24000;
                                    if (time > 23959) {
                                        time -= 23999;
                                    }
                                    exploTime.put(ent, time);
                                }

                                dismemberTimeout.put(creeper, 2);
                            }
                        } else if (ent instanceof PrimedTnt || ent.getType() == EntityType.TNT_MINECART || ent instanceof MinecartTNT) {
                            if (!exploTime.containsKey(ent)) {
                                int time = MobDismemberment.clientTicks % 24000;
                                if (time > 23959) {
                                    time -= 23999;
                                }
                                exploTime.put(ent, time);
                            }
                        }

                        explosionSources.remove(i);
                    }
                }

                Iterator<Entry<LivingEntity, Integer>> ite = dismemberTimeout.entrySet().iterator();
                if (ite.hasNext()) {
                    Entry<LivingEntity, Integer> e = ite.next();

                    e.setValue(e.getValue() - 1);

                    e.getKey().hurtTime = 0;
                    e.getKey().deathTime = 0;

                    Entity explo = null;
                    double dist = 1000D;
                    for (Entry<Entity, Integer> e1 : exploTime.entrySet()) {
                        double mobDist = e1.getKey().distanceTo(e.getKey());
                        if (mobDist < 10D && mobDist < dist) {
                            dist = mobDist;
                            explo = e1.getKey();
                            e.setValue(0);
                        }
                    }

                    if (e.getValue() <= 0) {
                        if (dismember((ClientLevel) e.getKey().level(), e.getKey(), explo)) {
                            e.getKey().discard();
                        }
                        ite.remove();
                    }
                }

                Iterator<Entry<Entity, Integer>> ite1 = exploTime.entrySet().iterator();
                int worldTime = MobDismemberment.clientTicks % 24000;
                while (ite1.hasNext()) {
                    Entry<Entity, Integer> e = ite1.next();
                    if (e.getValue() + 40 < worldTime) {
                        ite1.remove();
                    }
                }
            }
        }
    }

    public boolean dismember(ClientLevel world, LivingEntity living, Entity explo) {
        if (living.isBaby()) {
            return false;
        }

        // Extract all model parts from the entity
        List<ModelHelper.PartData> parts = ModelHelper.extractModelParts(living);

        if (parts.isEmpty()) {
            return false;
        }

        // Create a gib for each model part
        for (ModelHelper.PartData partData : parts) {
            addClientEntity(world, new EntityGib(world, living, partData, explo));
        }

        // Spawn blood particles (creeper blasts use a lighter path than TNT-scale explosions)
        if (Config.BLOOD.get()) {
            int bloodCount = Config.BLOOD_COUNT.get();
            int bloodIterations;
            double sprayMult;
            if (explo == null) {
                bloodIterations = bloodCount;
                sprayMult = 1.0D;
            } else if (explo instanceof Creeper) {
                bloodIterations = Math.min(bloodCount * 2, 96);
                sprayMult = 6.0D;
            } else {
                bloodIterations = Math.min(bloodCount * 10, 450);
                sprayMult = 100.0D;
            }

            for (int k = 0; k < bloodIterations; k++) {
                float var4 = 0.3F;
                double mX = (double) (-Mth.sin(living.getYRot() / 180.0F * (float) Math.PI) * Mth.cos(living.getXRot() / 180.0F * (float) Math.PI) * var4);
                double mZ = (double) (Mth.cos(living.getYRot() / 180.0F * (float) Math.PI) * Mth.cos(living.getXRot() / 180.0F * (float) Math.PI) * var4);
                double mY = (double) (-Mth.sin(living.getXRot() / 180.0F * (float) Math.PI) * var4 + 0.1F);
                var4 = 0.02F;
                float var5 = living.getRandom().nextFloat() * (float) Math.PI * 2.0F;
                var4 *= living.getRandom().nextFloat();

                if (explo != null) {
                    var4 *= (float) sprayMult;
                }

                mX += Math.cos((double) var5) * (double) var4;
                mY += (double) ((living.getRandom().nextFloat() - living.getRandom().nextFloat()) * 0.1F);
                mZ += Math.sin((double) var5) * (double) var4;

                world.addParticle(
                        MobDismemberment.BLOOD_PARTICLE.get(),
                        living.getX(),
                        living.getY() + 0.5D + (living.getRandom().nextDouble() * 0.7D),
                        living.getZ(),
                        living.getDeltaMovement().x + mX,
                        living.getDeltaMovement().y + mY,
                        living.getDeltaMovement().z + mZ
                );
            }
        }
        return true;
    }

    /**
     * Add a client-side only entity to the world.
     * Uses ClientLevel.addEntity (replaces putNonPlayerEntity removed after 1.20.x).
     * Also adds to our activeGibs list for manual ticking.
     */
    private void addClientEntity(ClientLevel world, EntityGib entity) {
        world.addEntity(entity);
        activeGibs.add(entity);
    }
}
