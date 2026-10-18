package me.ichun.mods.mobdismemberment.client;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Client-only registrations. Kept out of {@link MobDismemberment} so dedicated servers
 * do not load {@link EntityGib} / renderer classes (Forge 1.20.1 has no {@code @Mod(dist=)}).
 * <p>
 * Gibs are not entered in {@code ForgeRegistries.ENTITY_TYPES}. That registry is synced from
 * the dedicated server, which does not have this client-only type, so a {@code RegistryObject}
 * for {@code mobdismemberment:gib} is cleared on join and {@code get()} crashes when a mob dies.
 * <p>
 * {@code EntityType.Builder.build} still creates an intrusive holder. Forge refuses to finish
 * client loading while that holder has no key, and the error names {@code minecraft:pig} because
 * the entity registry's default entry is the pig. {@link #createClientGibType()} binds the key
 * without {@code Registry.register}.
 */
public class MobDismembermentClient {
    public static EventHandlerClient eventHandlerClient;

    /**
     * Particle instance captured while the client registry still contains {@code mobdismemberment:blood}.
     * After joining a server that does not have this mod, the registry object is gone, but this instance
     * is the one the particle provider was bound to.
     */
    public static SimpleParticleType bloodParticle;

    public static final EntityType<EntityGib> GIB_ENTITY = createClientGibType();

    private static EntityType<EntityGib> createClientGibType() {
        ResourceLocation id = new ResourceLocation(MobDismemberment.MOD_ID, "gib");
        EntityType<EntityGib> type = EntityType.Builder.<EntityGib>of(EntityGib::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(64)
                .updateInterval(1)
                .noSave()
                .noSummon()
                .build(id.toString());
        type.builtInRegistryHolder().bindKey(ResourceKey.create(Registries.ENTITY_TYPE, id));
        return type;
    }
}
