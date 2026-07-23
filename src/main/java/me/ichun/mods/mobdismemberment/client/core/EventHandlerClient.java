package me.ichun.mods.mobdismemberment.client.core;

import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.helper.ModelHelper;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.TNTEntity;
import net.minecraft.entity.item.minecart.TNTMinecartEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;

public class EventHandlerClient {
    /** Mutant Beasts (1.16.5) — Chumbanotz. */
    private static final String MUTANT_BEASTS_ZOMBIE_CLASS = "chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity";
    /** Mutant Monsters (1.18+) — Fuzs; kept as a soft fallback. */
    private static final String MUTANT_MONSTERS_ZOMBIE_CLASS = "fuzs.mutantmonsters.common.world.entity.mutant.MutantZombie";
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
    private static java.lang.reflect.Method mutantZombieGetLives;
    private static java.lang.reflect.Field mutantZombieVanishTime;
    private static boolean mutantZombieReflectResolved;

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntityLiving();
        // ThatSoulyGuy port: dismember any LivingEntity except players and babies,
        // but only when ModelHelper can actually extract gib parts (otherwise leave normal death alone).
        if (entity.level instanceof ClientWorld) {
            queueDismember(entity);
        }
    }

    @SubscribeEvent
    public void onClientConnection(ClientPlayerNetworkEvent.LoggedInEvent event) {
        // Leave world, edit config, rejoin — pick up TOML changes without full quit.
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
            ClientWorld world = mc.level;

            MobDismemberment.clientTicks++;
            refreshBlacklistCache();

            if (!mc.isPaused()) {
                activeGibs.removeIf(ent -> ent.removed);
                skippedDismember.removeIf(ent -> ent.removed);

                for (Entity ent : world.entitiesForRendering()) {
                    if (ent instanceof CreeperEntity || ent instanceof TNTEntity
                            || ent.getType() == EntityType.TNT_MINECART || ent instanceof TNTMinecartEntity) {
                        if (!explosionSources.contains(ent)) {
                            explosionSources.add(ent);
                        }
                    }
                    if (ent instanceof LivingEntity && isMutantZombie((LivingEntity) ent)) {
                        trackMutantZombieResurrection((LivingEntity) ent);
                    }
                    if (ent instanceof LivingEntity
                            && !(ent instanceof PlayerEntity)
                            && !((LivingEntity) ent).isBaby()
                            && !ent.isAlive() && !dismemberTimeout.containsKey(ent)) {
                        queueDismember((LivingEntity) ent);
                    }
                }

                for (int i = explosionSources.size() - 1; i >= 0; i--) {
                    Entity ent = explosionSources.get(i);
                    if (ent.removed) {
                        if (ent instanceof CreeperEntity) {
                            CreeperEntity creeper = (CreeperEntity) ent;
                            float swellProgress = creeper.getSwelling(0);
                            if (swellProgress >= 0.95f) {
                                if (!exploTime.containsKey(ent)) {
                                    int time = MobDismemberment.clientTicks % 24000;
                                    if (time > 23959) {
                                        time -= 23999;
                                    }
                                    exploTime.put(ent, time);
                                }

                                queueDismember(creeper);
                            }
                        } else if (ent instanceof TNTEntity || ent.getType() == EntityType.TNT_MINECART
                                || ent instanceof TNTMinecartEntity) {
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
                        if (dismember((ClientWorld) e.getKey().level, e.getKey(), explo)) {
                            e.getKey().remove();
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
     * Queue a living entity for gibbing only when ModelHelper extraction can produce parts.
     * Entities without extractable ModelRenderers keep their normal death animation.
     * Blacklisted entity IDs from config are also skipped.
     */
    private void queueDismember(LivingEntity entity) {
        if (entity instanceof PlayerEntity || entity.isBaby() || dismemberTimeout.containsKey(entity)) {
            return;
        }
        if (isBlacklisted(entity)) {
            return;
        }
        if (skippedDismember.contains(entity)) {
            return;
        }
        if (shouldDeferMutantZombieResurrection(entity)) {
            return;
        }
        if (ModelHelper.extractModelParts(entity).isEmpty()) {
            skippedDismember.add(entity);
            return;
        }
        if (isMutantZombie(entity)) {
            if (dismember((ClientWorld) entity.level, entity, null)) {
                entity.remove();
                skippedDismember.add(entity);
                mutantZombieWasDead.remove(entity.getUUID());
                mutantZombieReviveCounts.remove(entity.getUUID());
            }
            return;
        }
        dismemberTimeout.put(entity, 2);
    }

    private void refreshBlacklistCache() {
        String raw = Config.MOB_BLACKLIST.get();
        if (!Objects.equals(raw, lastBlacklistRaw)) {
            lastBlacklistRaw = raw;
            skippedDismember.clear();
        }
    }

    private boolean isBlacklisted(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
        if (id == null) {
            return false;
        }
        String key = id.toString().toLowerCase(Locale.ROOT);
        for (String entry : Config.getMobBlacklist()) {
            if (entry.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMutantZombie(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
        if (id == null || !"mutant_zombie".equals(id.getPath())) {
            return false;
        }
        // 1.16.5: Mutant Beasts (mutantbeasts). Later ports: Mutant Monsters (mutantmonsters).
        String ns = id.getNamespace();
        return "mutantbeasts".equals(ns) || "mutantmonsters".equals(ns);
    }

    private void trackMutantZombieResurrection(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        boolean dead = !entity.isAlive();
        if (mutantZombieWasDead.contains(uuid) && !dead) {
            Integer prev = mutantZombieReviveCounts.get(uuid);
            mutantZombieReviveCounts.put(uuid, prev == null ? 1 : prev + 1);
            mutantZombieWasDead.remove(uuid);
        } else if (dead) {
            mutantZombieWasDead.add(uuid);
        } else {
            mutantZombieWasDead.remove(uuid);
        }
        if (entity.removed) {
            mutantZombieWasDead.remove(uuid);
            mutantZombieReviveCounts.remove(uuid);
        }
    }

    /**
     * Soft compat with Mutant Beasts / Mutant Monsters Mutant Zombie.
     * While it can still resurrect, gibbing removes the client entity and leaves an invisible actor after revive.
     */
    private boolean shouldDeferMutantZombieResurrection(LivingEntity entity) {
        if (!isMutantZombie(entity)) {
            return false;
        }
        resolveMutantZombieReflection();

        Integer lives = invokeLives(entity);
        if (lives != null) {
            return lives > 0;
        }

        // Mutant Beasts: burning corpse uses vanishTime toward final death (lives already 0).
        Integer vanish = invokeVanishTime(entity);
        if (vanish != null && vanish > 0) {
            return false;
        }

        Integer revives = mutantZombieReviveCounts.get(entity.getUUID());
        return (revives == null ? 0 : revives) < MUTANT_ZOMBIE_STARTING_LIVES;
    }

    private static void resolveMutantZombieReflection() {
        if (mutantZombieReflectResolved) {
            return;
        }
        mutantZombieReflectResolved = true;
        String[] classNames = {MUTANT_BEASTS_ZOMBIE_CLASS, MUTANT_MONSTERS_ZOMBIE_CLASS};
        for (int i = 0; i < classNames.length; i++) {
            try {
                Class<?> clazz = Class.forName(classNames[i]);
                java.lang.reflect.Method livesMethod = null;
                try {
                    livesMethod = clazz.getMethod("getLives"); // Mutant Beasts 1.16.5
                } catch (NoSuchMethodException ignored) {
                }
                if (livesMethod == null) {
                    try {
                        livesMethod = clazz.getMethod("getRemainingLives"); // Mutant Monsters
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (livesMethod == null) {
                    continue;
                }
                mutantZombieClass = clazz;
                mutantZombieGetLives = livesMethod;
                try {
                    mutantZombieVanishTime = clazz.getField("vanishTime");
                } catch (NoSuchFieldException ignored) {
                    mutantZombieVanishTime = null;
                }
                return;
            } catch (ClassNotFoundException ignored) {
            }
        }
        mutantZombieClass = null;
        mutantZombieGetLives = null;
        mutantZombieVanishTime = null;
    }

    private static Integer invokeLives(LivingEntity entity) {
        if (mutantZombieGetLives != null && mutantZombieClass != null && mutantZombieClass.isInstance(entity)) {
            try {
                Object lives = mutantZombieGetLives.invoke(entity);
                if (lives instanceof Number) {
                    return ((Number) lives).intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        // Direct probe for either API name on the runtime class.
        String[] methodNames = {"getLives", "getRemainingLives"};
        for (int i = 0; i < methodNames.length; i++) {
            try {
                java.lang.reflect.Method method = entity.getClass().getMethod(methodNames[i]);
                Object lives = method.invoke(entity);
                if (lives instanceof Number) {
                    return ((Number) lives).intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Integer invokeVanishTime(LivingEntity entity) {
        if (mutantZombieVanishTime != null && mutantZombieClass != null && mutantZombieClass.isInstance(entity)) {
            try {
                Object vanish = mutantZombieVanishTime.get(entity);
                if (vanish instanceof Number) {
                    return ((Number) vanish).intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            java.lang.reflect.Field field = entity.getClass().getField("vanishTime");
            Object vanish = field.get(entity);
            if (vanish instanceof Number) {
                return ((Number) vanish).intValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public boolean dismember(ClientWorld world, LivingEntity living, Entity explo) {
        if (living.isBaby() || isBlacklisted(living) || shouldDeferMutantZombieResurrection(living)) {
            return false;
        }

        List<ModelHelper.PartData> parts = ModelHelper.extractModelParts(living);

        if (parts.isEmpty()) {
            return false;
        }

        for (ModelHelper.PartData partData : parts) {
            addClientEntity(world, new EntityGib(world, living, partData, explo));
        }

        if (Config.BLOOD.get()) {
            int bloodIterations = Config.BLOOD_COUNT.get();
            double explosionSprayMul = 0D;
            if (explo != null) {
                if (explo instanceof CreeperEntity) {
                    bloodIterations = Math.min(Config.BLOOD_COUNT.get() * 2, 96);
                    explosionSprayMul = 6D;
                } else {
                    bloodIterations = Math.min(Config.BLOOD_COUNT.get() * 10, 450);
                    explosionSprayMul = 100D;
                }
            }
            for (int k = 0; k < bloodIterations; k++) {
                float var4 = 0.3F;
                double mX = (double) (-MathHelper.sin(living.yRot / 180.0F * (float) Math.PI) * MathHelper.cos(living.xRot / 180.0F * (float) Math.PI) * var4);
                double mZ = (double) (MathHelper.cos(living.yRot / 180.0F * (float) Math.PI) * MathHelper.cos(living.xRot / 180.0F * (float) Math.PI) * var4);
                double mY = (double) (-MathHelper.sin(living.xRot / 180.0F * (float) Math.PI) * var4 + 0.1F);
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

    private void addClientEntity(ClientWorld world, EntityGib entity) {
        world.putNonPlayerEntity(entity.getId(), entity);
        activeGibs.add(entity);
    }
}
