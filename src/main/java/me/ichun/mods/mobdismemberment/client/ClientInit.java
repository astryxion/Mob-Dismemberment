package me.ichun.mods.mobdismemberment.client;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.particle.BloodParticleProvider;
import me.ichun.mods.mobdismemberment.client.render.RenderGib;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only bootstrap invoked from {@link MobDismemberment} behind an {@code FMLEnvironment.dist}
 * check so dedicated servers never resolve these class references.
 */
public final class ClientInit {
    private ClientInit() {
    }

    public static void init(IEventBus modEventBus) {
        MobDismembermentClient.register(modEventBus);
        modEventBus.addListener(ClientInit::clientSetup);
        modEventBus.addListener(ClientInit::registerEntityRenderers);
        modEventBus.addListener(ClientInit::registerParticleProviders);
        modEventBus.addListener(ClientInit::onConfigLoading);
        modEventBus.addListener(ClientInit::onConfigReloading);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        MobDismembermentClient.eventHandlerClient = new EventHandlerClient();
        MinecraftForge.EVENT_BUS.register(MobDismembermentClient.eventHandlerClient);
    }

    private static void onConfigLoading(final ModConfigEvent.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.CLIENT && event.getConfig().getSpec() == Config.SPEC) {
            if (MobDismembermentClient.eventHandlerClient != null) {
                MobDismembermentClient.eventHandlerClient.onConfigReloaded();
            }
        }
    }

    private static void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.CLIENT && event.getConfig().getSpec() == Config.SPEC) {
            if (MobDismembermentClient.eventHandlerClient != null) {
                MobDismembermentClient.eventHandlerClient.onConfigReloaded();
            }
        }
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MobDismembermentClient.GIB_ENTITY.get(), RenderGib::new);
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MobDismemberment.BLOOD_PARTICLE.get(), BloodParticleProvider::new);
    }
}
