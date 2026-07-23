package me.ichun.mods.mobdismemberment.common;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.particle.BloodParticleProvider;
import me.ichun.mods.mobdismemberment.client.render.RenderGib;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = MobDismemberment.MOD_ID, dist = Dist.CLIENT)
public class MobDismemberment {
    public static final String MOD_ID = "mobdismemberment";
    public static final String MOD_NAME = "Mob Dismemberment";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityGib>> GIB_ENTITY = ENTITY_TYPES.register("gib",
            () -> EntityType.Builder.<EntityGib>of(EntityGib::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "gib"))));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_PARTICLE = PARTICLE_TYPES.register("blood",
            () -> new SimpleParticleType(false));

    public static EventHandlerClient eventHandlerClient;
    public static int clientTicks = 0;

    public MobDismemberment(IEventBus modEventBus, ModContainer modContainer) {
        ENTITY_TYPES.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerEntityRenderers);
        modEventBus.addListener(this::registerParticleProviders);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        eventHandlerClient = new EventHandlerClient();
        NeoForge.EVENT_BUS.register(eventHandlerClient);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GIB_ENTITY.get(), RenderGib::new);
    }

    private void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BLOOD_PARTICLE.get(), BloodParticleProvider::new);
    }
}
