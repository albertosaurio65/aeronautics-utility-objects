package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.SwivelBearingPlateParentAccess;
import com.enxv.aerouniversaljoint.content.DampingStressBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(SwivelBearingPlateBlockEntity.class)
public abstract class SwivelBearingPlateBlockEntityMixin implements SwivelBearingPlateParentAccess {
    @Shadow
    private BlockPos parent;

    @Shadow
    private UUID parentSubLevelId;

    @Shadow
    private boolean assembling;

    @Shadow
    private void destroyBearing() {
    }

    @Redirect(
            method = "remove",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/blocks/swivel_bearing/link_block/SwivelBearingPlateBlockEntity;destroyBearing()V"))
    private void aeronautics$protectDampingBearingFromTransientPlateRemoval(SwivelBearingPlateBlockEntity instance) {
        Level level = instance.getLevel();
        if (level == null || level.isClientSide || this.assembling || this.parent == null) {
            this.destroyBearing();
            return;
        }

        BlockEntity parentBlockEntity = this.aeronautics$getParentBlockEntity(level);
        if (!(parentBlockEntity instanceof DampingStressBearingBlockEntity bearing)) {
            this.destroyBearing();
            return;
        }

        bearing.onLinkedPlateRemovedUnexpectedly();
    }

    @Inject(method = "propagateRotationTo", at = @At("HEAD"), cancellable = true)
    private void aeronautics$disconnectPlateFromCustomBearing(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                                              BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs,
                                                              CallbackInfoReturnable<Float> cir) {
        Level level = ((SwivelBearingPlateBlockEntity) (Object) this).getLevel();
        BlockEntity parentBlockEntity = level != null ? this.aeronautics$getParentBlockEntity(level) : null;
        if (parentBlockEntity instanceof DampingStressBearingBlockEntity && target == parentBlockEntity) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "isCustomConnection", at = @At("HEAD"), cancellable = true)
    private void aeronautics$disableCustomKineticConnection(KineticBlockEntity other, BlockState state, BlockState otherState,
                                                            CallbackInfoReturnable<Boolean> cir) {
        Level level = ((SwivelBearingPlateBlockEntity) (Object) this).getLevel();
        BlockEntity parentBlockEntity = level != null ? this.aeronautics$getParentBlockEntity(level) : null;
        if (parentBlockEntity instanceof DampingStressBearingBlockEntity && other == parentBlockEntity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addPropagationLocations", at = @At("RETURN"), cancellable = true)
    private void aeronautics$removeParentFromPropagation(IRotate block, BlockState state, List<BlockPos> neighbours,
                                                         CallbackInfoReturnable<List<BlockPos>> cir) {
        if (this.parent != null && this.aeronautics$isCustomBearingParent()) {
            List<BlockPos> propagated = cir.getReturnValue();
            try {
                propagated.remove(this.parent);
            } catch (UnsupportedOperationException exception) {
                List<BlockPos> copy = new ArrayList<>(propagated);
                copy.remove(this.parent);
                cir.setReturnValue(copy);
            }
        }
    }

    @Inject(method = "sable$physicsTick", at = @At("TAIL"))
    private void aeronautics$applyCustomBearingResistance(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep,
                                                          CallbackInfo ci) {
        Level level = ((SwivelBearingPlateBlockEntity) (Object) this).getLevel();
        BlockEntity parentBlockEntity = level != null ? this.aeronautics$getParentBlockEntity(level) : null;
        if (parentBlockEntity instanceof DampingStressBearingBlockEntity bearing) {
            bearing.aeronautics$runResistancePhysicsTick(subLevel, handle, timeStep);
        }
    }

    private boolean aeronautics$isCustomBearingParent() {
        SwivelBearingPlateBlockEntity plate = (SwivelBearingPlateBlockEntity) (Object) this;
        Level level = plate.getLevel();
        return level != null && this.parent != null && this.aeronautics$isDampingStressBearingParent(level);
    }

    @Override
    public BlockPos aeronautics$getParentPos() {
        return this.parent;
    }

    @Override
    public UUID aeronautics$getParentSubLevelId() {
        return this.parentSubLevelId;
    }
}
