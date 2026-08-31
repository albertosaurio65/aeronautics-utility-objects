package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModBlocks;
import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.enxv.aerouniversaljoint.access.SwivelBearingConstraintHandleAccess;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class DampingStressBearingBlockEntity extends SwivelBearingBlockEntity implements MenuProvider, DetachedKineticSafetyGuard {
    private static final String TAG_RESISTANCE = "ResistanceValue";
    private static final String LEGACY_TAG_DAMPING = "DampingValue";
    private static final String TAG_STRESS_OUTPUT_SUPPRESSED = "StressOutputSuppressed";
    private static final String TAG_STRESS_OUTPUT_SUPPRESSED_BY = "StressOutputSuppressedBy";
    private static final BehaviourType<ScrollValueBehaviour> RESISTANCE_BEHAVIOUR =
            new BehaviourType<>("aeronautics_utility_objects:damping_resistance");

    private static final double FREE_SPIN_BASE_DAMPING = 1.0E-3D;
    private static final double MIN_RELATIVE_ANGULAR_SPEED = 1.0E-4D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final float SPEED_STEP_RPM = 0.25F;
    private static final float SPEED_EPSILON = 1.0E-3F;
    private static final long OUTPUT_PUBLISH_INTERVAL_TICKS = 20L;
    private static final float OUTPUT_ZERO_DEADBAND_RPM = 1.0F;
    private static final float OUTPUT_PUBLISHED_STEP_RPM = 1.0F;
    private static final int OUTPUT_RESYNC_GRACE_TICKS = 40;
    private static final int PLATE_MISSING_GRACE_TICKS = 40;
    private static final int TRANSIENT_PLATE_RELINK_GRACE_TICKS = 20;
    private static final int OUTPUT_SOURCE_GRACE_TICKS = 40;
    private static final long PHYSICS_OUTPUT_SAMPLE_MAX_AGE_TICKS = 1L;
    private static final long BLACKLIST_SCAN_INTERVAL_TICKS = 20L;
    private static final double BLACKLIST_ADJACENCY_EPSILON = 1.0D / 16.0D;

    private final DampingOutputKinetics output;
    private final Vector3d attachedAngularVelocity = new Vector3d();
    private final Vector3d parentAngularVelocity = new Vector3d();
    private final Vector3d attachedLocalAxis = new Vector3d();
    private final Vector3d parentLocalAxis = new Vector3d();
    private final Vector3d attachedResistanceImpulse = new Vector3d();
    private final Vector3d parentResistanceImpulse = new Vector3d();
    private final Vector3d inertiaAxis = new Vector3d();
    private int resistanceValue = AeroUniversalJointConfig.DEFAULT_DAMPING_DEFAULT_RESISTANCE;
    private boolean runtimeRefreshQueued = true;
    @Nullable
    private PhysicsConstraintHandle lastRefreshedHandle;
    @Nullable
    private ScrollOptionBehaviour<?> hiddenLockingOption;
    @Nullable
    private ScrollValueBehaviour resistanceBehaviour;
    private int runtimeResyncTicks = OUTPUT_RESYNC_GRACE_TICKS;
    private int missingPlateTicks;
    private int transientPlateRelinkTicks;
    private float cachedOutputSpeed;
    private float latestPhysicsOutputSpeed;
    private long latestPhysicsOutputSampleTick = Long.MIN_VALUE;
    private double lastMeasuredRelativeAngleDegrees;
    private boolean hasMeasuredRelativeAngle;
    private boolean stressOutputSuppressed;
    @Nullable
    private String suppressedByBlockId;
    private long nextBlacklistScanTick = Long.MIN_VALUE;

    public DampingStressBearingBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.DAMPING_STRESS_BEARING.get(), pos, state);
    }

    public DampingStressBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.resistanceValue = AeroUniversalJointConfig.dampingDefaultResistance();
        this.output = new DampingOutputKinetics(type, new ExtraBlockPos(pos), state, this);
        this.syncResistanceBehaviour();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        int previousSize = behaviours.size();
        super.addBehaviours(behaviours);
        for (int i = behaviours.size() - 1; i >= previousSize; i--) {
            if (behaviours.get(i) instanceof ScrollOptionBehaviour<?> option) {
                option.setValue(LockingSetting.UNLOCKED_ALWAYS.ordinal());
                this.hiddenLockingOption = option;
                behaviours.remove(i);
                break;
            }
        }
        this.resistanceBehaviour = new TypedScrollValueBehaviour(RESISTANCE_BEHAVIOUR,
                Component.translatable("setting.aeronautics_utility_objects.damping_resistance"),
                this,
                new DirectionalValueBoxTransform(0))
                .between(0, getMaxResistanceValue())
                .withFormatter(DampingStressBearingBlockEntity::formatResistanceValue)
                .withCallback(this::setResistanceValue)
                .requiresWrench();
        this.syncResistanceBehaviour();
        behaviours.add(this.resistanceBehaviour);
    }

    @Override
    public void tick() {
        Level level = this.level;
        if (level == null) {
            return;
        }

        this.enforceFreeSwivelMode();
        super.tick();

        level = this.level;
        if (level == null) {
            return;
        }

        if (!level.isClientSide) {
            this.normalizeResistanceValue(true);
            if (this.runtimeResyncTicks > 0) {
                this.runtimeResyncTicks--;
            }
            if (this.transientPlateRelinkTicks > 0) {
                this.transientPlateRelinkTicks--;
            }
            this.refreshRuntimeStateIfNeeded();
            this.clearOwnKineticsIfNeeded();
            if (this.isAssembled()) {
                this.handleUnexpectedPlateState();
            } else {
                this.missingPlateTicks = 0;
                this.resetMeasuredOutput();
            }
            this.refreshStressOutputSuppressionState();
        }

        this.output.tick();
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = 0.0F;
        return 0.0F;
    }

    @Override
    public void assemble() {
        super.assemble();
        this.resetPlateTrackingState();
        this.clearOwnKineticsIfNeeded();
        this.requestRuntimeRefresh();
        this.updateServoCoefficients();
    }

    @Override
    public void disassemble() {
        super.disassemble();
        this.resetPlateTrackingState();
        this.resetMeasuredOutput();
        this.setStressOutputSuppressed(null);
        this.clearOwnKineticsIfNeeded();
        this.requestRuntimeRefresh();
    }

    @Override
    public void initialize() {
        super.initialize();
        this.requestRuntimeRefresh();
    }

    @Override
    public void updateServoCoefficients() {
        if (!this.isAssembled()) {
            return;
        }

        this.forceConstraintFreeSpin();
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        this.aeronautics$runResistancePhysicsTick(subLevel, handle, timeStep);
    }

    public void aeronautics$runResistancePhysicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (this.level == null || this.level.isClientSide || !this.isAssembled()) {
            return;
        }

        ServerSubLevel attached = this.getAttachedServerSubLevel();
        if (attached == null || attached != subLevel) {
            return;
        }

        Vector3d worldAxis = this.getWorldRotationAxis();
        ServerSubLevel parent = this.getContainingServerSubLevel();
        RigidBodyHandle parentHandle = this.getValidContainingHandle(parent, attached);
        double relativeAngularSpeed = this.computeRelativeAngularSpeed(worldAxis, handle, parentHandle);
        this.recordPhysicsOutputSpeed(relativeAngularSpeed);
        this.applyFixedResistance(attached, handle, parent, parentHandle, worldAxis, relativeAngularSpeed, timeStep);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {
        return 0.0F;
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return false;
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        return neighbours;
    }

    @Override
    public KineticBlockEntity getExtraKinetics() {
        return this.output;
    }

    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return "DampingStressBearingOutput";
    }

    public KineticBlockEntity getOutputKinetics() {
        return this.output;
    }

    public boolean setResistanceValueByIndex(int value) {
        if (value < 0 || value > getMaxResistanceValue()) {
            return false;
        }

        this.setResistanceValue(value);
        return true;
    }

    public void setResistanceValue(int value) {
        int clamped = clampResistanceValue(value);
        if (this.resistanceValue == clamped) {
            return;
        }

        this.resistanceValue = clamped;
        this.syncResistanceBehaviour();
        this.setChanged();
        this.sendData();
        this.output.requestUpdate();
    }

    public int getResistanceValue() {
        return this.getClampedResistanceValue();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DampingStressBearingMenu(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return ModBlocks.DAMPING_STRESS_BEARING.get().getName();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!super.addToGoggleTooltip(tooltip, isPlayerSneaking)) {
            return false;
        }

        tooltip.add(Component.translatable("tooltip.aeronautics_utility_objects.damping_setting")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" "))
                .append(describeResistanceValue(this.getClampedResistanceValue()).copy().withStyle(ChatFormatting.AQUA)));
        if (this.stressOutputSuppressed) {
            Component blockedBlockName = this.describeSuppressedBlock();
            tooltip.add(Component.translatable("tooltip.aeronautics_utility_objects.stress_output_suppressed", blockedBlockName)
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt(TAG_RESISTANCE, this.getClampedResistanceValue());
        if (clientPacket) {
            tag.putBoolean(TAG_STRESS_OUTPUT_SUPPRESSED, this.stressOutputSuppressed);
            if (this.suppressedByBlockId != null) {
                tag.putString(TAG_STRESS_OUTPUT_SUPPRESSED_BY, this.suppressedByBlockId);
            }
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (tag.contains(TAG_RESISTANCE)) {
            this.resistanceValue = clampResistanceValue(tag.getInt(TAG_RESISTANCE));
        } else if (tag.contains(LEGACY_TAG_DAMPING)) {
            this.resistanceValue = clampResistanceValue(tag.getInt(LEGACY_TAG_DAMPING));
        }
        this.syncResistanceBehaviour();
        if (clientPacket && tag.contains(TAG_STRESS_OUTPUT_SUPPRESSED)) {
            this.stressOutputSuppressed = tag.getBoolean(TAG_STRESS_OUTPUT_SUPPRESSED);
            this.suppressedByBlockId = tag.contains(TAG_STRESS_OUTPUT_SUPPRESSED_BY)
                    ? tag.getString(TAG_STRESS_OUTPUT_SUPPRESSED_BY)
                    : null;
        } else if (!clientPacket) {
            this.stressOutputSuppressed = false;
            this.suppressedByBlockId = null;
        }
        super.read(tag, registries, clientPacket);
        this.resetPlateTrackingState();
        this.resetMeasuredOutput();
        this.requestRuntimeRefresh();
    }

    public static int getMaxResistanceValue() {
        return AeroUniversalJointConfig.dampingMaxResistance();
    }

    public static Component describeResistanceValue(int value) {
        return Component.literal(Integer.toString(clampResistanceValue(value)));
    }

    private static String formatResistanceValue(int value) {
        return Integer.toString(clampResistanceValue(value));
    }

    private static int clampResistanceValue(int value) {
        return Math.max(0, Math.min(getMaxResistanceValue(), value));
    }

    private int getClampedResistanceValue() {
        return clampResistanceValue(this.resistanceValue);
    }

    private void normalizeResistanceValue(boolean notify) {
        int clamped = this.getClampedResistanceValue();
        if (this.resistanceValue == clamped) {
            return;
        }

        this.resistanceValue = clamped;
        this.syncResistanceBehaviour();
        if (notify) {
            this.setChanged();
            this.sendData();
            this.output.requestUpdate();
        }
    }

    private Vector3d getWorldRotationAxis() {
        Direction facing = this.getBlockState().getValue(SwivelBearingBlock.FACING);
        Vector3d localAxis = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (this.level == null) {
            return localAxis.normalize();
        }

        SubLevel containing = Sable.HELPER.getContaining(this);
        if (containing != null) {
            return containing.logicalPose().transformNormal(localAxis, new Vector3d()).normalize();
        }

        return localAxis.normalize();
    }

    float measureOutputSpeed() {
        if (this.level == null || this.level.isClientSide || !this.isAssembled()) {
            this.resetMeasuredOutput();
            return 0.0F;
        }

        Float physicsSpeed = this.getFreshPhysicsOutputSpeed();
        if (physicsSpeed != null) {
            Double currentAngleDegrees = this.measureRelativeAngleDegrees();
            if (currentAngleDegrees != null) {
                this.lastMeasuredRelativeAngleDegrees = currentAngleDegrees;
                this.hasMeasuredRelativeAngle = true;
            }
            this.cachedOutputSpeed = physicsSpeed;
            return physicsSpeed;
        }

        Double currentAngleDegrees = this.measureRelativeAngleDegrees();
        if (currentAngleDegrees == null) {
            this.hasMeasuredRelativeAngle = false;
            return this.isRuntimeResyncActive() ? this.cachedOutputSpeed : 0.0F;
        }

        if (!this.hasMeasuredRelativeAngle) {
            this.lastMeasuredRelativeAngleDegrees = currentAngleDegrees;
            this.hasMeasuredRelativeAngle = true;
            this.cachedOutputSpeed = 0.0F;
            return 0.0F;
        }

        double deltaDegrees = Mth.wrapDegrees(currentAngleDegrees - this.lastMeasuredRelativeAngleDegrees);
        this.lastMeasuredRelativeAngleDegrees = currentAngleDegrees;
        float rpm = (float) (deltaDegrees * TICKS_PER_SECOND / 360.0D * 60.0D);
        Direction rotationReference = this.getBlockState().getValue(SwivelBearingBlock.FACING);
        float roundedSpeed = this.normalizeOutputSpeed(KineticBlockEntity.convertToDirection(rpm, rotationReference));
        this.cachedOutputSpeed = roundedSpeed;
        return roundedSpeed;
    }

    @Nullable
    private Double measureRelativeAngleDegrees() {
        if (this.level == null || this.getSubLevelID() == null) {
            return null;
        }

        SubLevel attached = SubLevelContainer.getContainer(this.level).getSubLevel(this.getSubLevelID());
        if (attached == null) {
            return null;
        }

        Pose3dc attachedPose = attached.logicalPose();
        SubLevel containing = Sable.HELPER.getContaining(this);
        Pose3dc containingPose = containing != null ? containing.logicalPose() : null;
        return DampingStressBearingAngleHelper.extractRelativeAngleDegrees(
                this.getBlockState().getValue(SwivelBearingBlock.FACING),
                DampingStressBearingAngleHelper.resolvePlateFacing(this),
                containingPose,
                attachedPose);
    }

    float calculateStressCapacity(float speedRpm) {
        return DampingStressOutputModel.calculateCapacity(
                speedRpm, this.getClampedResistanceValue(), getMaxResistanceValue(), this.stressOutputSuppressed);
    }

    float getFullSpeedStressOutput() {
        return DampingStressOutputModel.calculateFullSpeedCapacity(
                this.getClampedResistanceValue(), getMaxResistanceValue());
    }

    @Nullable
    private ServerSubLevel getAttachedServerSubLevel() {
        if (this.level == null || this.getSubLevelID() == null) {
            return null;
        }

        SubLevel subLevel = SubLevelContainer.getContainer(this.level).getSubLevel(this.getSubLevelID());
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    @Nullable
    private ServerSubLevel getContainingServerSubLevel() {
        if (this.level == null) {
            return null;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(this);
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    @Nullable
    private PhysicsConstraintHandle getConstraintHandle() {
        return ((SwivelBearingConstraintHandleAccess) this).aeronautics$getConstraintHandle();
    }

    private void enforceFreeSwivelMode() {
        if (this.hiddenLockingOption != null && this.hiddenLockingOption.getValue() != LockingSetting.UNLOCKED_ALWAYS.ordinal()) {
            this.hiddenLockingOption.setValue(LockingSetting.UNLOCKED_ALWAYS.ordinal());
        }
    }

    private void forceConstraintFreeSpin() {
        PhysicsConstraintHandle constraintHandle = this.getConstraintHandle();
        if (constraintHandle == null || !constraintHandle.isValid()) {
            return;
        }

        constraintHandle.setMotor(
                RotaryConstraintHandle.DEFAULT_AXIS,
                0.0D,
                0.0D,
                FREE_SPIN_BASE_DAMPING,
                false,
                0.0D);
        constraintHandle.setContactsEnabled(false);
    }

    @Nullable
    private RigidBodyHandle getValidContainingHandle(@Nullable ServerSubLevel parent, ServerSubLevel attached) {
        if (parent == null || parent == attached) {
            return null;
        }

        RigidBodyHandle parentHandle = RigidBodyHandle.of(parent);
        return parentHandle != null && parentHandle.isValid() ? parentHandle : null;
    }

    private double computeRelativeAngularSpeed(Vector3d worldAxis, RigidBodyHandle attachedHandle, @Nullable RigidBodyHandle parentHandle) {
        double attachedAngular = attachedHandle.getAngularVelocity(this.attachedAngularVelocity).dot(worldAxis);
        double parentAngular = parentHandle != null
                ? parentHandle.getAngularVelocity(this.parentAngularVelocity).dot(worldAxis)
                : 0.0D;
        return attachedAngular - parentAngular;
    }

    private void recordPhysicsOutputSpeed(double relativeAngularSpeed) {
        if (this.level == null) {
            return;
        }

        float rpm = (float) (relativeAngularSpeed * 60.0D / (2.0D * Math.PI));
        Direction rotationReference = this.getBlockState().getValue(SwivelBearingBlock.FACING);
        this.latestPhysicsOutputSpeed = this.normalizeOutputSpeed(KineticBlockEntity.convertToDirection(rpm, rotationReference));
        this.latestPhysicsOutputSampleTick = this.level.getGameTime();
    }

    @Nullable
    private Float getFreshPhysicsOutputSpeed() {
        if (this.level == null || this.latestPhysicsOutputSampleTick == Long.MIN_VALUE) {
            return null;
        }

        long sampleAge = this.level.getGameTime() - this.latestPhysicsOutputSampleTick;
        if (sampleAge < 0 || sampleAge > PHYSICS_OUTPUT_SAMPLE_MAX_AGE_TICKS) {
            return null;
        }

        return this.latestPhysicsOutputSpeed;
    }

    private float normalizeOutputSpeed(float rpm) {
        float roundedSpeed = Math.round(rpm / SPEED_STEP_RPM) * SPEED_STEP_RPM;
        if (Math.abs(roundedSpeed) < OUTPUT_ZERO_DEADBAND_RPM) {
            return 0.0F;
        }
        return roundedSpeed;
    }

    private void applyFixedResistance(ServerSubLevel attached, RigidBodyHandle attachedHandle, @Nullable ServerSubLevel parent,
                                      @Nullable RigidBodyHandle parentHandle, Vector3d worldAxis, double relativeAngularSpeed,
                                      double timeStep) {
        int resistance = this.getClampedResistanceValue();
        if (resistance <= 0 || timeStep <= 0.0D) {
            return;
        }

        double speedMagnitude = Math.abs(relativeAngularSpeed);
        if (speedMagnitude <= MIN_RELATIVE_ANGULAR_SPEED) {
            return;
        }

        double totalInverseInertia = this.computeInverseInertiaAlongAxis(attached, worldAxis, this.attachedLocalAxis);
        if (parent != null && parentHandle != null) {
            totalInverseInertia += this.computeInverseInertiaAlongAxis(parent, worldAxis, this.parentLocalAxis);
        }
        if (totalInverseInertia <= 1.0E-10D) {
            return;
        }

        double requestedImpulse = resistance * AeroUniversalJointConfig.dampingResistanceTorquePerUnit()
                * this.calculateSpeedDampingMultiplier(relativeAngularSpeed)
                * timeStep;
        double stoppingImpulse = speedMagnitude / totalInverseInertia;
        double impulseMagnitude = Math.min(requestedImpulse, stoppingImpulse);
        double impulseDirection = -Math.signum(relativeAngularSpeed);

        this.attachedResistanceImpulse.set(this.attachedLocalAxis).mul(impulseMagnitude * impulseDirection);
        attachedHandle.applyAngularImpulse(this.attachedResistanceImpulse);

        if (parent != null && parentHandle != null) {
            this.parentResistanceImpulse.set(this.parentLocalAxis).mul(-impulseMagnitude * impulseDirection);
            parentHandle.applyAngularImpulse(this.parentResistanceImpulse);
        }
    }

    private double calculateSpeedDampingMultiplier(double relativeAngularSpeed) {
        double speedRpm = Math.abs(relativeAngularSpeed) * 60.0D / (2.0D * Math.PI);
        double speedDampingBaseRpm = AeroUniversalJointConfig.dampingSpeedDampingBaseRpm();
        if (speedRpm <= speedDampingBaseRpm) {
            return 1.0D;
        }

        double octave = Math.log(speedRpm / speedDampingBaseRpm) / Math.log(2.0D);
        return Math.pow(2.0D, octave * (octave + 1.0D) * 0.5D);
    }

    void applySyncedResistanceValue(int value) {
        this.resistanceValue = clampResistanceValue(value);
        this.syncResistanceBehaviour();
    }

    private void syncResistanceBehaviour() {
        if (this.resistanceBehaviour != null) {
            this.resistanceBehaviour.value = this.getClampedResistanceValue();
        }
    }

    private double computeInverseInertiaAlongAxis(ServerSubLevel subLevel, Vector3d worldAxis, Vector3d localAxisBuffer) {
        subLevel.logicalPose().transformNormalInverse(worldAxis, localAxisBuffer);
        double axisLengthSquared = localAxisBuffer.lengthSquared();
        if (axisLengthSquared <= 1.0E-10D) {
            return 0.0D;
        }

        localAxisBuffer.div(Math.sqrt(axisLengthSquared));
        subLevel.getMassTracker().getInverseInertiaTensor().transform(localAxisBuffer, this.inertiaAxis);
        return Math.max(0.0D, localAxisBuffer.dot(this.inertiaAxis));
    }

    private void scheduleRuntimeRefresh() {
        this.runtimeRefreshQueued = true;
        this.lastRefreshedHandle = null;
        this.runtimeResyncTicks = OUTPUT_RESYNC_GRACE_TICKS;
        this.hasMeasuredRelativeAngle = false;
        this.nextBlacklistScanTick = Long.MIN_VALUE;
    }

    boolean isRuntimeResyncActive() {
        return this.runtimeRefreshQueued || this.runtimeResyncTicks > 0;
    }

    boolean isStressOutputSuppressed() {
        return this.stressOutputSuppressed;
    }

    private void refreshRuntimeStateIfNeeded() {
        PhysicsConstraintHandle handle = this.getConstraintHandle();
        PhysicsConstraintHandle validHandle = handle != null && handle.isValid() ? handle : null;
        if (validHandle != this.lastRefreshedHandle) {
            this.runtimeRefreshQueued = true;
        }
        if (!this.runtimeRefreshQueued) {
            this.lastRefreshedHandle = validHandle;
            return;
        }

        this.output.requestUpdate();
        if (this.isAssembled()) {
            if (validHandle == null) {
                return;
            }
            this.associatePlateWithParent();
            this.updateServoCoefficients();
        }

        this.lastRefreshedHandle = validHandle;
        this.runtimeRefreshQueued = false;
    }

    public void onLinkedPlateRemovedUnexpectedly() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        this.missingPlateTicks = 0;
        this.transientPlateRelinkTicks = Math.max(this.transientPlateRelinkTicks, TRANSIENT_PLATE_RELINK_GRACE_TICKS);
        this.requestRuntimeRefresh();
    }

    private void clearOwnKineticsIfNeeded() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (!this.hasNetwork() && !this.hasSource() && Math.abs(this.getSpeed()) <= SPEED_EPSILON) {
            return;
        }

        if (this.hasNetwork()) {
            this.getOrCreateNetwork().remove(this);
            this.setNetwork(null);
        }

        this.detachKinetics();
        this.removeSource();
        this.setSpeed(0.0F);
    }

    private void handleUnexpectedPlateState() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        BlockPos platePos = this.getPlatePos();
        if (platePos == null) {
            if (this.transientPlateRelinkTicks > 0) {
                return;
            }
            if (++this.missingPlateTicks > PLATE_MISSING_GRACE_TICKS) {
                this.disassembleFromMissingPlate();
            }
            return;
        }

        if (!this.level.isLoaded(platePos)) {
            return;
        }

        if (this.level.getBlockState(platePos).is(SimBlocks.SWIVEL_BEARING_LINK_BLOCK)) {
            if (this.transientPlateRelinkTicks > 0 || this.isRuntimeResyncActive()) {
                this.associatePlateWithParent();
            }
            this.missingPlateTicks = 0;
            this.transientPlateRelinkTicks = 0;
            return;
        }

        if (this.transientPlateRelinkTicks > 0) {
            return;
        }

        if (++this.missingPlateTicks > PLATE_MISSING_GRACE_TICKS) {
            this.disassembleFromMissingPlate();
        }
    }

    private void disassembleFromMissingPlate() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (!this.isAssembled() && this.getSubLevelID() == null) {
            this.missingPlateTicks = 0;
            return;
        }

        this.disassemble();
        this.clearOwnKineticsIfNeeded();
        this.requestRuntimeRefresh();
        this.resetPlateTrackingState();
    }

    private void resetMeasuredOutput() {
        this.cachedOutputSpeed = 0.0F;
        this.latestPhysicsOutputSpeed = 0.0F;
        this.latestPhysicsOutputSampleTick = Long.MIN_VALUE;
        this.lastMeasuredRelativeAngleDegrees = 0.0D;
        this.hasMeasuredRelativeAngle = false;
    }

    private void refreshStressOutputSuppressionState() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (!this.isAssembled()) {
            this.nextBlacklistScanTick = Long.MIN_VALUE;
            this.setStressOutputSuppressed(null);
            return;
        }

        long gameTime = this.level.getGameTime();
        if (this.nextBlacklistScanTick != Long.MIN_VALUE && gameTime < this.nextBlacklistScanTick) {
            return;
        }

        this.nextBlacklistScanTick = gameTime + BLACKLIST_SCAN_INTERVAL_TICKS;
        Set<Block> blacklist = AeroUniversalJointConfig.getStressOutputBlacklistBlocks();
        if (blacklist.isEmpty()) {
            this.setStressOutputSuppressed(null);
            return;
        }

        ServerSubLevel attached = this.getAttachedServerSubLevel();
        Block matchedBlock = null;
        if (attached != null) {
            matchedBlock = this.findBlacklistedStressOutputBlock(attached, blacklist, true);
        }
        if (matchedBlock == null) {
            matchedBlock = this.findAdjacentBlacklistedStressOutputBlock(attached, blacklist);
        }
        this.setStressOutputSuppressed(matchedBlock);
    }

    @Nullable
    private Block findAdjacentBlacklistedStressOutputBlock(@Nullable ServerSubLevel attached, Set<Block> blacklist) {
        if (this.level == null || attached == null) {
            return null;
        }

        BoundingBox3d scanBox = this.createConnectedStructureAdjacencyScanBox(attached);
        SubLevelContainer container = SubLevelContainer.getContainer(attached.getLevel());
        if (container == null) {
            return null;
        }

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (!(subLevel instanceof ServerSubLevel candidate)
                    || candidate == attached
                    || candidate.isRemoved()
                    || !candidate.boundingBox().intersects(scanBox)) {
                continue;
            }

            Block matchedBlock = this.findBlacklistedStressOutputBlock(candidate, blacklist, false);
            if (matchedBlock != null) {
                return matchedBlock;
            }
        }
        return null;
    }

    private BoundingBox3d createConnectedStructureAdjacencyScanBox(ServerSubLevel attached) {
        return new BoundingBox3d(attached.boundingBox()).expand(BLACKLIST_ADJACENCY_EPSILON);
    }

    @Nullable
    private Block findBlacklistedStressOutputBlock(ServerSubLevel subLevel, Set<Block> blacklist, boolean skipOwnPlate) {
        if (blacklist.isEmpty() || subLevel.isRemoved()) {
            return null;
        }

        BlockPos ownPlatePos = skipOwnPlate ? this.getPlatePos() : null;
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }

            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()
                        || !section.maybeHas(state -> !state.isAir() && blacklist.contains(state.getBlock()))) {
                    continue;
                }

                int sectionBottomY = SectionPos.sectionToBlockCoord(chunk.getMinSection() + sectionIndex);
                for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
                    for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                        for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            Block block = state.getBlock();
                            if (!state.isAir() && blacklist.contains(block)
                                    && !this.isOwnPlateBlock(chunk, sectionBottomY, x, y, z, ownPlatePos)) {
                                return block;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isOwnPlateBlock(LevelChunk chunk, int sectionBottomY, int sectionX, int sectionY, int sectionZ,
                                    @Nullable BlockPos ownPlatePos) {
        if (ownPlatePos == null) {
            return false;
        }
        int worldX = chunk.getPos().getMinBlockX() + sectionX;
        int worldY = sectionBottomY + sectionY;
        int worldZ = chunk.getPos().getMinBlockZ() + sectionZ;
        return ownPlatePos.getX() == worldX && ownPlatePos.getY() == worldY && ownPlatePos.getZ() == worldZ;
    }

    private void setStressOutputSuppressed(@Nullable Block matchedBlock) {
        boolean suppressed = matchedBlock != null;
        String matchedId = matchedBlock != null ? BuiltInRegistries.BLOCK.getKey(matchedBlock).toString() : null;
        if (this.stressOutputSuppressed == suppressed && stringEquals(this.suppressedByBlockId, matchedId)) {
            return;
        }

        this.stressOutputSuppressed = suppressed;
        this.suppressedByBlockId = matchedId;
        this.setChanged();
        this.sendData();
        this.output.requestUpdate();
    }

    private Component describeSuppressedBlock() {
        if (this.suppressedByBlockId == null) {
            return Component.translatable("tooltip.aeronautics_utility_objects.stress_output_suppressed.unknown");
        }

        ResourceLocation id = ResourceLocation.tryParse(this.suppressedByBlockId);
        if (id == null) {
            return Component.literal(this.suppressedByBlockId);
        }

        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null) {
            return Component.literal(this.suppressedByBlockId);
        }
        return block.getName();
    }

    private static boolean stringEquals(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
    }

    private void resetPlateTrackingState() {
        this.missingPlateTicks = 0;
        this.transientPlateRelinkTicks = 0;
    }

    private void requestRuntimeRefresh() {
        this.scheduleRuntimeRefresh();
        this.output.requestUpdate();
    }

}
