package me.ichun.mods.mobdismemberment.common;

import me.ichun.mods.mobdismemberment.client.ClientInit;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MobDismemberment.MOD_ID)
public class MobDismemberment {
    public static final String MOD_ID = "mobdismemberment";
    public static final String MOD_NAME = "Mob Dismemberment";

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    public static final RegistryObject<SimpleParticleType> BLOOD_PARTICLE = PARTICLE_TYPES.register("blood",
            () -> new SimpleParticleType(false));

    public static int clientTicks = 0;

    public MobDismemberment() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        PARTICLE_TYPES.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // Forge 1.20.1 has no @Mod(dist=...); gate client bootstrap so dedicated servers do not load client classes.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init(modEventBus);
        }
    }
}
