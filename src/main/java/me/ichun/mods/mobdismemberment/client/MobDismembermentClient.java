package me.ichun.mods.mobdismemberment.client;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client-only registrations. Kept out of {@link MobDismemberment} so dedicated servers
 * do not load {@link EntityGib} / renderer classes.
 */
public class MobDismembermentClient {
    public static EventHandlerClient eventHandlerClient;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, MobDismemberment.MOD_ID);

    public static final RegistryObject<EntityType<EntityGib>> GIB_ENTITY = ENTITY_TYPES.register("gib",
            () -> EntityType.Builder.<EntityGib>of(EntityGib::new, EntityClassification.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(new ResourceLocation(MobDismemberment.MOD_ID, "gib").toString()));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
