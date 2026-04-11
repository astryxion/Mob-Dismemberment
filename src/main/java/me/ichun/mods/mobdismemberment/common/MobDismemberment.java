package me.ichun.mods.mobdismemberment.common;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.client.particle.BloodParticleProvider;
import me.ichun.mods.mobdismemberment.client.render.RenderGib;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MobDismemberment.MOD_ID)
public class MobDismemberment {
    public static final String MOD_ID = "mobdismemberment";
    public static final String MOD_NAME = "Mob Dismemberment";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<EntityGib>> GIB_ENTITY = ENTITY_TYPES.register("gib",
            () -> EntityType.Builder.<EntityGib>of(EntityGib::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(new ResourceLocation(MOD_ID, "gib").toString()));

    public static final RegistryObject<SimpleParticleType> BLOOD_PARTICLE = PARTICLE_TYPES.register("blood",
            () -> new SimpleParticleType(false));

    public static EventHandlerClient eventHandlerClient;
    public static int clientTicks = 0;

    public MobDismemberment() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ENTITY_TYPES.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::registerEntityRenderers);
            modEventBus.addListener(this::registerParticleProviders);
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        eventHandlerClient = new EventHandlerClient();
        MinecraftForge.EVENT_BUS.register(eventHandlerClient);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GIB_ENTITY.get(), RenderGib::new);
    }

    private void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BLOOD_PARTICLE.get(), BloodParticleProvider::new);
    }
}
