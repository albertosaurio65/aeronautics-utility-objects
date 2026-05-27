package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.enxv.aerouniversaljoint.access.KineticEffectHandlerAccess;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KineticEffectHandler.class)
public class KineticEffectHandlerMixin implements KineticEffectHandlerAccess {
    @Shadow
    KineticBlockEntity kte;

    @Shadow
    float overStressedEffect;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void aeronautics$skipWhenDetached(CallbackInfo ci) {
        if (this.kte instanceof DetachedKineticSafetyGuard && this.kte.getLevel() == null) {
            ci.cancel();
        }
    }

    @Override
    public float aeronautics$getOverStressedEffect() {
        return this.overStressedEffect;
    }
}
