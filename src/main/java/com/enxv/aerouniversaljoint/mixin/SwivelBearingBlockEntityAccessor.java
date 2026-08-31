package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.enxv.aerouniversaljoint.access.SwivelBearingConstraintHandleAccess;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SwivelBearingBlockEntity.class)
public abstract class SwivelBearingBlockEntityAccessor implements SwivelBearingConstraintHandleAccess, DetachedKineticSafetyGuard {
    @Accessor("handle")
    @Nullable
    public abstract RotaryConstraintHandle aeronautics$getConstraintHandleInternal();

    @Override
    @Nullable
    public PhysicsConstraintHandle aeronautics$getConstraintHandle() {
        return this.aeronautics$getConstraintHandleInternal();
    }
}
