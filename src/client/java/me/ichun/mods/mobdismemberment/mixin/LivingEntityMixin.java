package me.ichun.mods.mobdismemberment.mixin;

import me.ichun.mods.mobdismemberment.client.MobDismembermentClient;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void mobdismemberment$onLivingDeath(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()
                && !(self instanceof Player)
                && !self.isBaby()
                && MobDismembermentClient.eventHandlerClient != null) {
            MobDismembermentClient.eventHandlerClient.onLivingDeath(self);
        }
    }
}
