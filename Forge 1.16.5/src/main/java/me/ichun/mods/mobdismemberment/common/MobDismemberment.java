package me.ichun.mods.mobdismemberment.common;

import me.ichun.mods.mobdismemberment.client.ClientInit;
import me.ichun.mods.mobdismemberment.common.core.Config;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(MobDismemberment.MOD_ID)
public class MobDismemberment {
    public static final String MOD_ID = "mobdismemberment";
    public static final String MOD_NAME = "Mob Dismemberment";

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    public static final RegistryObject<BasicParticleType> BLOOD_PARTICLE = PARTICLE_TYPES.register("blood",
            () -> new BasicParticleType(false));

    public static int clientTicks = 0;

    public MobDismemberment() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        PARTICLE_TYPES.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // Client-only bootstrap so dedicated servers do not load client classes.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientInit.init(modEventBus));
    }
}
