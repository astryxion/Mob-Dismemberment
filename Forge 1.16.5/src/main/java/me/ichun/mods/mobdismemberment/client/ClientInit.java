package me.ichun.mods.mobdismemberment.client;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.particle.BloodParticleProvider;
import me.ichun.mods.mobdismemberment.client.render.RenderGib;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only bootstrap invoked from {@link MobDismemberment} behind a Dist check
 * so dedicated servers never resolve these class references.
 */
public final class ClientInit {
    private ClientInit() {
    }

    public static void init(IEventBus modEventBus) {
        MobDismembermentClient.register(modEventBus);
        modEventBus.addListener(ClientInit::clientSetup);
        modEventBus.addListener(ClientInit::registerParticleProviders);
        modEventBus.addListener(ClientInit::onConfigLoading);
        modEventBus.addListener(ClientInit::onConfigReloading);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        MobDismembermentClient.eventHandlerClient = new EventHandlerClient();
        MinecraftForge.EVENT_BUS.register(MobDismembermentClient.eventHandlerClient);

        RenderingRegistry.registerEntityRenderingHandler(MobDismembermentClient.GIB_ENTITY.get(), RenderGib::new);
    }

    private static void onConfigLoading(final ModConfig.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.CLIENT && event.getConfig().getSpec() == Config.SPEC) {
            if (MobDismembermentClient.eventHandlerClient != null) {
                MobDismembermentClient.eventHandlerClient.onConfigReloaded();
            }
        }
    }

    private static void onConfigReloading(final ModConfig.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.CLIENT && event.getConfig().getSpec() == Config.SPEC) {
            if (MobDismembermentClient.eventHandlerClient != null) {
                MobDismembermentClient.eventHandlerClient.onConfigReloaded();
            }
        }
    }

    private static void registerParticleProviders(ParticleFactoryRegisterEvent event) {
        Minecraft.getInstance().particleEngine.register(MobDismemberment.BLOOD_PARTICLE.get(), BloodParticleProvider::new);
    }
}
