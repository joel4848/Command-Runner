package com.joel4848.commandrunner.mixin;

import com.joel4848.commandrunner.schedule.ScheduleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Damage received checker for scheduling
@Mixin(ClientPlayerEntity.class)
public class DamageMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // Only fire if there is actually positive damage incoming
        if (amount > 0) {
            ScheduleManager.onDamageTaken();
        }
    }
}
