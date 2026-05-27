package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void aeronautics$skipDetachedAtHead(CallbackInfo ci) {
        KineticBlockEntity kinetic = (KineticBlockEntity) (Object) this;
        if (kinetic instanceof DetachedKineticSafetyGuard && kinetic.getLevel() == null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;level:Lnet/minecraft/world/level/Level;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 1),
            cancellable = true)
    private void aeronautics$skipDetachedBeforeServerBranch(CallbackInfo ci) {
        KineticBlockEntity kinetic = (KineticBlockEntity) (Object) this;
        if (kinetic instanceof DetachedKineticSafetyGuard && kinetic.getLevel() == null) {
            ci.cancel();
        }
    }
}
