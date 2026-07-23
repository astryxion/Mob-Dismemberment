package me.ichun.mods.mobdismemberment.client.core;

import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.helper.ModelHelper;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.Map.Entry;

public class EventHandlerClient {
    private static final String MUTANT_ZOMBIE_CLASS = "fuzs.mutantmonsters.common.world.entity.mutant.MutantZombie";
    private static final int MUTANT_ZOMBIE_STARTING_LIVES = 3;

    public HashMap<LivingEntity, Integer> dismemberTimeout = new HashMap<>();
    public HashMap<Entity, Integer> exploTime = new HashMap<>();
    public ArrayList<Entity> explosionSources = new ArrayList<>();
    public ArrayList<EntityGib> activeGibs = new ArrayList<>();
    public HashSet<LivingEntity> skippedDismember = new HashSet<>();
    /** Mutant Zombie UUIDs currently observed as dead (health &lt;= 0). */
    private final HashSet<UUID> mutantZombieWasDead = new HashSet<>();
    /** How many times each Mutant Zombie has been seen resurrecting (dead → alive). */
    private final HashMap<UUID, Integer> mutantZombieReviveCounts = new HashMap<>();
    /** Last seen raw blacklist string; used to drop skip-cache when config changes in-session. */
    private String lastBlacklistRaw;
    private static Class<?> mutantZombieClass;
    private static java.lang.reflect.Method mutantZombieGetRemainingLives;
    private static java.lang.reflect.Method mutantZombieGetVanishingProgress;
    private static boolean mutantZombieReflectResolved;

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        // ThatSoulyGuy 1.20.1 port: dismember any LivingEntity except players and babies,
        // but only when ModelHelper can actually extract gib parts (otherwise leave normal death alone).
        if (entity.getLevel() instanceof ClientLevel) {
            queueDismember(entity);
        }
    }

    @SubscribeEvent
    public void onClientConnection(ClientPlayerNetworkEvent.LoggedInEvent event) {
        // Match NeoForge UX: leave world, edit config, rejoin — pick up TOML changes without full quit.
        Config.reloadClientConfigFromDisk();

        exploTime.clear();
        dismemberTimeout.clear();
        explosionSources.clear();
        activeGibs.clear();
        skippedDismember.clear();
        mutantZombieWasDead.clear();
        mutantZombieReviveCounts.clear();
        lastBlacklistRaw = null;
    }

    /** Call when the client config reloads so blacklist edits take effect immediately. */
    public void onConfigReloaded() {
        skippedDismember.clear();
        lastBlacklistRaw = null;
    }

    @SubscribeEvent
    public void worldTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel world = mc.level;

            MobDismemberment.clientTicks++;
            refreshBlacklistCache();

            if (!mc.isPaused()) {
                // Clean up removed gibs from our tracking list
                // Note: We don't manually tick - putNonPlayerEntity adds them to the world's tick list
                activeGibs.removeIf(Entity::isRemoved);
                skippedDismember.removeIf(Entity::isRemoved);

                for (Entity ent : world.entitiesForRendering()) {
                    if (ent instanceof Creeper || ent instanceof PrimedTnt || ent.getType() == EntityType.TNT_MINECART || ent instanceof MinecartTNT) {
                        if (!explosionSources.contains(ent)) {
                            explosionSources.add(ent);
                        }
                    }
                    if (ent instanceof LivingEntity living && isMutantZombie(living)) {
                        trackMutantZombieResurrection(living);
                    }
                    // Detect death for any LivingEntity except players
                    if (ent instanceof LivingEntity living &&
                        !(ent instanceof Player) &&
                        !living.isBaby() &&
                        !ent.isAlive() && !dismemberTimeout.containsKey(ent)) {
                        queueDismember(living);
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

                                queueDismember(creeper);
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
                        if (dismember((ClientLevel) e.getKey().getLevel(), e.getKey(), explo)) {
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

    /**
     * Queue a living entity for gibbing only when ThatSoulyGuy-style ModelHelper extraction
     * can produce parts. Entities without extractable ModelParts keep their normal death animation.
     * Blacklisted entity IDs from config are also skipped.
     */
    private void queueDismember(LivingEntity entity) {
        if (entity instanceof Player || entity.isBaby() || dismemberTimeout.containsKey(entity)) {
            return;
        }
        // Always read live config — do not cache blacklist hits in skippedDismember,
        // otherwise removing an ID mid-session (or after a reload) keeps blocking that corpse/path.
        if (isBlacklisted(entity)) {
            return;
        }
        if (skippedDismember.contains(entity)) {
            return;
        }
        // Mutant Monsters: leave the corpse alone while it can still resurrect.
        // Do not mark skipped — final death must still be allowed to gib later.
        if (shouldDeferMutantZombieResurrection(entity)) {
            return;
        }
        if (ModelHelper.extractModelParts(entity).isEmpty()) {
            skippedDismember.add(entity);
            return;
        }
        // Final Mutant Zombie death: gib immediately so we don't zero deathTime / fight their vanish fade.
        if (isMutantZombie(entity)) {
            if (dismember((ClientLevel) entity.getLevel(), entity, null)) {
                entity.discard();
                skippedDismember.add(entity);
                mutantZombieWasDead.remove(entity.getUUID());
                mutantZombieReviveCounts.remove(entity.getUUID());
            }
            // If gibbing failed this tick, do not permanently skip — retry while the corpse remains.
            return;
        }
        dismemberTimeout.put(entity, 2);
    }

    private void refreshBlacklistCache() {
        String raw = Config.MOB_BLACKLIST.get();
        if (!java.util.Objects.equals(raw, lastBlacklistRaw)) {
            lastBlacklistRaw = raw;
            // Blacklist membership is no longer stored in skippedDismember, but clear it anyway
            // so a prior "no parts" skip cannot stick across a config tweak / reload.
            skippedDismember.clear();
        }
    }

    private boolean isBlacklisted(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
        if (id == null) {
            return false;
        }
        String key = id.toString().toLowerCase(java.util.Locale.ROOT);
        for (String entry : Config.getMobBlacklist()) {
            if (entry.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMutantZombie(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
        return id != null && "mutantmonsters".equals(id.getNamespace()) && "mutant_zombie".equals(id.getPath());
    }

    private void trackMutantZombieResurrection(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        boolean dead = !entity.isAlive();
        if (mutantZombieWasDead.contains(uuid) && !dead) {
            mutantZombieReviveCounts.merge(uuid, 1, Integer::sum);
            mutantZombieWasDead.remove(uuid);
        } else if (dead) {
            mutantZombieWasDead.add(uuid);
        } else {
            mutantZombieWasDead.remove(uuid);
        }
        if (entity.isRemoved()) {
            mutantZombieWasDead.remove(uuid);
            mutantZombieReviveCounts.remove(uuid);
        }
    }

    /**
     * Soft compat with Mutant Monsters' Mutant Zombie.
     * While it can still resurrect, gibbing discards the client entity and leaves an invisible actor after revive.
     * Only allow gibbing on the true final death (remaining lives &lt;= 0 / vanishing / enough observed revives).
     */
    private boolean shouldDeferMutantZombieResurrection(LivingEntity entity) {
        if (!isMutantZombie(entity)) {
            return false;
        }
        resolveMutantZombieReflection();

        Integer lives = invokeRemainingLives(entity);
        if (lives != null) {
            return lives > 0;
        }

        // Final-death vanish fade is unique to lives &lt;= 0 — allow gibbing.
        Float vanish = invokeVanishingProgress(entity);
        if (vanish != null && vanish > 0.0F) {
            return false;
        }

        // Fallback when reflection is unavailable: Mutant Zombie starts with 3 lives and decrements on each revive.
        int revives = mutantZombieReviveCounts.getOrDefault(entity.getUUID(), 0);
        return revives < MUTANT_ZOMBIE_STARTING_LIVES;
    }

    private static void resolveMutantZombieReflection() {
        if (mutantZombieReflectResolved) {
            return;
        }
        mutantZombieReflectResolved = true;
        try {
            mutantZombieClass = Class.forName(MUTANT_ZOMBIE_CLASS);
            mutantZombieGetRemainingLives = mutantZombieClass.getMethod("getRemainingLives");
            mutantZombieGetVanishingProgress = mutantZombieClass.getMethod("getVanishingProgress", float.class);
        } catch (ReflectiveOperationException ignored) {
            mutantZombieClass = null;
            mutantZombieGetRemainingLives = null;
            mutantZombieGetVanishingProgress = null;
        }
    }

    private static Integer invokeRemainingLives(LivingEntity entity) {
        if (mutantZombieGetRemainingLives == null || mutantZombieClass == null || !mutantZombieClass.isInstance(entity)) {
            try {
                java.lang.reflect.Method method = entity.getClass().getMethod("getRemainingLives");
                Object lives = method.invoke(entity);
                if (lives instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return null;
        }
        try {
            Object lives = mutantZombieGetRemainingLives.invoke(entity);
            if (lives instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static Float invokeVanishingProgress(LivingEntity entity) {
        if (mutantZombieGetVanishingProgress == null || mutantZombieClass == null || !mutantZombieClass.isInstance(entity)) {
            try {
                java.lang.reflect.Method method = entity.getClass().getMethod("getVanishingProgress", float.class);
                Object vanish = method.invoke(entity, 0.0F);
                if (vanish instanceof Number number) {
                    return number.floatValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return null;
        }
        try {
            Object vanish = mutantZombieGetVanishingProgress.invoke(entity, 0.0F);
            if (vanish instanceof Number number) {
                return number.floatValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public boolean dismember(ClientLevel world, LivingEntity living, Entity explo) {
        if (living.isBaby() || isBlacklisted(living) || shouldDeferMutantZombieResurrection(living)) {
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

        // Spawn blood particles (creeper blasts use a lighter path than TNT-style explosions)
        if (Config.BLOOD.get()) {
            int bloodIterations = Config.BLOOD_COUNT.get();
            double explosionSprayMul = 0D;
            if (explo != null) {
                if (explo instanceof Creeper) {
                    bloodIterations = Math.min(Config.BLOOD_COUNT.get() * 2, 96);
                    explosionSprayMul = 6D;
                } else {
                    bloodIterations = Math.min(Config.BLOOD_COUNT.get() * 10, 450);
                    explosionSprayMul = 100D;
                }
            }
            for (int k = 0; k < bloodIterations; k++) {
                float var4 = 0.3F;
                double mX = (double) (-Mth.sin(living.getYRot() / 180.0F * (float) Math.PI) * Mth.cos(living.getXRot() / 180.0F * (float) Math.PI) * var4);
                double mZ = (double) (Mth.cos(living.getYRot() / 180.0F * (float) Math.PI) * Mth.cos(living.getXRot() / 180.0F * (float) Math.PI) * var4);
                double mY = (double) (-Mth.sin(living.getXRot() / 180.0F * (float) Math.PI) * var4 + 0.1F);
                var4 = 0.02F;
                float var5 = living.getRandom().nextFloat() * (float) Math.PI * 2.0F;
                var4 *= living.getRandom().nextFloat();

                if (explosionSprayMul > 0D) {
                    var4 *= (float) explosionSprayMul;
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
     * Uses putNonPlayerEntity which is the method used for entities received from the server.
     * Also adds to our activeGibs list for manual ticking.
     */
    private void addClientEntity(ClientLevel world, EntityGib entity) {
        world.putNonPlayerEntity(entity.getId(), entity);
        activeGibs.add(entity);
    }
}
