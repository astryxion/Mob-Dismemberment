package me.ichun.mods.mobdismemberment.client;

import me.ichun.mods.mobdismemberment.client.core.EventHandlerClient;
import me.ichun.mods.mobdismemberment.client.entity.EntityGib;
import me.ichun.mods.mobdismemberment.common.MobDismemberment;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MobDismembermentClient {
    public static EventHandlerClient eventHandlerClient;
    public static int clientTicks;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MobDismemberment.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityGib>> GIB_ENTITY = ENTITY_TYPES.register("gib",
            () -> EntityType.Builder.<EntityGib>of(EntityGib::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(ResourceLocation.fromNamespaceAndPath(MobDismemberment.MOD_ID, "gib").toString()));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
