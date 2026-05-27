package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.enxv.aerouniversaljoint.access.SwivelBearingConstraintHandleAccess;
import dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintHandle;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SwivelBearingBlockEntity.class)
public interface SwivelBearingBlockEntityAccessor extends SwivelBearingConstraintHandleAccess, DetachedKineticSafetyGuard {
    @Override
    @Accessor("handle")
    @Nullable
    RotaryConstraintHandle aeronautics$getConstraintHandle();
}
