package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
import com.enxv.aerouniversaljoint.ModBlockEntities;
import com.enxv.aerouniversaljoint.ModBlocks;
import com.enxv.aerouniversaljoint.access.DetachedKineticSafetyGuard;
import com.enxv.aerouniversaljoint.access.SwivelBearingConstraintHandleAccess;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintHandle;
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
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
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

    private static final int MAX_RESISTANCE_VALUE = 256;
    private static final int DEFAULT_RESISTANCE_VALUE = 64;
    private static final float RATED_OUTPUT_SPEED_RPM = 256.0F;
    private static final float MAX_FULL_SPEED_STRESS_OUTPUT = 200000.0F;
    private static final float SUPPRESSED_STRESS_OUTPUT = 40000.0F;
    private static final double RESISTANCE_TORQUE_PER_UNIT = 20.0D;
    private static final double SPEED_DAMPING_BASE_RPM = 128.0D;
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

    private final Output output;
    private final Vector3d attachedAngularVelocity = new Vector3d();
    private final Vector3d parentAngularVelocity = new Vector3d();
    private final Vector3d attachedLocalAxis = new Vector3d();
    private final Vector3d parentLocalAxis = new Vector3d();
    private final Vector3d attachedResistanceImpulse = new Vector3d();
    private final Vector3d parentResistanceImpulse = new Vector3d();
    private final Vector3d inertiaAxis = new Vector3d();
    private int resistanceValue = DEFAULT_RESISTANCE_VALUE;
    private boolean runtimeRefreshQueued = true;
    @Nullable
    private RotaryConstraintHandle lastRefreshedHandle;
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
        this.output = new Output(type, new ExtraBlockPos(pos), state, this);
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
                .between(0, MAX_RESISTANCE_VALUE)
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

        // Damping bearings do not use the swivel bearing's angle servo.
        // We only ensure the hinge stays in the unlocked free-spin branch.
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
        if (value < 0 || value > MAX_RESISTANCE_VALUE) {
            return false;
        }

        this.setResistanceValue(value);
        return true;
    }

    public void setResistanceValue(int value) {
        int clamped = Math.max(0, Math.min(MAX_RESISTANCE_VALUE, value));
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
        return this.resistanceValue;
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
                .append(describeResistanceValue(this.resistanceValue).copy().withStyle(ChatFormatting.AQUA)));
        if (this.stressOutputSuppressed) {
            Component blockedBlockName = this.describeSuppressedBlock();
            tooltip.add(Component.translatable("tooltip.aeronautics_utility_objects.stress_output_suppressed", blockedBlockName)
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt(TAG_RESISTANCE, this.resistanceValue);
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
            this.resistanceValue = Math.max(0, Math.min(MAX_RESISTANCE_VALUE, tag.getInt(TAG_RESISTANCE)));
        } else if (tag.contains(LEGACY_TAG_DAMPING)) {
            this.resistanceValue = Math.max(0, Math.min(MAX_RESISTANCE_VALUE, tag.getInt(LEGACY_TAG_DAMPING)));
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
        return MAX_RESISTANCE_VALUE;
    }

    public static Component describeResistanceValue(int value) {
        return Component.literal(Integer.toString(Math.max(0, Math.min(MAX_RESISTANCE_VALUE, value))));
    }

    private static String formatResistanceValue(int value) {
        return Integer.toString(Math.max(0, Math.min(MAX_RESISTANCE_VALUE, value)));
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

    private float measureOutputSpeed() {
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

    private float calculateStressCapacity(float speedRpm) {
        float speedMagnitude = Math.abs(speedRpm);
        if (speedMagnitude <= SPEED_EPSILON) {
            return 0.0F;
        }

        // Stress output = (actual speed / rated speed) × resistance × coefficient
        // Extra high-speed damping does not increase this payout.
        float coefficient = MAX_FULL_SPEED_STRESS_OUTPUT / (float) MAX_RESISTANCE_VALUE;
        float requestedCapacity = (speedMagnitude / RATED_OUTPUT_SPEED_RPM) * this.resistanceValue * coefficient;
        float cappedCapacity = Math.min(MAX_FULL_SPEED_STRESS_OUTPUT, requestedCapacity);
        return this.stressOutputSuppressed
                ? Math.min(SUPPRESSED_STRESS_OUTPUT, cappedCapacity)
                : cappedCapacity;
    }

    private float getFullSpeedStressOutput() {
        return MAX_FULL_SPEED_STRESS_OUTPUT * this.resistanceValue / (float) MAX_RESISTANCE_VALUE;
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
    private RotaryConstraintHandle getConstraintHandle() {
        return ((SwivelBearingConstraintHandleAccess) this).aeronautics$getConstraintHandle();
    }

    private void enforceFreeSwivelMode() {
        if (this.hiddenLockingOption != null && this.hiddenLockingOption.getValue() != LockingSetting.UNLOCKED_ALWAYS.ordinal()) {
            this.hiddenLockingOption.setValue(LockingSetting.UNLOCKED_ALWAYS.ordinal());
        }
    }

    private void forceConstraintFreeSpin() {
        RotaryConstraintHandle constraintHandle = this.getConstraintHandle();
        if (constraintHandle == null || !constraintHandle.isValid()) {
            return;
        }

        // Rapier rotary constraints do not behave like a truly free hinge when the motor is driven with an exact all-zero state.
        // A tiny damping value keeps the joint in the unlocked branch without introducing noticeable drag of its own.
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
        if (this.resistanceValue <= 0 || timeStep <= 0.0D) {
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

        double requestedImpulse = this.resistanceValue * RESISTANCE_TORQUE_PER_UNIT
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
        if (speedRpm <= SPEED_DAMPING_BASE_RPM) {
            return 1.0D;
        }

        double octave = Math.log(speedRpm / SPEED_DAMPING_BASE_RPM) / Math.log(2.0D);
        return Math.pow(2.0D, octave * (octave + 1.0D) * 0.5D);
    }

    void applySyncedResistanceValue(int value) {
        this.resistanceValue = Math.max(0, Math.min(MAX_RESISTANCE_VALUE, value));
        this.syncResistanceBehaviour();
    }

    private void syncResistanceBehaviour() {
        if (this.resistanceBehaviour != null) {
            this.resistanceBehaviour.value = this.resistanceValue;
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

    private boolean isRuntimeResyncActive() {
        return this.runtimeRefreshQueued || this.runtimeResyncTicks > 0;
    }

    private void refreshRuntimeStateIfNeeded() {
        RotaryConstraintHandle handle = this.getConstraintHandle();
        RotaryConstraintHandle validHandle = handle != null && handle.isValid() ? handle : null;
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

    private static class Output extends GeneratingKineticBlockEntity
            implements dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics.ExtraKineticsBlockEntity,
            DetachedKineticSafetyGuard {
        private static final String TAG_GENERATED_SPEED = "GeneratedSpeed";

        static final IRotate CONFIG = new IRotate() {
            @Override
            public boolean hasShaftTowards(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state, Direction face) {
                return face == state.getValue(SwivelBearingBlock.FACING).getOpposite();
            }

            @Override
            public Direction.Axis getRotationAxis(BlockState state) {
                return state.getValue(SwivelBearingBlock.FACING).getAxis();
            }
        };

        private final DampingStressBearingBlockEntity parent;
        private int customValidationCountdown;
        private float generatedSpeed;
        private float publishedStressCapacity;
        private boolean forceUpdate;
        private int invalidSourceTicks;
        private long nextPublishTick = Long.MIN_VALUE;

        private Output(BlockEntityType<?> type, ExtraBlockPos pos, BlockState state, DampingStressBearingBlockEntity parent) {
            super(type, pos, state);
            this.parent = parent;
        }

        @Override
        public void initialize() {
            super.initialize();
            this.generatedSpeed = 0.0F;
            this.publishedStressCapacity = 0.0F;
            this.invalidSourceTicks = 0;
            this.nextPublishTick = Long.MIN_VALUE;
            this.reActivateSource = true;
            this.forceUpdate = true;
        }

        @Override
        public void tick() {
            if (this.level == null) {
                return;
            }

            if (this.level.isClientSide) {
                super.tick();
                return;
            }

            ((KineticBlockEntityExtension) this).simulated$setValidationCountdown(Integer.MAX_VALUE);
            if (--this.customValidationCountdown <= 0) {
                this.customValidationCountdown = AllConfigs.server().kinetics.kineticValidationFrequency.get();
                this.customValidateKinetics();
            }

            float previousSpeed = this.generatedSpeed;
            float previousStressCapacity = this.publishedStressCapacity;
            float measuredSpeed = this.parent.measureOutputSpeed();
            if (this.shouldPublishOutput()) {
                this.publishOutput(measuredSpeed);
            }
            super.tick();

            if (this.forceUpdate
                    || Math.abs(previousSpeed - this.generatedSpeed) > SPEED_EPSILON
                    || Math.abs(previousStressCapacity - this.publishedStressCapacity) > SPEED_EPSILON) {
                this.forceUpdate = false;
                this.updateGeneratedRotation();
            }
        }

        private boolean shouldPublishOutput() {
            return this.forceUpdate
                    || this.nextPublishTick == Long.MIN_VALUE
                    || this.level != null && this.level.getGameTime() >= this.nextPublishTick;
        }

        private void publishOutput(float measuredSpeed) {
            float targetSpeed = this.quantizePublishedSpeed(measuredSpeed);
            // Clamp output speed to rated maximum to prevent Create blocks from breaking
            // But calculate stress output based on actual measured speed (not clamped)
            float clampedSpeed = Math.signum(targetSpeed) * Math.min(Math.abs(targetSpeed), RATED_OUTPUT_SPEED_RPM);
            if (this.needsSafeReversal(clampedSpeed)) {
                this.generatedSpeed = 0.0F;
                this.publishedStressCapacity = 0.0F;
                this.restartKineticOutput();
            } else {
                this.generatedSpeed = clampedSpeed;
                // Use actual measured speed (not clamped) for stress output calculation
                this.publishedStressCapacity = this.parent.calculateStressCapacity(targetSpeed);
            }
            if (this.level != null) {
                this.nextPublishTick = this.level.getGameTime() + OUTPUT_PUBLISH_INTERVAL_TICKS;
            }
        }

        private float quantizePublishedSpeed(float speed) {
            float roundedSpeed = Math.round(speed / OUTPUT_PUBLISHED_STEP_RPM) * OUTPUT_PUBLISHED_STEP_RPM;
            if (Math.abs(roundedSpeed) < OUTPUT_ZERO_DEADBAND_RPM) {
                return 0.0F;
            }
            return roundedSpeed;
        }

        private void restartKineticOutput() {
            if (this.level == null || this.level.isClientSide) {
                return;
            }
            if (!this.hasNetwork() && !this.hasSource() && Math.abs(this.getSpeed()) <= SPEED_EPSILON) {
                return;
            }

            this.detachKinetics();
            this.removeSource();
            this.reActivateSource = false;
            this.invalidSourceTicks = 0;
            this.forceUpdate = true;
        }

        private boolean needsSafeReversal(float targetSpeed) {
            float actualSpeed = this.getSpeed();
            return Math.abs(targetSpeed) > OUTPUT_ZERO_DEADBAND_RPM
                    && Math.abs(actualSpeed) > OUTPUT_ZERO_DEADBAND_RPM
                    && Math.signum(targetSpeed) != Math.signum(actualSpeed);
        }

        private void customValidateKinetics() {
            if (this.parent.isRuntimeResyncActive()) {
                this.invalidSourceTicks = 0;
                return;
            }
            if (!this.hasSource()) {
                this.invalidSourceTicks = 0;
                return;
            }
            if (!this.hasNetwork()) {
                if (++this.invalidSourceTicks <= OUTPUT_SOURCE_GRACE_TICKS) {
                    return;
                }
                this.removeSource();
                this.invalidSourceTicks = 0;
                return;
            }
            if (!this.level.isLoaded(this.source)) {
                this.invalidSourceTicks = 0;
                return;
            }

            BlockEntity blockEntity = this.level.getBlockEntity(this.source);
            if (blockEntity instanceof ExtraKinetics extraKinetics
                    && ((KineticBlockEntityExtension) this).simulated$getConnectedToExtraKinetics()) {
                blockEntity = extraKinetics.getExtraKinetics();
            }

            KineticBlockEntity source = blockEntity instanceof KineticBlockEntity kineticBlockEntity ? kineticBlockEntity : null;
            if (source == null || source.getTheoreticalSpeed() == 0.0F) {
                if (++this.invalidSourceTicks <= OUTPUT_SOURCE_GRACE_TICKS) {
                    return;
                }
                this.removeSource();
                this.detachKinetics();
                this.invalidSourceTicks = 0;
                return;
            }
            this.invalidSourceTicks = 0;
        }

        private void requestUpdate() {
            this.forceUpdate = true;
            this.invalidSourceTicks = 0;
            this.nextPublishTick = Long.MIN_VALUE;
        }

        @Override
        public float getGeneratedSpeed() {
            return this.generatedSpeed;
        }

        @Override
        public float calculateStressApplied() {
            return 0.0F;
        }

        @Override
        public float calculateAddedStressCapacity() {
            // Return capacity per unit speed for Create's stress system
            // Create will multiply this by actual speed automatically
            float speed = Math.abs(this.getTheoreticalSpeed());
            if (this.parent.stressOutputSuppressed && this.publishedStressCapacity > 0.0F) {
                float capacityPerUnitSpeed = this.publishedStressCapacity / Math.max(speed, 1.0F);
                this.lastCapacityProvided = capacityPerUnitSpeed;
                return capacityPerUnitSpeed;
            }
            if (speed <= SPEED_EPSILON) {
                this.lastCapacityProvided = 0.0F;
                return 0.0F;
            }
            float capacityPerUnitSpeed = this.publishedStressCapacity / speed;
            this.lastCapacityProvided = capacityPerUnitSpeed;
            return capacityPerUnitSpeed;
        }

        @Override
        public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
            boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
            
            // If parent didn't add anything (because capacity is 0), add it manually
            if (!added && this.parent.isAssembled()) {
                float maxCapacity = this.parent.stressOutputSuppressed
                        ? SUPPRESSED_STRESS_OUTPUT
                        : this.parent.getFullSpeedStressOutput();
                if (maxCapacity > 0.0F) {
                    com.simibubi.create.foundation.utility.CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);
                    com.simibubi.create.foundation.utility.CreateLang.translate("tooltip.capacityProvided")
                        .style(ChatFormatting.GRAY)
                        .forGoggles(tooltip);
                    
                    // publishedStressCapacity is already the final stress output value
                    // Don't multiply by speed again
                    float currentCapacity = this.publishedStressCapacity;
                    
                    com.simibubi.create.foundation.utility.CreateLang.number(currentCapacity)
                        .translate("generic.unit.stress")
                        .style(ChatFormatting.AQUA)
                        .space()
                        .add(com.simibubi.create.foundation.utility.CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY))
                        .forGoggles(tooltip, 1);
                    
                    return true;
                }
            }
            
            return added;
        }

        @Override
        protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            super.write(tag, registries, clientPacket);
            if (clientPacket) {
                tag.putFloat(TAG_GENERATED_SPEED, this.generatedSpeed);
                tag.putFloat("PublishedStressCapacity", this.publishedStressCapacity);
            }
        }

        @Override
        protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            super.read(tag, registries, clientPacket);
            this.generatedSpeed = clientPacket && tag.contains(TAG_GENERATED_SPEED) ? tag.getFloat(TAG_GENERATED_SPEED) : 0.0F;
            this.publishedStressCapacity = clientPacket && tag.contains("PublishedStressCapacity") ? tag.getFloat("PublishedStressCapacity") : 0.0F;
            this.invalidSourceTicks = 0;
            this.nextPublishTick = Long.MIN_VALUE;
            this.reActivateSource = true;
            this.forceUpdate = true;
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return this.parent;
        }
    }
}
