package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SwivelBearingBlockEntity.SwivelBearingCogwheelBlockEntity.class)
public interface SwivelBearingCogwheelBlockEntityMixin extends DetachedKineticSafetyGuard {
}
