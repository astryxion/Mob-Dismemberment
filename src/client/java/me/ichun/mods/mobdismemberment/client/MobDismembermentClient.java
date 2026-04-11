package me.ichun.mods.mobdismemberment.client;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.particle.BloodParticleProvider;
import me.ichun.mods.mobdismemberment.client.render.RenderGib;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class MobDismembermentClient implements ClientModInitializer {
    public static EntityType<EntityGib> GIB_ENTITY;
    public static SimpleParticleType BLOOD_PARTICLE;

    public static EventHandlerClient eventHandlerClient;
    public static int clientTicks = 0;

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save());

        Identifier gibId = Identifier.fromNamespaceAndPath(MobDismemberment.MOD_ID, "gib");
        ResourceKey<EntityType<?>> gibKey = ResourceKey.create(Registries.ENTITY_TYPE, gibId);
        GIB_ENTITY = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                gibId,
                FabricEntityTypeBuilder.<EntityGib>create(MobCategory.MISC, EntityGib::new)
                        .dimensions(EntityDimensions.scalable(0.5F, 0.5F))
                        .trackRangeChunks(4)
                        .trackedUpdateRate(1)
                        .disableSaving()
                        .disableSummon()
                        .build(gibKey)
        );

        Identifier bloodId = Identifier.fromNamespaceAndPath(MobDismemberment.MOD_ID, "blood");
        BLOOD_PARTICLE = Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                bloodId,
                FabricParticleTypes.simple()
        );

        EntityRendererRegistry.register(GIB_ENTITY, RenderGib::new);
        ParticleFactoryRegistry.getInstance().register(BLOOD_PARTICLE, BloodParticleProvider::new);

        eventHandlerClient = new EventHandlerClient();
        eventHandlerClient.register();
    }
}
